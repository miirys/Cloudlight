import QtQuick
import OpenNOW

// Indented checkbox row, as on GeForce NOW's Notifications page.
OverlayFocusable {
    id: root
    property string title: ""
    property bool checked: false
    width: parent ? parent.width : 0
    height: OverlayStyle.u(75)
    Accessible.role: Accessible.CheckBox
    Accessible.name: title
    Accessible.checkable: true
    Accessible.checked: checked

    Rectangle {
        id: box
        x: OverlayStyle.u(43)
        anchors.verticalCenter: parent.verticalCenter
        width: OverlayStyle.u(24)
        height: width
        color: root.checked && root.available ? OverlayStyle.accent : "transparent"
        border.width: root.checked && root.available ? 0 : Math.max(1, Math.round(OverlayStyle.uf(2)))
        border.color: root.available ? OverlayStyle.subtitle : OverlayStyle.disabled
        Behavior on color { ColorAnimation { duration: OverlayStyle.fastDuration } }
        OverlayIcon {
            anchors.centerIn: parent
            width: OverlayStyle.u(22)
            height: width
            name: "check"
            ink: OverlayStyle.inkOnAccent
            scale: root.checked ? 1 : 0.4
            opacity: root.checked && root.available ? 1 : 0
            Behavior on scale { NumberAnimation { duration: OverlayStyle.fastDuration; easing.type: Easing.OutCubic } }
            Behavior on opacity { NumberAnimation { duration: OverlayStyle.fastDuration } }
        }
    }
    Text {
        x: OverlayStyle.u(97)
        width: parent.width - x - OverlayStyle.gutter
        anchors.verticalCenter: parent.verticalCenter
        text: root.title
        color: root.available ? OverlayStyle.text : OverlayStyle.disabled
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.bodySize
        font.variableAxes: OverlayStyle.bodyAxes
        elide: Text.ElideRight
    }
}
