pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// Where on-screen indicators sit during play, with a live preview of the layout.
OverlayPage {
    id: page
    property var menu
    pageName: "hud"
    title: qsTr("Heads up display")

    readonly property var corners: ["top-left", "top-right", "bottom-left", "bottom-right"]
    readonly property var cornerLabels: ({
        "top-left": qsTr("Upper left"), "top-right": qsTr("Upper right"),
        "bottom-left": qsTr("Lower left"), "bottom-right": qsTr("Lower right"), "none": qsTr("None")
    })
    function position(key, fallback) {
        return String(ShellStore.settings[key] ?? fallback)
    }
    function step(key, fallback, direction, allowNone) {
        const choices = allowNone ? page.corners.concat(["none"]) : page.corners
        const index = Math.max(0, choices.indexOf(page.position(key, fallback)))
        ShellStore.setSetting(key, choices[(index + direction + choices.length) % choices.length])
    }
    // Indicators in preview order: statistics first, as the widest chip.
    readonly property var indicators: [
        {key: "statsOverlayPosition", fallback: "top-right", icon: "statistics", wide: true},
        {key: "hudRecordingPosition", fallback: "top-right", icon: "record", wide: false},
        {key: "hudMicrophonePosition", fallback: "none", icon: "mic", wide: false},
        {key: "hudConnectionPosition", fallback: "top-right", icon: "network", wide: false}
    ]

    OverlaySectionLabel { text: qsTr("Layout"); topPadding: OverlayStyle.u(39) }
    Item { width: 1; height: OverlayStyle.u(5) }
    Rectangle {
        id: preview
        objectName: "overlayHudPreview"
        x: OverlayStyle.gutter
        width: parent.width - OverlayStyle.gutter * 2 - OverlayStyle.u(10)
        height: Math.round(width * 9 / 16)
        color: OverlayStyle.field
        Component {
            id: chipDelegate
            Rectangle {
                id: chip
                required property var modelData
                width: OverlayStyle.u(chip.modelData.wide ? 65 : 32)
                height: OverlayStyle.u(32)
                color: "#000000"
                OverlayIcon {
                    anchors.centerIn: parent
                    width: OverlayStyle.u(20)
                    height: width
                    name: chip.modelData.icon
                    ink: "#FFFFFF"
                }
            }
        }
        Repeater {
            model: page.corners
            // As in the reference: the small indicators stack at the edge and the wider
            // statistics chip sits beside them, toward the middle.
            delegate: Row {
                id: corner
                required property string modelData
                readonly property bool atRight: modelData.endsWith("right")
                readonly property bool atBottom: modelData.startsWith("bottom")
                readonly property var here: page.indicators.filter(item => page.position(item.key, item.fallback) === modelData)
                x: atRight ? preview.width - width - OverlayStyle.u(10) : OverlayStyle.u(10)
                y: atBottom ? preview.height - height - OverlayStyle.u(10) : OverlayStyle.u(10)
                spacing: OverlayStyle.u(11)
                layoutDirection: atRight ? Qt.RightToLeft : Qt.LeftToRight
                Column {
                    spacing: OverlayStyle.u(11)
                    Repeater { model: corner.here.filter(item => !item.wide); delegate: chipDelegate }
                }
                Repeater { model: corner.here.filter(item => item.wide); delegate: chipDelegate }
            }
        }
    }

    component PositionRow: OverlayRow {
        required property string setting
        required property string fallback
        property bool allowNone: true
        trailing: "stepper"
        height: OverlayStyle.u(85)
        titleCenter: OverlayStyle.u(36)
        stepperCenter: titleCenter + OverlayStyle.u(10)
        stepperValueWidth: OverlayStyle.u(202)
        stepperMargin: OverlayStyle.u(19)
        valueText: page.cornerLabels[page.position(setting, fallback)] || ""
        onStepped: direction => page.step(setting, fallback, direction, allowNone)
        onActivated: stepped(1)
    }
    OverlaySectionLabel { text: qsTr("Status indicators"); topPadding: OverlayStyle.u(28) }
    PositionRow { objectName: "overlayHudRecording"; title: qsTr("Record"); setting: "hudRecordingPosition"; fallback: "top-right" }
    PositionRow { title: qsTr("Microphone"); setting: "hudMicrophonePosition"; fallback: "none" }
    OverlaySectionLabel { text: qsTr("Network"); topPadding: OverlayStyle.u(22); bottomPadding: 0 }
    PositionRow { title: qsTr("Connection status"); setting: "hudConnectionPosition"; fallback: "top-right"; titleCenter: OverlayStyle.u(29) }
    OverlaySectionLabel { text: qsTr("Statistics"); topPadding: OverlayStyle.u(22); bottomPadding: 0 }
    PositionRow { title: qsTr("Position"); setting: "statsOverlayPosition"; fallback: "top-right"; allowNone: false; titleCenter: OverlayStyle.u(29) }
}
