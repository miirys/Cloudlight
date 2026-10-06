//! Best-effort decoding of `0x0110` bitmap cursor notifications.
//!
//! The official client logs this command as "Server sent bitmap cursor info
//! with ID: %u, size: %u", but the rest of its layout is not documented. A
//! cursor is only accepted when its header names dimensions whose pixel data
//! exactly fills the payload (or when it carries an embedded PNG), so an
//! unrecognised layout falls back to the game's own in-video cursor instead
//! of drawing garbage.

/// Largest cursor drawn locally. The Qt wire format carries the encoded image
/// with a 16-bit length, which a 112x112 PNG in base64 still fits.
const MAX_EXTENT: u32 = 112;
/// Header bytes searched for dimensions and hotspot fields.
const MAX_HEADER: usize = 64;
const PNG_SIGNATURE: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0d, 0x0a, 0x1a, 0x0a];

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct DecodedCursor {
    pub hotspot: (u8, u8),
    /// PNG file bytes.
    pub png: Vec<u8>,
}

/// Qt cursor message: type 1, id, hotspot, empty MIME, image length and the
/// image in base64, which `StreamVideoItem::applyRemoteCursor` decodes.
pub(crate) fn normalized_bitmap_cursor(payload: &[u8]) -> Option<Vec<u8>> {
    let cursor = decode(payload)?;
    let encoded = base64(&cursor.png);
    let length = u16::try_from(encoded.len()).ok()?;
    let mut message = vec![1, 1, cursor.hotspot.0, cursor.hotspot.1, 0];
    message.extend_from_slice(&length.to_le_bytes());
    message.extend_from_slice(encoded.as_bytes());
    Some(message)
}

pub(crate) fn decode(payload: &[u8]) -> Option<DecodedCursor> {
    if let Some(start) = find(payload, &PNG_SIGNATURE)
        && start <= MAX_HEADER
    {
        let hotspot = small_pair(&payload[..start], u32::MAX, u32::MAX).unwrap_or((0, 0));
        return Some(DecodedCursor {
            hotspot: clamp_hotspot(hotspot),
            png: payload[start..].to_vec(),
        });
    }
    let fields = header_fields(payload);
    for field in &fields {
        for other in &fields {
            if other.offset != field.offset + field.width {
                continue;
            }
            let (width, height) = (field.value, other.value);
            if width == 0 || height == 0 || width > MAX_EXTENT || height > MAX_EXTENT {
                continue;
            }
            let header_end = other.offset + other.width;
            let colour = (width * height * 4) as usize;
            let mask = (width.div_ceil(32) * 4 * height) as usize;
            for (pixels_len, trailing) in [(colour, 0), (colour, mask)] {
                let Some(start) = payload.len().checked_sub(pixels_len + trailing) else {
                    continue;
                };
                // Pixels start aligned, shortly after the dimensions (room for
                // a hotspot and a format word), never mid-field.
                if start < header_end
                    || start > MAX_HEADER
                    || start % 4 != 0
                    || start - header_end > 16
                {
                    continue;
                }
                let pixels = &payload[start..start + pixels_len];
                let hotspot = hotspot_near(&fields, field, other, width, height);
                let rgba = bgra_to_rgba(pixels, width, height, hotspot);
                return Some(DecodedCursor {
                    hotspot: clamp_hotspot(hotspot),
                    png: encode_png(width, height, &rgba),
                });
            }
        }
    }
    None
}

#[derive(Clone, Copy)]
struct Field {
    offset: usize,
    width: usize,
    value: u32,
}

fn header_fields(payload: &[u8]) -> Vec<Field> {
    let end = payload.len().min(MAX_HEADER);
    let mut fields = Vec::new();
    for width in [4usize, 2] {
        let mut offset = 0;
        while offset + width <= end {
            let value = if width == 4 {
                u32::from_le_bytes(payload[offset..offset + 4].try_into().unwrap_or([0; 4]))
            } else {
                u32::from(u16::from_le_bytes([payload[offset], payload[offset + 1]]))
            };
            fields.push(Field {
                offset,
                width,
                value,
            });
            offset += width;
        }
    }
    fields
}

