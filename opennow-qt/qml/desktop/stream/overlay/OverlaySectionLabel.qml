import QtQuick
import OpenNOW

// Small grey group label ("General", "Status indicators").
Text {
    property bool strong: false
    x: OverlayStyle.gutter
    width: (parent ? parent.width : 0) - OverlayStyle.gutter * 2
    topPadding: OverlayStyle.u(12)
    bottomPadding: OverlayStyle.u(14)
    color: strong ? OverlayStyle.text : OverlayStyle.label
    font.family: Theme.bodyFont
    font.pixelSize: OverlayStyle.labelSize
    font.variableAxes: strong ? OverlayStyle.bodyAxes : OverlayStyle.strongAxes
    elide: Text.ElideRight
}
