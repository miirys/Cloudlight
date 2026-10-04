import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: root

    property bool selected: false
    property bool hasMenu: false

    implicitWidth: contentRow.implicitWidth + DesktopTokens.px(36)
    implicitHeight: DesktopTokens.px(40)
    padding: 0
    focusPolicy: Qt.NoFocus
    hoverEnabled: true
    Accessible.role: Accessible.Button
    Accessible.name: text

    // Pill filter, GeForce NOW style: selected is solid light on dark.
    background: Rectangle {
        radius: height / 2
        color: root.selected || root.down ? DesktopTokens.textHigh
                                          : root.hovered ? DesktopTokens.hover : DesktopTokens.raised
        Behavior on color {
            ColorAnimation { duration: DesktopTokens.quickDuration }
        }
    }

    contentItem: Item {
      Row {
        id: contentRow
        anchors.centerIn: parent
        spacing: root.hasMenu ? DesktopTokens.px(8) : 0

        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: root.text
            color: root.selected || root.down ? DesktopTokens.shell : DesktopTokens.textBody
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            font.weight: Font.DemiBold
        }

        DesktopSettingsIcon {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.hasMenu
            width: DesktopTokens.px(12)
            height: width
            glyph: "chevron"
            rotation: 90
            ink: root.selected || root.down ? DesktopTokens.shell : DesktopTokens.textMuted
        }
    }
    }
}
