#!/usr/bin/env python3
"""Draw the Cloudlight icon and brand marks as vectors and render the PNGs the app ships.

The artwork is maddie's design: a glossy white cloud with a faceted four-point star on a
near-black rounded tile. Everything is built from exact geometry on a 1024 grid (three
circles and a flat base for the cloud, a superellipse star) so it stays crisp at any size.

    python3 cloudlight_icon.py            # write the SVGs here and the PNGs into the repo

Then run packaging/generate_icons.py to refresh the platform icons from logo.png and
packaging/logo-small.png (the variant drawn for 16 to 32 px).
"""

import argparse
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
QT_ROOT = HERE.parents[2]
REPO = QT_ROOT.parent

# Cloud: two side lobes, one tall lobe, a flat base. Units are the 1024 icon grid.
LEFT = (315.0, 619.0, 122.0)
TOP = (506.0, 528.0, 196.0)
RIGHT = (708.0, 619.0, 122.0)
BASE = LEFT[1] + LEFT[2]
FILLET = 16.0
# Star: four concave arms, slightly taller than wide.
STAR_CENTER = (720.0, 339.0)
STAR_HALF_HEIGHT = 119.0
STAR_HALF_WIDTH = 106.0
STAR_EXPONENT = 0.62
STAR_GAP = 40.0
TILE_RADIUS = 220.0


def fmt(value):
    return f"{value:.2f}".rstrip("0").rstrip(".")


def fillet_center(a, b, radius):
    """Centre of the circle of `radius` touching circles a and b from outside, above them."""
    (ax, ay, ar), (bx, by, br) = a, b
    ra, rb = ar + radius, br + radius
    dx, dy = bx - ax, by - ay
    d = math.hypot(dx, dy)
    along = (ra * ra - rb * rb + d * d) / (2 * d)
    h = math.sqrt(max(0.0, ra * ra - along * along))
    mx, my = ax + along * dx / d, ay + along * dy / d
    # Of the two solutions, the one with the smaller y sits in the valley on top.
    c1 = (mx + h * dy / d, my - h * dx / d)
    c2 = (mx - h * dy / d, my + h * dx / d)
    return c1 if c1[1] < c2[1] else c2


def toward(center, point, radius):
    cx, cy = center
    px, py = point
    d = math.hypot(px - cx, py - cy)
    return (cx + (px - cx) * radius / d, cy + (py - cy) * radius / d)


def cloud_path():
    left_fillet = fillet_center(LEFT, TOP, FILLET)
    right_fillet = fillet_center(TOP, RIGHT, FILLET)
    l1 = toward(LEFT[:2], left_fillet, LEFT[2])
    t1 = toward(TOP[:2], left_fillet, TOP[2])
    t2 = toward(TOP[:2], right_fillet, TOP[2])
    r1 = toward(RIGHT[:2], right_fillet, RIGHT[2])
    lx, ly, lr = LEFT
    rx, ry, rr = RIGHT
    tr = TOP[2]
    p = lambda pt: f"{fmt(pt[0])},{fmt(pt[1])}"
    return (f"M{fmt(lx)},{fmt(BASE)} "
            f"A{fmt(lr)},{fmt(lr)} 0 0 1 {p(l1)} "
            f"A{fmt(FILLET)},{fmt(FILLET)} 0 0 0 {p(t1)} "
            f"A{fmt(tr)},{fmt(tr)} 0 0 1 {p(t2)} "
            f"A{fmt(FILLET)},{fmt(FILLET)} 0 0 0 {p(r1)} "
            f"A{fmt(rr)},{fmt(rr)} 0 0 1 {fmt(rx)},{fmt(BASE)} Z")


def star_path(steps=48, grow=1.0, n=STAR_EXPONENT):
    cx, cy = STAR_CENTER
    a, b = STAR_HALF_WIDTH * grow, STAR_HALF_HEIGHT * grow
    points = []
    for quadrant in range(4):
        for i in range(steps):
            t = (i / steps) * (math.pi / 2)
            # Superellipse |x/a|^n + |y/b|^n = 1, walked from one tip to the next.
            x = a * math.sin(t) ** (2 / n)
            y = b * math.cos(t) ** (2 / n)
            sx, sy = [(1, -1), (1, 1), (-1, 1), (-1, -1)][quadrant]
            if quadrant % 2:
                x, y = a * math.cos(t) ** (2 / n), b * math.sin(t) ** (2 / n)
            points.append((cx + sx * x, cy + sy * y))
    return "M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in points) + " Z"


