import QtQuick
import QtQuick.Controls
import OpenNOW

TextField {
    id: control
    implicitWidth: DesktopTokens.settingsControlWidth
    implicitHeight: DesktopTokens.px(40)
    color: Theme.label
    placeholderTextColor: Theme.textMuted
    selectionColor: Theme.focus
    selectedTextColor: Theme.focusText
    font.family: Theme.bodyFont
    font.pixelSize: DesktopTokens.px(14)
    leftPadding: DesktopTokens.px(14)
    rightPadding: DesktopTokens.px(14)
    selectByMouse: true
    background: Rectangle {
        radius: DesktopTokens.radius
        // With a mouse the focused field just brightens; the ring is for keyboard
        // and controller focus, like buttons.
        color: control.activeFocus ? Theme.surfaceHover : Theme.surfaceRaised
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
        border.width: control.activeFocus && AppController.inputMode !== "pointer" ? 2 : 0
        border.color: control.activeFocus ? Theme.focus : Theme.seam
    }
}
