import QtQuick
import QtQuick.Controls
import OpenNOW

// "Advanced" disclosure: a plain list row with a chevron, no card.
AbstractButton {
    id: root
    property string detail: ""
    property bool expanded: false
    width: parent.width
    implicitHeight: DesktopTokens.px(48)
    hoverEnabled: true
    background: Rectangle {
        color: root.hovered ? DesktopTokens.hover : "transparent"
        border.width: root.activeFocus ? 2 : 0; border.color: Theme.focus
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: DesktopTokens.seamSoft }
    }
    Text { id: title; x: 0; anchors.verticalCenter: parent.verticalCenter; text: qsTr("Advanced"); color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(14); font.weight: Font.Medium }
    Text { anchors.left: title.right; anchors.leftMargin: DesktopTokens.px(12); anchors.right: arrow.left; anchors.rightMargin: DesktopTokens.px(16); anchors.verticalCenter: parent.verticalCenter; text: root.detail; elide: Text.ElideRight; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12) }
    DesktopSettingsIcon {
        id: arrow; anchors.right: parent.right; anchors.rightMargin: DesktopTokens.px(4); anchors.verticalCenter: parent.verticalCenter
        width: DesktopTokens.px(14); height: width; glyph: "chevron"; rotation: root.expanded ? -90 : 90; ink: Theme.textMuted
        Behavior on rotation { enabled: !AppController.reducedMotion; NumberAnimation { duration: 120; easing.type: Easing.OutCubic } }
    }
}
