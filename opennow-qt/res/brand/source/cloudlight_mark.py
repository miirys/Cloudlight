import sys
from PySide6.QtGui import QGuiApplication, QImage, QPainter, QColor
from PySide6.QtSvg import QSvgRenderer
from PySide6.QtCore import QByteArray, QRectF, Qt
app = QGuiApplication(sys.argv[:1])

def star(cx, cy, R, r, k):
    # Four-point star with concave sides; k pulls the waist out from the centre.
    return (f"M{cx},{cy-R} C{cx+k},{cy-k} {cx+k},{cy-k} {cx+r},{cy} "
            f"C{cx+k},{cy+k} {cx+k},{cy+k} {cx},{cy+R} "
            f"C{cx-k},{cy+k} {cx-k},{cy+k} {cx-r},{cy} "
            f"C{cx-k},{cy-k} {cx-k},{cy-k} {cx},{cy-R} Z")

# Cloud: a flat-bottomed silhouette built from circles over a rounded base.
CLOUD = '''
  <circle cx="352" cy="626" r="126"/>
  <circle cx="520" cy="512" r="178"/>
  <circle cx="700" cy="630" r="122"/>
  <rect x="352" y="560" width="348" height="192"/>
'''
STAR = star(706, 330, 168, 112, 22)

def mark_svg(cloud="#F3EFF9", star_fill="#FFFFFF", bg=None, gap=30, size=1024):
    bgs = ''
    if bg:
        bgs = f'''<defs><radialGradient id="g" cx="0.28" cy="0.2" r="0.95">
          <stop offset="0" stop-color="#2B2336"/><stop offset="0.55" stop-color="#17131C"/><stop offset="1" stop-color="#0D0B10"/></radialGradient></defs>
        <rect width="1024" height="1024" rx="228" fill="url(#g)"/>'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 1024 1024">
  {bgs}
  <defs>
    <mask id="cut" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024">
      <rect width="1024" height="1024" fill="#fff"/>
      <path d="{STAR}" fill="#000" stroke="#000" stroke-width="{gap*2}" stroke-linejoin="round"/>
    </mask>
    <linearGradient id="cl" gradientUnits="userSpaceOnUse" x1="0" y1="330" x2="0" y2="752">
      <stop offset="0" stop-color="{cloud}"/><stop offset="1" stop-color="#C9BEDB"/>
    </linearGradient>
  </defs>
  <g transform="translate(0,46)"><g mask="url(#cut)" fill="url(#cl)">{CLOUD}</g>
  <path d="{STAR}" fill="{star_fill}"/></g>
</svg>'''

def render(svg, path, size):
    r = QSvgRenderer(QByteArray(svg.encode()))
    img = QImage(size, size, QImage.Format_ARGB32_Premultiplied); img.fill(Qt.transparent)
    p = QPainter(img); p.setRenderHint(QPainter.Antialiasing); r.render(p, QRectF(0, 0, size, size)); p.end()
    img.save(path)

if __name__ == '__main__':
    open('cloudlight-mark.svg','w').write(mark_svg())
    open('cloudlight-icon.svg','w').write(mark_svg(bg=True))
    render(mark_svg(), 'mark-1024.png', 1024)
    render(mark_svg(bg=True), 'icon-1024.png', 1024)

def render_all():
    render(mark_svg(), 'mark-2048.png', 2048)
    render(mark_svg(bg=True), 'icon-2048.png', 2048)