/// The hotspot is taken from the pair of fields right after (or else right
/// before) the dimensions when both values lie inside the cursor.
fn hotspot_near(fields: &[Field], first: &Field, second: &Field, w: u32, h: u32) -> (u32, u32) {
    let pair_at = |offset: usize| {
        let x = fields
            .iter()
            .find(|field| field.offset == offset && field.width == first.width)?;
        let y = fields
            .iter()
            .find(|field| field.offset == offset + first.width && field.width == first.width)?;
        (x.value < w && y.value < h).then_some((x.value, y.value))
    };
    pair_at(second.offset + second.width)
        .or_else(|| first.offset.checked_sub(2 * first.width).and_then(pair_at))
        .unwrap_or((0, 0))
}

fn small_pair(header: &[u8], max_x: u32, max_y: u32) -> Option<(u32, u32)> {
    let fields = header_fields(header);
    fields.iter().rev().find_map(|x| {
        let y = fields
            .iter()
            .find(|y| y.width == x.width && y.offset == x.offset + x.width)?;
        (x.value < 256 && y.value < 256 && x.value < max_x && y.value < max_y && x.offset >= 8)
            .then_some((x.value, y.value))
    })
}

fn clamp_hotspot((x, y): (u32, u32)) -> (u8, u8) {
    (x.min(255) as u8, y.min(255) as u8)
}

/// Windows cursor pixels are BGRA and may be stored bottom-up. The rows are
/// flipped when the hotspot pixel is transparent but its mirror is not, and
/// cursors without any alpha are made opaque where they have colour.
fn bgra_to_rgba(pixels: &[u8], width: u32, height: u32, hotspot: (u32, u32)) -> Vec<u8> {
    let (w, h) = (width as usize, height as usize);
    let alpha_at = |x: usize, y: usize| pixels[(y * w + x) * 4 + 3];
    let (hx, hy) = (hotspot.0 as usize, hotspot.1 as usize);
    let flip = alpha_at(hx, hy) == 0 && alpha_at(hx, h - 1 - hy) != 0;
    let no_alpha = pixels.chunks_exact(4).all(|pixel| pixel[3] == 0);
    let mut rgba = Vec::with_capacity(pixels.len());
    for row in 0..h {
        let source = if flip { h - 1 - row } else { row };
        for pixel in pixels[source * w * 4..(source + 1) * w * 4].chunks_exact(4) {
            let alpha = if no_alpha {
                if pixel[..3].iter().any(|channel| *channel != 0) {
                    255
                } else {
                    0
                }
            } else {
                pixel[3]
            };
            rgba.extend_from_slice(&[pixel[2], pixel[1], pixel[0], alpha]);
        }
    }
    rgba
}

fn find(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack
        .windows(needle.len())
        .position(|window| window == needle)
}

/// Uncompressed (stored-deflate) RGBA PNG; cursors are small.
fn encode_png(width: u32, height: u32, rgba: &[u8]) -> Vec<u8> {
    let stride = width as usize * 4;
    let mut raw = Vec::with_capacity((stride + 1) * height as usize);
    for row in rgba.chunks_exact(stride) {
        raw.push(0);
        raw.extend_from_slice(row);
    }
    let mut zlib = vec![0x78, 0x01];
    let mut blocks = raw.chunks(65_535).peekable();
    if blocks.peek().is_none() {
        zlib.extend_from_slice(&[1, 0, 0, 0xff, 0xff]);
    }
    while let Some(block) = blocks.next() {
        zlib.push(u8::from(blocks.peek().is_none()));
        let length = block.len() as u16;
        zlib.extend_from_slice(&length.to_le_bytes());
        zlib.extend_from_slice(&(!length).to_le_bytes());
        zlib.extend_from_slice(block);
    }
    zlib.extend_from_slice(&adler32(&raw).to_be_bytes());

    let mut header = Vec::with_capacity(13);
    header.extend_from_slice(&width.to_be_bytes());
    header.extend_from_slice(&height.to_be_bytes());
    header.extend_from_slice(&[8, 6, 0, 0, 0]);
    let mut png = PNG_SIGNATURE.to_vec();
    for (kind, data) in [(b"IHDR", &header), (b"IDAT", &zlib), (b"IEND", &Vec::new())] {
        png.extend_from_slice(&(data.len() as u32).to_be_bytes());
        let mut crc = crc32fast::Hasher::new();
        crc.update(kind);
        crc.update(data);
        png.extend_from_slice(kind);
        png.extend_from_slice(data);
        png.extend_from_slice(&crc.finalize().to_be_bytes());
    }
    png
}

