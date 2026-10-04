import QtQuick
import QtQuick.Controls
import QtQuick.Window
import OpenNOW

// One setting: label and description on the left, control on the right, hairline
// below. No decorative icon tiles; a leading image is only shown for real content
// such as store logos or the profile avatar. `glyph` is accepted for API
// compatibility but intentionally not drawn.
Item {
    id: root
    property bool paperStyle: false
    property string glyph: ""
    property bool expanded: false
    property bool expandable: false
    signal expansionRequested()
    property string title: ""
    property string description: ""
    property string value: ""
    property int rowHeight: DesktopTokens.px(56)
    property bool showDivider: true
    property string leadingLetter: ""
    property url leadingIcon: ""
    property real leadingIconWidth: DesktopTokens.px(20)
    property color leadingColor: DesktopTokens.raised
    readonly property bool hasLeading: leadingLetter !== "" || leadingIcon.toString() !== ""
    default property alias trailing: trailingSlot.data

    readonly property real labelInset: hasLeading ? DesktopTokens.px(52) : 0
    readonly property real rightInset: 0
    readonly property real controlWidth: Math.max(0, Math.min(DesktopTokens.settingsControlWidth, width - labelInset - rightInset))
    readonly property bool stacked: width < DesktopTokens.settingsCompactWidth && trailingSlot.implicitWidth > DesktopTokens.px(120)
    implicitHeight: Math.max(rowHeight, (stacked ? trailingSlot.y + trailingSlot.height : Math.max(labels.y + labels.height, trailingSlot.y + trailingSlot.height)) + DesktopTokens.px(12))

    // Whole-row hover for expandable rows, like a list item.
    Rectangle {
        anchors.fill: parent
        visible: root.expandable && expandButton.hovered
        color: DesktopTokens.hover
    }

    Rectangle {
        id: leadingTile
        visible: root.hasLeading
        x: 0
        y: DesktopTokens.px(12)
        width: DesktopTokens.px(36)
        height: width
        radius: DesktopTokens.radius
        color: root.leadingColor
        Image {
            anchors.centerIn: parent
            width: root.leadingIconWidth
            height: width
            source: root.leadingIcon
            sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
            fillMode: Image.PreserveAspectFit
            visible: root.leadingIcon.toString() !== ""
        }
        Text {
            anchors.centerIn: parent
            text: root.leadingLetter
            visible: root.leadingIcon.toString() === ""
            color: Theme.contrastText(root.leadingColor)
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.px(15)
            font.weight: Font.DemiBold
        }
    }

    Column {
        id: labels
        objectName: "settingsRowLabels"
        anchors.left: parent.left
        anchors.leftMargin: root.labelInset
        anchors.right: root.stacked ? parent.right : trailingSlot.left
        anchors.rightMargin: root.stacked ? root.rightInset : DesktopTokens.px(24)
        y: DesktopTokens.px(12) + Math.max(0, (DesktopTokens.px(32) - height) / 2)
        spacing: DesktopTokens.px(3)
        Text {
            id: titleLabel
            width: parent.width
            text: root.title
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(14)
            font.weight: Font.Medium
            wrapMode: Text.WordWrap
        }
        Text {
            width: parent.width
            visible: root.description !== ""
            text: root.description
            color: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(12)
            lineHeight: 1.15
            wrapMode: Text.WordWrap
        }
    }

    Row {
        id: trailingSlot
        objectName: "settingsRowControls"
        readonly property real availableWidth: root.controlWidth
        anchors.right: parent.right
        anchors.rightMargin: root.rightInset + (root.expandable ? DesktopTokens.px(32) : 0)
        y: root.stacked ? labels.y + labels.height + DesktopTokens.px(8) : DesktopTokens.px(12)
        spacing: DesktopTokens.px(10)
        height: Math.max(DesktopTokens.px(32), implicitHeight)

        add: Transition {
            ScriptAction { script: root.centerTrailing() }
        }

        Text {
            visible: root.value !== ""
            text: root.value
            color: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(14)
            anchors.verticalCenter: parent.verticalCenter
        }
    }

    AbstractButton {
        id: expandButton
        visible: root.expandable
        anchors.fill: parent
        hoverEnabled: true
        Accessible.name: root.title
        onClicked: root.expansionRequested()
        background: Rectangle {
            color: "transparent"
            border.width: parent.activeFocus ? 2 : 0
            border.color: Theme.focus
        }
        DesktopSettingsIcon {
            anchors.right: parent.right
            anchors.rightMargin: DesktopTokens.px(4)
            anchors.verticalCenter: parent.verticalCenter
            width: DesktopTokens.px(14); height: width; glyph: "chevron"
            rotation: root.expanded ? -90 : 90; ink: Theme.textMuted
            Behavior on rotation { enabled: !AppController.reducedMotion; NumberAnimation { duration: 120; easing.type: Easing.OutCubic } }
        }
    }

    function centerTrailing() {
        for (let i = 0; i < trailingSlot.children.length; ++i) {
            const item = trailingSlot.children[i]
            if (item)
                item.anchors.verticalCenter = trailingSlot.verticalCenter
        }
    }

    Component.onCompleted: centerTrailing()

    Rectangle {
        visible: root.showDivider
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 1
        color: DesktopTokens.seamSoft
    }
}
