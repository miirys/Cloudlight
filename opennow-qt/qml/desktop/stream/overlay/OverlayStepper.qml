import QtQuick
import OpenNOW

// "<  Value  >" picker used for Statistics and heads-up display positions. The arrows
// are clickable; Left and Right on the focused row step it too.
Item {
    id: root
    property string valueText: ""
    property bool available: true
    property int valueWidth: OverlayStyle.u(150)
    signal stepped(int direction)
    implicitWidth: OverlayStyle.u(28) * 2 + valueWidth
    implicitHeight: OverlayStyle.u(32)

    component Arrow: OverlayIcon {
        id: arrow
        property int direction: 1
        width: OverlayStyle.u(28)
        height: width
        anchors.verticalCenter: parent.verticalCenter
        name: direction < 0 ? "chevron-left" : "chevron-right"
        ink: root.available ? OverlayStyle.text : OverlayStyle.disabled
        TapHandler {
            enabled: root.available
            gesturePolicy: TapHandler.ReleaseWithinBounds
            grabPermissions: PointerHandler.CanTakeOverFromAnything
            onTapped: root.stepped(arrow.direction)
        }
    }
    Arrow { x: 0; direction: -1 }
    Text {
        x: OverlayStyle.u(28)
        width: root.valueWidth
        anchors.verticalCenter: parent.verticalCenter
        horizontalAlignment: Text.AlignHCenter
        text: root.valueText
        color: root.available ? OverlayStyle.text : OverlayStyle.disabled
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        elide: Text.ElideRight
    }
    Arrow { x: root.width - width; direction: 1 }
}
