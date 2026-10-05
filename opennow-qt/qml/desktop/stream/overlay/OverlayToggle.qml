import QtQuick
import OpenNOW

// GeForce NOW's flat switch: a short track with a knob that overhangs it.
Item {
    id: root
    property bool checked: false
    property bool available: true
    implicitWidth: OverlayStyle.u(50)
    implicitHeight: OverlayStyle.u(28)

    Rectangle {
        anchors.verticalCenter: parent.verticalCenter
        x: OverlayStyle.u(4)
        width: parent.width - OverlayStyle.u(4)
        height: OverlayStyle.u(18)
        radius: height / 2
        color: !root.available ? "#3A3A3A" : root.checked ? OverlayStyle.accentTrack : OverlayStyle.trackOff
        Behavior on color { ColorAnimation { duration: OverlayStyle.fastDuration } }
    }
    Rectangle {
        width: OverlayStyle.u(26)
        height: width
        radius: width / 2
        anchors.verticalCenter: parent.verticalCenter
        x: root.checked ? root.width - width : 0
        color: !root.available ? "#555555" : root.checked ? OverlayStyle.accent : OverlayStyle.knobOff
        Behavior on x { NumberAnimation { duration: OverlayStyle.fastDuration; easing.type: Easing.OutCubic } }
        Behavior on color { ColorAnimation { duration: OverlayStyle.fastDuration } }
    }
}
