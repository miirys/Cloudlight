import QtQuick
import QtQuick.Controls
import OpenNOW

// Compact on/off switch. Accent track when on, neutral track when off, no glow.
AbstractButton {
    id: control
    signal valueChangedByUser(bool value)

    implicitWidth: DesktopTokens.px(40)
    implicitHeight: DesktopTokens.px(22)
    hoverEnabled: true
    onClicked: valueChangedByUser(!checked)
    Accessible.role: Accessible.CheckBox
    Accessible.checked: checked

    background: Rectangle {
        radius: height / 2
        color: control.checked ? Theme.focus
             : control.hovered ? Theme.surfaceStrong : Theme.surfaceRaised
        border.width: control.checked ? 0 : 1
        border.color: Theme.seam
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
        Rectangle {
            visible: control.activeFocus
            anchors.fill: parent
            anchors.margins: -DesktopTokens.px(3)
            radius: height / 2
            color: "transparent"
            border.width: 2
            border.color: Theme.label
        }
    }

    Rectangle {
        width: DesktopTokens.px(16)
        height: width
        radius: width / 2
        y: (control.height - height) / 2
        x: control.checked ? control.width - width - DesktopTokens.px(3) : DesktopTokens.px(3)
        color: control.checked ? Theme.focusText : Theme.label
        Behavior on x {
            NumberAnimation { duration: AppController.reducedMotion ? 0 : 110; easing.type: Easing.OutCubic }
        }
    }
}
