#!/usr/bin/env python3
"""Generate the Qt application's platform icons from repository-root logo.png.

Sizes up to 32 px come from packaging/logo-small.png, the same icon drawn with a larger
mark so it stays readable in title bars and taskbars.
"""

import argparse
import base64
import io
from pathlib import Path

from PIL import Image, ImageDraw


PACKAGING = Path(__file__).resolve().parent
SIZES = (16, 24, 32, 48, 64, 128, 256, 512, 1024)
SMALL_SIZES = (16, 24, 32)


def master_from(path):
    with Image.open(path) as source:
        source = source.convert("RGBA")
        bounds = source.getchannel("A").getbbox()
        if bounds is None:
            raise ValueError(f"{path.name} must contain visible artwork")
        mark = source.crop(bounds)
    mark.thumbnail((896, 896), Image.Resampling.LANCZOS)
    master = Image.new("RGBA", (1024, 1024))
    master.paste(mark, ((1024 - mark.width) // 2, (1024 - mark.height) // 2))
    return master


def generate_assets():
    master = master_from(PACKAGING.parents[1] / "logo.png")
    small = master_from(PACKAGING / "logo-small.png")
    images = {size: (small if size in SMALL_SIZES else master).resize((size, size), Image.Resampling.LANCZOS)
              for size in SIZES}
    assets = {}
    for size, image in images.items():
        output = io.BytesIO()
        image.save(output, format="PNG")
        assets[f"icons/opennow-{size}.png"] = output.getvalue()
    output = io.BytesIO()
    images[256].save(output, format="ICO", sizes=[(s, s) for s in SIZES if s <= 256],
                     append_images=[images[s] for s in SIZES if s < 256])
    assets["icons/OpenNOW.ico"] = output.getvalue()
    output = io.BytesIO()
    master.save(output, format="ICNS", append_images=list(images.values()))
    assets["icons/OpenNOW.icns"] = output.getvalue()
    png = base64.b64encode(assets["icons/opennow-512.png"]).decode("ascii")
    assets["io.github.opencloudgaming.OpenNOW.svg"] = (
        '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        'width="512" height="512" viewBox="0 0 512 512" role="img" aria-label="Cloudlight">\n'
        f'  <image width="512" height="512" xlink:href="data:image/png;base64,{png}"/>\n'
        '</svg>\n'
    ).encode("ascii")
    return assets


def write_preview(assets, path):
    preview = Image.new("RGB", (960, 480))
    draw = ImageDraw.Draw(preview)
    for column, background in enumerate(("#f4f4f4", "#202124")):
        x = column * 480
        draw.rectangle((x, 0, x + 479, 479), fill=background)
        foreground = "#202124" if column == 0 else "#f4f4f4"
        draw.text((x + 24, 20), "Cloudlight / generated from logo.png", fill=foreground)
        with Image.open(io.BytesIO(assets["icons/opennow-256.png"])) as image:
            preview.paste(image, (x + 112, 55), image)
        position = x + 24
        for size in (16, 24, 32, 48, 64, 128):
            with Image.open(io.BytesIO(assets[f"icons/opennow-{size}.png"])) as image:
                preview.paste(image, (position, 365 - size // 2), image)
            draw.text((position, 442), str(size), fill=foreground)
            position += size + 14
    path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if checked-in assets differ")
    parser.add_argument("--preview", type=Path, help="Write a light/dark size contact sheet")
    args = parser.parse_args()
    assets = generate_assets()
    stale = []
    for relative_path, content in assets.items():
        path = PACKAGING / relative_path
        if args.check:
            if not path.exists() or path.read_bytes() != content:
                stale.append(relative_path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if args.preview:
        write_preview(assets, args.preview)
    if stale:
        parser.exit(1, "Stale application icons: " + ", ".join(stale) + "\n")
    print(f"{'Verified' if args.check else 'Generated'} {len(assets)} application icon assets")


if __name__ == "__main__":
    main()