CLOUD = cloud_path()
STAR = star_path()
# Small sizes (16 to 32 px) use a bigger mark, a fuller star and a wider gap so both
# shapes survive downsampling.
SMALL_SCALE = 1.22
SMALL_STAR = star_path(grow=1.18, n=0.72)
SMALL_GAP = 58.0


def palette(light):
    if not light:
        return {
            "cloudTop": "#FBFBFC", "cloudBottom": "#DCDDE1",
            "sheen": "#C4C5CB", "sheenOpacity": "0.7",
            "waveTop": "#8E9098", "waveBottom": "#B7B9BF",
            "rim": "#FFFFFF", "rimOpacity": "0.9",
            "star": ("#FFFFFF", "#ECECEF", "#D7D8DC", "#C2C3C8"),
        }
    return {
        "cloudTop": "#4A4B52", "cloudBottom": "#2A2B30",
        "sheen": "#202126", "sheenOpacity": "0.45",
        "waveTop": "#121317", "waveBottom": "#1D1E22",
        "rim": "#6B6C74", "rimOpacity": "0.6",
        "star": ("#3A3B41", "#2E2F35", "#24252A", "#1A1B1F"),
    }


def artwork(prefix, light=False, small=False):
    """Cloud and star, without the tile. Ids are prefixed so several can share a page.
    Masks rather than clip paths, because QtSvg renders masks but ignores clipPath."""
    c = palette(light)
    cx, cy = STAR_CENTER
    s1, s2, s3, s4 = c["star"]
    big = 1200
    star = SMALL_STAR if small else STAR
    gap = SMALL_GAP if small else STAR_GAP
    sheen = "" if small else (f'<path d="M270,744 C300,640 400,532 560,486 C640,463 700,452 760,452 L900,452 L900,800 L270,800 Z" '
                              f'fill="{c["sheen"]}" opacity="{c["sheenOpacity"]}"/>')
    wave_opacity = "0.5" if small else "0.78"
    group = (f' transform="translate(512,482) scale({SMALL_SCALE}) translate(-512,-482)"' if small else "")
    return f'''<defs>
    <mask id="{prefix}cloud" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024">
      <path d="{CLOUD}" fill="#FFFFFF"/>
    </mask>
    <mask id="{prefix}star" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024">
      <path d="{star}" fill="#FFFFFF"/>
    </mask>
    <mask id="{prefix}gap" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024">
      <rect width="1024" height="1024" fill="#FFFFFF"/>
      <path d="{star}" fill="#000000" stroke="#000000" stroke-width="{fmt(gap * 2)}" stroke-linejoin="round"/>
    </mask>
    <linearGradient id="{prefix}body" gradientUnits="userSpaceOnUse" x1="0" y1="{fmt(TOP[1] - TOP[2])}" x2="0" y2="{fmt(BASE)}">
      <stop offset="0" stop-color="{c['cloudTop']}"/><stop offset="1" stop-color="{c['cloudBottom']}"/>
    </linearGradient>
    <linearGradient id="{prefix}rim" gradientUnits="userSpaceOnUse" x1="0" y1="{fmt(TOP[1] - TOP[2])}" x2="0" y2="{fmt(BASE)}">
      <stop offset="0" stop-color="{c['rim']}" stop-opacity="{c['rimOpacity']}"/><stop offset="0.55" stop-color="{c['rim']}" stop-opacity="0.25"/><stop offset="1" stop-color="{c['rim']}" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="{prefix}wave" gradientUnits="userSpaceOnUse" x1="260" y1="560" x2="700" y2="760">
      <stop offset="0" stop-color="{c['waveBottom']}"/><stop offset="0.55" stop-color="{c['waveTop']}"/><stop offset="1" stop-color="{c['waveBottom']}"/>
    </linearGradient>
  </defs>
  <g{group}>
  <g mask="url(#{prefix}gap)">
    <g mask="url(#{prefix}cloud)">
      <path d="{CLOUD}" fill="url(#{prefix}body)"/>
      {sheen}
      <path d="M160,562 C250,548 350,550 440,574 C530,598 590,640 660,662 C720,681 780,690 860,690 L860,800 L160,800 Z" fill="url(#{prefix}wave)" opacity="{wave_opacity}"/>
      <path d="{CLOUD}" fill="none" stroke="url(#{prefix}rim)" stroke-width="12"/>
    </g>
  </g>
  <g mask="url(#{prefix}star)">
    <rect x="{fmt(cx - big)}" y="{fmt(cy - big)}" width="{big}" height="{big}" fill="{s1}"/>
    <rect x="{fmt(cx)}" y="{fmt(cy - big)}" width="{big}" height="{big}" fill="{s2}"/>
    <rect x="{fmt(cx - big)}" y="{fmt(cy)}" width="{big}" height="{big}" fill="{s3}"/>
    <rect x="{fmt(cx)}" y="{fmt(cy)}" width="{big}" height="{big}" fill="{s4}"/>
  </g>
  </g>'''


