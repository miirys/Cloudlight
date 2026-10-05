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
        color: Theme.surfaceRaised
        border.width: control.activeFocus ? 2 : 0
        border.color: control.activeFocus ? Theme.focus : Theme.seam
    }
}
