pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// Small status chips in the corners chosen on the overlay's Heads up display page:
// recording time, microphone on, and a connection warning while the network is unstable.
Item {
    id: root
    objectName: "desktopStreamHud"
    property string statsCorner: ""
    property real statsHeight: 0
    readonly property real inset: DesktopTokens.px(24)
    readonly property real chip: DesktopTokens.px(32)
    readonly property int elapsedSeconds: Math.max(0, Math.floor(ShellStore.streamRecordingElapsedMs / 1000))

    function corner(key, fallback) {
        const value = String(ShellStore.settings[key] ?? fallback)
        return ["top-left", "top-right", "bottom-left", "bottom-right"].indexOf(value) >= 0 ? value : ""
    }
    readonly property var indicators: [
        {kind: "recording", corner: corner("hudRecordingPosition", "top-right"), shown: ShellStore.streamRecordingActive},
        {kind: "microphone", corner: corner("hudMicrophonePosition", "none"), shown: ShellStore.microphoneEnabled},
        {kind: "connection", corner: corner("hudConnectionPosition", "top-right"),
            shown: ShellStore.connectionHealth.status === "unstable"}
    ]

    Repeater {
        model: ["top-left", "top-right", "bottom-left", "bottom-right"]
        delegate: Column {
            id: stack
            required property string modelData
            readonly property bool atRight: modelData.endsWith("right")
            readonly property bool atBottom: modelData.startsWith("bottom")
            readonly property real offset: root.statsCorner === modelData ? root.statsHeight : 0
            spacing: DesktopTokens.px(8)
            x: atRight ? root.width - width - root.inset : root.inset
            y: atBottom ? root.height - height - root.inset - offset : root.inset + offset
            Repeater {
                model: root.indicators.filter(item => item.shown && item.corner === stack.modelData)
                delegate: Rectangle {
                    id: chip
                    required property var modelData
                    objectName: "hud-" + modelData.kind
                    x: stack.atRight ? stack.width - width : 0
                    height: root.chip
                    width: modelData.kind === "recording" ? timeText.implicitWidth + DesktopTokens.px(36) : root.chip
                    color: Qt.rgba(0, 0, 0, 0.72)
                    Accessible.role: Accessible.StaticText
                    Accessible.name: modelData.kind === "recording" ? qsTr("Recording · %1").arg(timeText.text)
                        : modelData.kind === "microphone" ? qsTr("Microphone on") : qsTr("Connection unstable")
                    Rectangle {
                        visible: chip.modelData.kind === "recording"
                        x: DesktopTokens.px(11)
                        anchors.verticalCenter: parent.verticalCenter
                        width: DesktopTokens.px(9)
                        height: width
                        radius: width / 2
                        color: "#F2665B"
                        SequentialAnimation on opacity {
                            running: chip.visible && chip.modelData.kind === "recording" && !AppController.reducedMotion
                            loops: Animation.Infinite
                            NumberAnimation { to: 0.35; duration: 700; easing.type: Easing.InOutSine }
                            NumberAnimation { to: 1; duration: 700; easing.type: Easing.InOutSine }
                        }
                    }
                    Text {
                        id: timeText
                        visible: chip.modelData.kind === "recording"
                        x: DesktopTokens.px(26)
                        anchors.verticalCenter: parent.verticalCenter
                        text: {
                            const s = root.elapsedSeconds
                            const h = Math.floor(s / 3600)
                            return (h > 0 ? h + ":" : "") + String(Math.floor(s / 60) % 60).padStart(2, "0")
                                + ":" + String(s % 60).padStart(2, "0")
                        }
                        color: "#FFFFFF"
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.px(15)
                        font.features: {"tnum": 1}
                    }
                    OverlayIcon {
                        visible: chip.modelData.kind !== "recording"
                        anchors.centerIn: parent
                        width: DesktopTokens.px(20)
                        height: width
                        name: chip.modelData.kind === "microphone" ? "mic" : "network"
                        ink: chip.modelData.kind === "connection" ? Theme.yellow : "#FFFFFF"
                    }
                }
            }
        }
    }
}
