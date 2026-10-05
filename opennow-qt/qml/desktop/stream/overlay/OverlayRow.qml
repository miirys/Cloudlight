import QtQuick
import OpenNOW

// One overlay entry: icon column, white title, grey subtitle (usually the shortcut) and
// a trailing affordance on the right, the way GeForce NOW lays out its panel rows.
// trailing: "none", "chevron", "play", "stop", "toggle", "text", "stepper", "external",
// "folder", "dropdown" or "select" (value in a right-hand column with a caret).
OverlayFocusable {
    id: root
    property string icon: ""
    property string title: ""
    property string subtitle: ""
    property string trailing: "none"
    property bool checked: false
    property string valueText: ""
    property bool bold: false
    property color iconInk: OverlayStyle.icon
    // Stepper geometry: GeForce NOW's HUD rows use a wider picker set further in.
    property real stepperValueWidth: OverlayStyle.u(150)
    property real stepperMargin: OverlayStyle.u(9)
    property real stepperCenter: subtitle !== "" ? (titleCenter + subtitleCenter) / 2 : titleCenter
    property real titleInset: icon !== "" ? OverlayStyle.textInset : OverlayStyle.gutter
    width: parent ? parent.width : 0
    implicitHeight: subtitle !== "" ? OverlayStyle.rowHeight : OverlayStyle.compactRowHeight
    height: implicitHeight
    Accessible.role: trailing === "toggle" ? Accessible.CheckBox : Accessible.Button
    Accessible.name: title
    Accessible.description: subtitle
    Accessible.checkable: trailing === "toggle"
    Accessible.checked: checked

    readonly property color ink: available ? OverlayStyle.text : OverlayStyle.disabled
    readonly property color subInk: available ? OverlayStyle.subtitle : OverlayStyle.disabled
    // With a subtitle the title sits high and the icon aligns with it, as in the reference.
    property real titleCenter: subtitle !== "" ? OverlayStyle.u(36) : height / 2
    readonly property real subtitleCenter: titleCenter + OverlayStyle.u(28)

    OverlayIcon {
        visible: root.icon !== ""
        name: root.icon
        ink: root.available ? root.iconInk : OverlayStyle.disabled
        width: OverlayStyle.iconSize
        height: width
        x: OverlayStyle.iconCenter - width / 2
        y: root.titleCenter - height / 2
    }
    Text {
        id: titleText
        x: root.titleInset
        width: trailingBox.x - x - OverlayStyle.u(16)
        y: root.titleCenter - height / 2
        text: root.title
        color: root.ink
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.bodySize
        font.weight: root.bold ? OverlayStyle.strongWeight : OverlayStyle.bodyWeight
        elide: Text.ElideRight
    }
    Text {
        visible: root.subtitle !== ""
        x: titleText.x
        width: titleText.width
        y: root.subtitleCenter - height / 2
        text: root.subtitle
        color: root.subInk
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        elide: Text.ElideRight
    }

    Item {
        id: trailingBox
        anchors.right: parent.right
        anchors.rightMargin: OverlayStyle.gutter
        width: root.trailing === "stepper" ? stepper.implicitWidth + root.stepperMargin
            : root.trailing === "toggle" ? toggle.width + OverlayStyle.u(16)
            : root.trailing === "text" ? valueLabel.implicitWidth
            : root.trailing === "select" ? root.width - OverlayStyle.u(422) - OverlayStyle.gutter
            : root.trailing === "none" ? 0 : OverlayStyle.u(28)
        height: root.height
        OverlayIcon {
            visible: ["chevron", "play", "stop", "external", "folder", "dropdown"].indexOf(root.trailing) >= 0
            anchors.right: parent.right
            anchors.rightMargin: root.trailing === "chevron" ? OverlayStyle.u(1)
                : root.trailing === "play" || root.trailing === "stop" ? OverlayStyle.u(8) : 0
            y: (root.trailing === "play" || root.trailing === "stop" ? root.height / 2 : root.titleCenter) - height / 2
            width: OverlayStyle.u(root.trailing === "play" || root.trailing === "stop" ? 32 : 28)
            height: width
            name: root.trailing === "chevron" ? "chevron-right"
                : root.trailing === "dropdown" ? "expand-more" : root.trailing
            ink: root.available ? OverlayStyle.text : OverlayStyle.disabled
        }
        OverlayToggle {
            id: toggle
            visible: root.trailing === "toggle"
            anchors.right: parent.right
            anchors.rightMargin: OverlayStyle.u(16)
            y: root.titleCenter - height / 2
            checked: root.checked
            available: root.available
        }
        Text {
            id: valueLabel
            visible: root.trailing === "text"
            anchors.right: parent.right
            y: root.titleCenter - height / 2 - OverlayStyle.u(4)
            text: root.valueText
            color: root.subInk
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.subtitleSize
            font.features: { "tnum": 1 }
        }
        Text {
            visible: root.trailing === "select"
            width: parent.width - OverlayStyle.u(60)
            anchors.verticalCenter: parent.verticalCenter
            text: root.valueText
            color: root.ink
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.subtitleSize
            elide: Text.ElideRight
        }
        OverlayIcon {
            visible: root.trailing === "select"
            anchors.right: parent.right
            anchors.rightMargin: OverlayStyle.u(28)
            anchors.verticalCenter: parent.verticalCenter
            width: OverlayStyle.u(30)
            height: width
            name: "dropdown"
            ink: root.ink
        }
        OverlayStepper {
            id: stepper
            visible: root.trailing === "stepper"
            anchors.right: parent.right
            anchors.rightMargin: root.stepperMargin
            // Centred between the title and subtitle lines, as in the reference.
            y: root.stepperCenter - height / 2
            valueWidth: root.stepperValueWidth
            valueText: root.valueText
            available: root.available
            onStepped: direction => root.stepped(direction)
        }
    }
}
