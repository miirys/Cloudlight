import QtQuick
import OpenNOW

// Large hero action for 10-foot use: solid fill, 56px tall, 1.06 scale and a
// white outline under keyboard or controller focus.
Item {
    id: button
    property bool primary: false
    property string glyph: ""
    property string text: ""
    property bool selected: false
    readonly property bool keyboardFocus: selected && AppController.inputMode !== "pointer"
    readonly property color fill: primary ? DesktopTokens.focus
        : (hover.hovered || keyboardFocus ? "#FFFFFF" : "#E8E8E8")
    readonly property color ink: primary ? Theme.focusText : "#111111"
    signal activated()
    signal pointed()
    width: Math.max(DesktopTokens.px(150), label.implicitWidth + DesktopTokens.px(84))
    height: DesktopTokens.px(56)
    scale: keyboardFocus && !AppController.reducedMotion ? 1.06 : 1
    Behavior on scale { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
    Accessible.role: Accessible.Button
    Accessible.name: text

    Rectangle {
        anchors.fill: parent
        anchors.margins: -DesktopTokens.px(5)
        radius: DesktopTokens.radius + DesktopTokens.px(5)
        color: "transparent"
        border.width: DesktopTokens.focusOutline
        border.color: "#FFFFFF"
        opacity: button.keyboardFocus ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
    }
    Rectangle {
        anchors.fill: parent
        radius: DesktopTokens.radius
        color: tap.pressed ? Qt.darker(button.fill, 1.15) : button.fill
        Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
    }
    Row {
        anchors.centerIn: parent
        spacing: DesktopTokens.px(12)
        DesktopSettingsIcon {
            anchors.verticalCenter: parent.verticalCenter
            width: DesktopTokens.px(22); height: width
            glyph: button.glyph
            ink: button.ink
        }
        Text {
            id: label
            anchors.verticalCenter: parent.verticalCenter
            text: button.text
            color: button.ink
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            font.weight: Font.Bold
        }
    }
    HoverHandler { id: hover; cursorShape: Qt.PointingHandCursor; onHoveredChanged: if (hovered) button.pointed() }
    TapHandler { id: tap; onTapped: button.activated() }
}
