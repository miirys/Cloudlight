import QtQuick
import OpenNOW

// A calm inline notice for setting combinations that won't apply as chosen.
// Filled, no outline; the tint carries the meaning.
Rectangle {
    id: root
    property var messages: []
    visible: messages.length > 0
    implicitHeight: visible ? column.implicitHeight + DesktopTokens.px(28) : 0
    radius: DesktopTokens.px(12)
    color: Qt.rgba(Theme.yellow.r, Theme.yellow.g, Theme.yellow.b, Theme.lightMode ? 0.12 : 0.14)

    Rectangle {
        id: badge
        x: DesktopTokens.px(16); y: DesktopTokens.px(15)
        width: DesktopTokens.px(20); height: width; radius: width / 2
        color: Theme.yellow
        Text {
            anchors.centerIn: parent
            text: "!"
            color: Theme.lightMode ? "#FFFFFF" : "#1A1206"
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(13)
            font.weight: Font.Bold
        }
    }
    Column {
        id: column
        x: badge.x + badge.width + DesktopTokens.px(12)
        y: DesktopTokens.px(14)
        width: root.width - x - DesktopTokens.px(16)
        spacing: DesktopTokens.px(6)
        Text {
            width: parent.width
            text: qsTr("Some settings won't apply as chosen")
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(14)
            font.weight: Font.DemiBold
        }
        Repeater {
            model: root.messages
            delegate: Text {
                required property string modelData
                width: column.width
                text: modelData
                wrapMode: Text.WordWrap
                lineHeight: 1.2
                color: DesktopTokens.textBody
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.px(13)
            }
        }
    }
}
