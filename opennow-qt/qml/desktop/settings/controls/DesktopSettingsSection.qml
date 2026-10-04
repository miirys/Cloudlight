import QtQuick
import OpenNOW

// Group heading, e.g. "Streaming quality". Sentence case, no letter-spaced caps.
Item {
    id: root
    property string text: ""
    property string description: ""
    default property alias actions: actionRow.data
    width: parent.width
    implicitHeight: heading.y + Math.max(heading.implicitHeight, actionRow.implicitHeight) + DesktopTokens.px(8)

    Column {
        id: heading
        x: 0
        y: DesktopTokens.px(28)
        width: Math.max(0, actionRow.x - x - DesktopTokens.px(16))
        spacing: DesktopTokens.px(4)
        Text {
            width: parent.width
            text: root.text
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.headingSize
            font.weight: Font.DemiBold
            wrapMode: Text.WordWrap
        }
        Text {
            visible: text !== ""
            width: parent.width
            text: root.description
            color: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            wrapMode: Text.WordWrap
        }
    }
    Row {
        id: actionRow
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(8)
        spacing: DesktopTokens.px(8)
    }
}
