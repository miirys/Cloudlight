import QtQuick
import QtQuick.Controls
import OpenNOW

// Compact on/off switch. Accent track when on, neutral track when off, no glow.
AbstractButton {
    id: control
    signal valueChangedByUser(bool value)

    implicitWidth: DesktopTokens.px(44)
    implicitHeight: DesktopTokens.px(26)
    hoverEnabled: true
    onClicked: valueChangedByUser(!checked)
    Accessible.role: Accessible.CheckBox
    Accessible.checked: checked

    background: Rectangle {
        radius: height / 2
        color: control.checked ? Theme.focus
             : control.hovered ? Theme.surfaceHover : Theme.surfaceStrong
        border.width: control.checked ? 0 : 0
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
        // The knob widens while pressed and springs across, like iOS.
        width: DesktopTokens.px(control.pressed ? 26 : 22)
        height: DesktopTokens.px(22)
        radius: height / 2
        y: (control.height - height) / 2
        x: control.checked ? control.width - width - DesktopTokens.px(2) : DesktopTokens.px(2)
        color: control.checked ? Theme.focusText : Theme.label
        Behavior on x {
            NumberAnimation { duration: AppController.reducedMotion ? 0 : 340; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring }
        }
        Behavior on width {
            NumberAnimation { duration: AppController.reducedMotion ? 0 : 160; easing.type: Easing.OutCubic }
        }
    }
}
