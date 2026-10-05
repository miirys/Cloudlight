"""Draws the Cloudlight repository banner (docs/assets/brand/cloudlight-banner.png)
and the 1280x640 social preview from the brand mark SVG and the bundled Cormorant
Garamond face. Run from the repository root: QT_QPA_PLATFORM=offscreen python3 <this>"""
import math
from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import (QColor, QFont, QFontDatabase, QGuiApplication, QImage, QPainter,
                           QPainterPath, QRadialGradient)
from PySide6.QtSvg import QSvgRenderer

app = QGuiApplication([])
family = QFontDatabase.applicationFontFamilies(
    QFontDatabase.addApplicationFont("opennow-qt/res/fonts/CormorantGaramond-Variable.ttf"))[0]
inter = QFontDatabase.applicationFontFamilies(
    QFontDatabase.addApplicationFont("opennow-qt/res/fonts/Inter-SemiBold.ttf"))[0]
W, H = 2560, 1280


def sparkle(p, cx, cy, r, color):
    k = 0.18 * r
    path = QPainterPath(QPointF(cx, cy - r))
    for (x, y), (nx, ny) in zip([(0, -r), (r, 0), (0, r), (-r, 0)], [(r, 0), (0, r), (-r, 0), (0, -r)]):
        path.cubicTo(QPointF(cx + x * 0.12 + (k if x == 0 else 0) * (1 if nx > 0 else -1 if nx < 0 else 0), cy + y * 0.12),
                     QPointF(cx + nx * 0.12, cy + ny * 0.12 + (k if ny == 0 else 0) * (1 if y > 0 else -1 if y < 0 else 0)),
                     QPointF(cx + nx, cy + ny))
    p.fillPath(path, color)


img = QImage(W, H, QImage.Format_ARGB32_Premultiplied)
p = QPainter(img)
p.setRenderHint(QPainter.Antialiasing)
p.fillRect(img.rect(), QColor("#0D0D10"))
glow = QRadialGradient(QPointF(W * 0.30, H * 0.48), W * 0.42)
glow.setColorAt(0, QColor(40, 41, 47, 255))
glow.setColorAt(1, QColor(13, 13, 16, 0))
p.fillRect(img.rect(), glow)

ink = QColor("#C9CAD2")
for x, y, r, a in [(0.08, 0.20, 20, 0.8), (0.17, 0.78, 12, 0.6), (0.46, 0.16, 14, 0.7), (0.53, 0.82, 24, 0.8),
                   (0.88, 0.22, 16, 0.6), (0.94, 0.70, 10, 0.5), (0.70, 0.12, 9, 0.5)]:
    c = QColor(ink)
    c.setAlphaF(a)
    sparkle(p, W * x, H * y, r * 1.6, c)

mark = QSvgRenderer("opennow-qt/res/brand/source/cloudlight-mark.svg")
box = mark.viewBoxF()
width = 820
height = width * box.height() / box.width()
mark.render(p, QRectF(W * 0.30 - width / 2, H * 0.5 - height / 2 - 20, width, height))

font = QFont(family)
font.setPixelSize(260)
font.setWeight(QFont.Medium)
p.setFont(font)
p.setPen(QColor("#F4F4F6"))
p.drawText(QRectF(W * 0.50, H * 0.26, W * 0.48, 320), Qt.AlignLeft | Qt.AlignVCenter, "Cloudlight")

body = QFont(inter)
body.setPixelSize(56)
p.setFont(body)
p.setPen(QColor("#A2A3AB"))
p.drawText(QRectF(W * 0.505, H * 0.56, W * 0.46, 200), Qt.AlignLeft | Qt.TextWordWrap,
           "Your GeForce NOW library,\non the big screen.")
p.end()
img.save("docs/assets/brand/cloudlight-banner.png")
img.scaled(1280, 640, Qt.IgnoreAspectRatio, Qt.SmoothTransformation).save("docs/assets/brand/cloudlight-social.png")
