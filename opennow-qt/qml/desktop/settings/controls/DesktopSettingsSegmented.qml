import QtQuick
import QtQuick.Controls
import OpenNOW

Item {
    id: root
    property var options: []
    property int selectedIndex: 0
    property int optionWidth: 72
    // Values (or labels) that are visible but not selectable, e.g. frame
    // rates the current membership tier does not entitle. Accepts raw values
    // matching options entries, or {label,value} objects.
    property var disabledValues: []
    // Optional per-option hint shown as a tooltip, e.g. "Requires Ultimate".
    property var disabledHint: ""
    signal selected(int index, var value)

    implicitWidth: options.reduce((total, option) => total + root.widthFor(option), 0)

    function optionValue(option) {
        return (typeof option === "object" && option !== null && option.value !== undefined)
            ? option.value : option
    }

    function widthFor(option) {
        return typeof option === "object" && option !== null && option.width !== undefined
            ? DesktopTokens.px(Math.max(1, Number(option.width))) : DesktopTokens.px(optionWidth)
    }

    function optionLabel(option) {
        if (typeof option === "object" && option !== null)
            return String(option.label !== undefined ? option.label : option.value)
        return String(option)
    }

    function focusSelectedOption() {
        const selected = optionsRepeater.itemAt(selectedIndex)
        if (selected && selected.enabled) {
            selected.forceActiveFocus()
            return
        }
        for (let i = 0; i < optionsRepeater.count; ++i) {
            const option = optionsRepeater.itemAt(i)
            if (option && option.enabled) {
                option.forceActiveFocus()
                return
            }
        }
    }

    function isDisabled(option) {
        const value = String(root.optionValue(option))
        for (let i = 0; i < root.disabledValues.length; ++i) {
            const disabled = root.disabledValues[i]
            if (String(disabled) === value
                    || String(root.optionValue(disabled)) === value)
                return true
        }
        if (typeof option === "object" && option !== null && option.enabled === false)
            return true
        return false
    }

    // Apple-style segmented control: a soft track with one raised thumb that
    // springs to the selected option. No borders or separators.
    implicitHeight: DesktopTokens.px(34)
    Rectangle {
        anchors.fill: parent
        radius: DesktopTokens.px(9)
        color: Theme.surfaceRaised
    }
    Rectangle {
        id: thumb
        readonly property Item target: optionsRepeater.count > root.selectedIndex && root.selectedIndex >= 0
            ? optionsRepeater.itemAt(root.selectedIndex) : null
        visible: target !== null && !target.locked
        x: target ? target.x + 2 : 0
        y: 2
        width: target ? target.width - 4 : 0
        height: root.height - 4
        radius: DesktopTokens.px(7)
        color: Theme.focus
        Behavior on x { enabled: !AppController.reducedMotion; NumberAnimation { duration: 380; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
        Behavior on width { enabled: !AppController.reducedMotion; NumberAnimation { duration: 380; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
    }
    Row {
        spacing: 0
        Repeater {
            id: optionsRepeater
            model: root.options
            delegate: AbstractButton {
                id: chip
                required property int index
                required property var modelData
                readonly property bool on: index === root.selectedIndex && !locked
                readonly property bool locked: root.isDisabled(modelData)
                objectName: "settingsOption-" + String(root.optionValue(modelData))
                readonly property string label: root.optionLabel(modelData)
                width: root.widthFor(modelData)
                height: root.height
                hoverEnabled: true
                enabled: !locked
                onClicked: root.selected(chip.index, chip.modelData)
                background: Rectangle {
                    x: 2; y: 2; width: parent.width - 4; height: parent.height - 4
                    radius: DesktopTokens.px(7)
                    color: !chip.on && chip.hovered ? Theme.surfaceHover : "transparent"
                    border.width: chip.activeFocus && AppController.inputMode !== "pointer" ? 2 : 0
                    border.color: Theme.label
                }
                opacity: chip.locked ? 0.4 : 1

                Text {
                    anchors.centerIn: parent
                    width: parent.width - DesktopTokens.px(8)
                    text: chip.label
                    color: chip.on ? Theme.focusText : Theme.label
                    Behavior on color { ColorAnimation { duration: 160 } }
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.px(14)
                    font.weight: chip.on ? Font.DemiBold : Font.Medium
                    horizontalAlignment: Text.AlignHCenter
                    elide: Text.ElideRight
                }
                ToolTip.visible: chip.locked && chip.hovered && root.disabledHint !== ""
                ToolTip.text: root.disabledHint
                ToolTip.delay: 400
            }
        }
    }
}
