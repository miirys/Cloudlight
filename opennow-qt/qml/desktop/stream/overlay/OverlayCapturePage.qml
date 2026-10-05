import QtQuick
import OpenNOW

// Recording and Instant Replay settings.
OverlayPage {
    id: page
    property var menu
    pageName: "capture"
    title: qsTr("Video capture")

    function cycle(key, choices, fallback, direction) {
        const current = ShellStore.settings[key] ?? fallback
        const index = Math.max(0, choices.indexOf(current))
        ShellStore.setSetting(key, choices[(index + direction + choices.length) % choices.length])
    }

    OverlayDescribedToggle {
        objectName: "overlayReplayEnabled"
        title: qsTr("Instant Replay")
        description: ShellStore.streamReplayEnabled
            ? qsTr("Keeps the last moments of play so you can save them. Turning it off stops it now.")
            : qsTr("Keeps the last moments of play so you can save them. Starts with your next session.")
        checked: ShellStore.replayBufferRequested
        onActivated: ShellStore.setSetting("replayBufferEnabled", !checked)
    }
    OverlayRow {
        objectName: "overlayReplayLength"
        title: qsTr("Instant Replay length")
        trailing: "stepper"
        readonly property var choices: [15, 30, 60, 120]
        valueText: qsTr("%1 s").arg(Number(ShellStore.settings.replayBufferSeconds || 30))
        onStepped: direction => page.cycle("replayBufferSeconds", choices, 30, direction)
        onActivated: stepped(1)
    }
    OverlayDivider {}
    OverlaySectionLabel { text: qsTr("Recording") }
    OverlayRow {
        objectName: "overlayRecordingResolution"
        title: qsTr("Resolution")
        trailing: "stepper"
        readonly property var choices: ["720p", "1080p", "1440p"]
        valueText: String(ShellStore.settings.recordingResolution || "720p")
        onStepped: direction => page.cycle("recordingResolution", choices, "720p", direction)
        onActivated: stepped(1)
    }
    OverlayRow {
        objectName: "overlayRecordingFps"
        title: qsTr("Frame rate")
        trailing: "stepper"
        readonly property var choices: [30, 60]
        valueText: qsTr("%1 FPS").arg(Number(ShellStore.settings.recordingFps || 30))
        onStepped: direction => page.cycle("recordingFps", choices, 30, direction)
        onActivated: stepped(1)
    }
    Text {
        x: OverlayStyle.gutter
        width: parent.width - x * 2
        topPadding: OverlayStyle.u(12)
        text: qsTr("Resolution and frame rate apply to the next recording.")
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        wrapMode: Text.WordWrap
    }
}