fn adler32(data: &[u8]) -> u32 {
    let (mut a, mut b) = (1u32, 0u32);
    for byte in data {
        a = (a + u32::from(*byte)) % 65_521;
        b = (b + a) % 65_521;
    }
    (b << 16) | a
}

fn base64(data: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for chunk in data.chunks(3) {
        let bytes = [
            chunk[0],
            *chunk.get(1).unwrap_or(&0),
            *chunk.get(2).unwrap_or(&0),
        ];
        let value = (u32::from(bytes[0]) << 16) | (u32::from(bytes[1]) << 8) | u32::from(bytes[2]);
        for index in 0..4 {
            if index <= chunk.len() {
                out.push(TABLE[((value >> (18 - 6 * index)) & 63) as usize] as char);
            } else {
                out.push('=');
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cursor_payload(width: u32, height: u32, hotspot: (u32, u32), bottom_up: bool) -> Vec<u8> {
        let mut payload = Vec::new();
        payload.extend_from_slice(&7u32.to_le_bytes());
        payload.extend_from_slice(&(width * height * 4 + 16).to_le_bytes());
        for value in [width, height, hotspot.0, hotspot.1] {
            payload.extend_from_slice(&value.to_le_bytes());
        }
        for row in 0..height {
            let y = if bottom_up { height - 1 - row } else { row };
            for x in 0..width {
                let opaque = x + y < height / 2;
                payload.extend_from_slice(&[10, 20, 30, if opaque { 255 } else { 0 }]);
            }
        }
        payload
    }

    #[test]
    fn decodes_sized_bgra_cursors_with_hotspot() {
        let cursor = decode(&cursor_payload(32, 32, (3, 5), false)).expect("decoded");
        assert_eq!(cursor.hotspot, (3, 5));
        assert_eq!(&cursor.png[..8], &PNG_SIGNATURE);
        assert_eq!(&cursor.png[16..24], &[0, 0, 0, 32, 0, 0, 0, 32]);
    }

    #[test]
    fn flips_bottom_up_cursors() {
        let top_down = decode(&cursor_payload(16, 16, (0, 0), false)).unwrap();
        let bottom_up = decode(&cursor_payload(16, 16, (0, 0), true)).unwrap();
        assert_eq!(top_down.png, bottom_up.png);
    }

    #[test]
    fn rejects_payloads_that_do_not_fit_their_dimensions() {
        let mut payload = cursor_payload(32, 32, (0, 0), false);
        payload.truncate(payload.len() - 3);
        assert_eq!(decode(&payload), None);
        assert_eq!(decode(&[1, 0, 0, 0, 9, 0, 0, 0]), None);
    }

    #[test]
    fn normalized_message_carries_base64_png_for_qt() {
        let message = normalized_bitmap_cursor(&cursor_payload(8, 8, (1, 2), false)).unwrap();
        assert_eq!(&message[..5], &[1, 1, 1, 2, 0]);
        let length = u16::from_le_bytes([message[5], message[6]]) as usize;
        assert_eq!(message.len(), 7 + length);
        assert!(message[7..].starts_with(b"iVBORw0KGgo"));
    }
}
