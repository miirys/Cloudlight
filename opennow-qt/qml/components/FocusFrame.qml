import QtQuick
import OpenNOW

Item {
    id: root
    property bool focused: false
    property real frameRadius: Theme.radiusLarge

    anchors.fill: parent
    anchors.margins: focused ? -7 : 0

    Rectangle {
        anchors.fill: parent
        visible: root.focused
        radius: root.frameRadius + 7
        color: "transparent"
        border.width: 5
        border.color: Theme.focus
    }

    Rectangle {
        anchors.fill: parent
        anchors.margins: root.focused ? 7 : 0
        radius: root.frameRadius
        color: "transparent"
        border.width: root.focused ? 2 : 0
        border.color: Theme.shell
    }

    Behavior on anchors.margins {
        NumberAnimation { duration: Theme.focusDuration; easing.type: Easing.OutCubic }
    }
}