def tile(prefix):
    r = fmt(TILE_RADIUS)
    return f'''<defs>
    <linearGradient id="{prefix}tile" x1="0.15" y1="0" x2="0.85" y2="1">
      <stop offset="0" stop-color="#2C2D33"/><stop offset="0.5" stop-color="#15161A"/><stop offset="1" stop-color="#09090C"/>
    </linearGradient>
    <linearGradient id="{prefix}edge" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.16"/><stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.03"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0.08"/>
    </linearGradient>
  </defs>
  <rect width="1024" height="1024" rx="{r}" fill="url(#{prefix}tile)"/>
  <rect x="2" y="2" width="1020" height="1020" rx="{fmt(TILE_RADIUS - 2)}" fill="none" stroke="url(#{prefix}edge)" stroke-width="4"/>'''


# Tight bounds of the mark on the 1024 grid, padded so antialiasing never clips.
MARK_BOX = (LEFT[0] - LEFT[2] - 8, STAR_CENTER[1] - STAR_HALF_HEIGHT - 8,
            max(RIGHT[0] + RIGHT[2], STAR_CENTER[0] + STAR_HALF_WIDTH) + 8, BASE + 8)


def icon_svg(size=1024, small=False):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 1024 1024" '
            f'role="img" aria-label="Cloudlight">\n  {tile("t")}\n  {artwork("i", small=small)}\n</svg>\n')


def mark_svg(light=False):
    x0, y0, x1, y1 = MARK_BOX
    w, h = x1 - x0, y1 - y0
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{fmt(w)}" height="{fmt(h)}" '
            f'viewBox="{fmt(x0)} {fmt(y0)} {fmt(w)} {fmt(h)}" role="img" aria-label="Cloudlight">\n'
            f'  {artwork("m", light)}\n</svg>\n')


def render(svg, path, width, height):
    from PySide6.QtCore import QByteArray, QRectF, Qt
    from PySide6.QtGui import QImage, QPainter
    from PySide6.QtSvg import QSvgRenderer
    renderer = QSvgRenderer(QByteArray(svg.encode()))
    if not renderer.isValid():
        raise RuntimeError(f"invalid SVG for {path}")
    image = QImage(width, height, QImage.Format_ARGB32_Premultiplied)
    image.fill(Qt.transparent)
    painter = QPainter(image)
    painter.setRenderHint(QPainter.Antialiasing)
    renderer.render(painter, QRectF(0, 0, width, height))
    painter.end()
    if not image.save(str(path)):
        raise RuntimeError(f"could not write {path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--svg-only", action="store_true", help="Write the SVG sources only")
    args = parser.parse_args()
    from PySide6.QtGui import QGuiApplication
    app = QGuiApplication(sys.argv[:1])  # noqa: F841 (QtSvg needs an application)
    (HERE / "cloudlight-icon.svg").write_text(icon_svg())
    (HERE / "cloudlight-icon-small.svg").write_text(icon_svg(small=True))
    (HERE / "cloudlight-mark.svg").write_text(mark_svg())
    (HERE / "cloudlight-mark-light.svg").write_text(mark_svg(light=True))
    if args.svg_only:
        return
    render(icon_svg(), REPO / "logo.png", 2048, 2048)
    render(icon_svg(small=True), QT_ROOT / "packaging/logo-small.png", 1024, 1024)
    x0, y0, x1, y1 = MARK_BOX
    width = 1440
    height = round(width * (y1 - y0) / (x1 - x0))
    render(mark_svg(), QT_ROOT / "res/brand/opennow-mark.png", width, height)
    render(mark_svg(light=True), QT_ROOT / "res/brand/cloudlight-mark-light.png", width, height)
    print(f"mark {width}x{height}, aspect {width}/{height}")


if __name__ == "__main__":
    main()
