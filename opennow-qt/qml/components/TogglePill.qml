import QtQuick
import QtQuick.Controls
import OpenNOW

Switch {
    id: root
    implicitWidth: 60
    implicitHeight: 34
    focusPolicy: Qt.StrongFocus
    indicator: Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: root.checked ? Theme.focus : Theme.surfaceStrong
        border.color: Theme.label
        border.width: root.activeFocus ? 3 : 0
        Rectangle {
            width: 26; height: 26; radius: 13
            x: root.checked ? parent.width - width - 4 : 4
            anchors.verticalCenter: parent.verticalCenter
            color: root.checked ? Theme.focusText : Theme.label
            Behavior on x { NumberAnimation { duration: Theme.focusDuration; easing.type: Easing.OutCubic } }
        }
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
    }
    contentItem: Item {}
}
