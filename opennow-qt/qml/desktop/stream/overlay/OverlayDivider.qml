import QtQuick
import OpenNOW

// Thin grey rule between groups, inset from both panel edges.
Item {
    width: parent ? parent.width : 0
    height: OverlayStyle.u(41)
    Rectangle {
        x: OverlayStyle.gutter
        width: parent.width - OverlayStyle.gutter * 2
        height: Math.max(1, Math.round(OverlayStyle.uf(1)))
        anchors.verticalCenter: parent.verticalCenter
        color: OverlayStyle.divider
    }
}
