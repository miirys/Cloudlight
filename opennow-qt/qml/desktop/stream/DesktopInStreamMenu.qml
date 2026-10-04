pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Window
import OpenNOW

// In-game overlay, modelled on GeForce NOW's: an opaque panel docked to the left
// edge over a dimmed game. Game and session time at the top, a plain action list,
// then the current stream details. No floating card, no status pills or dots.
FocusScope {
    id: root
    width: 1440
    height: 900
    property bool opened: false
    readonly property bool present: reveal.present
    visible: present
    enabled: opened
    focus: opened

    signal resumeRequested()
    signal inviteRequested()
    signal consoleModeRequested(bool enabled)
    signal fullscreenRequested()
    signal endSessionRequested()
    signal statsRequested()

    // Action ids (kept stable for callers): 0 resume, 1 invite, 2 console mode,
    // 3 fullscreen, 4 exit game, 5 statistics overlay, 6 microphone.
    property int selectedIndex: 0
    property int pendingAction: -1
    property bool closing: false
    property double nowMs: Date.now()
    readonly property var game: ShellStore.selectedGame || ({})
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var profile: session.negotiatedStreamProfile || session.streamProfile || ({})
    readonly property var live: ShellStore.streamer || ({})
    readonly property bool liveTelemetryAvailable: ["starting", "negotiating", "connecting", "streaming", "error"]
        .indexOf(String(live.status || "")) >= 0
    readonly property bool modePending: DesktopTokens.consoleModePending(Window.window)
    readonly property bool modeOn: DesktopTokens.consoleModeOn(Window.window)
    readonly property bool fullscreen: Window.window
        && Window.window.visibility === Window.FullScreen
    readonly property int outputWidth: Number(profile.width || 0)
    readonly property int outputHeight: Number(profile.height || 0)
    readonly property bool invitesAvailable: Boolean(ShellStore.socialCapabilities && ShellStore.socialCapabilities.invitesAvailable)
    readonly property string statsShortcut: String(ShellStore.settings.shortcutToggleStats ?? "Ctrl+N")

    function liveNumber(value) {
        return value === undefined || value === null || isNaN(Number(value)) ? null : Number(value)
    }
    function firstAvailable(primary, fallback) {
        return primary === undefined || primary === null ? fallback : primary
    }
    function liveValue(value) {
        return root.liveTelemetryAvailable ? value : undefined
    }
    readonly property var fpsValue: root.liveNumber(root.liveValue(root.firstAvailable(live.framesPerSecond, live.fps)))
    readonly property var bitrateValue: root.liveNumber(root.liveValue(root.firstAvailable(live.bitrateMbps, live.receiveBitrateMbps)))
    readonly property string fpsText: fpsValue === null ? qsTr("Measuring…") : qsTr("%1 FPS").arg(Math.round(fpsValue))
    readonly property string bitrateText: bitrateValue === null ? qsTr("Measuring…") : qsTr("%1 Mbps").arg(bitrateValue.toFixed(1))
    readonly property string codecText: {
        const raw = String(root.liveValue(live.codec) || profile.codec
            || ShellStore.runtimeStreamProfile.codec || ShellStore.settings.codec || "").toLowerCase()
        if (raw === "" || raw === "auto") return qsTr("Automatic")
        if (raw === "h265" || raw === "hevc") return "H.265"
        if (raw === "h264" || raw === "avc") return "H.264"
        return raw.toUpperCase()
    }
    readonly property string decoderText: {
        const raw = String(root.liveValue(live.mediaBackend) || "")
        return raw === "" ? qsTr("Starting…") : raw.charAt(0).toUpperCase() + raw.slice(1)
    }
    readonly property string resolutionText: outputWidth > 0 && outputHeight > 0
        ? outputWidth + " × " + outputHeight : qsTr("Starting…")

    // Visual order of the list, top to bottom.
    readonly property var actionOrder: ShellStore.microphoneCanToggle ? [0, 5, 3, 6, 2, 1, 4] : [0, 5, 3, 2, 1, 4]
    readonly property var actions: ({
        0: { title: qsTr("Resume game"), hint: "Esc", icon: "desktop-play.svg", enabled: true },
        1: { title: qsTr("Invite a friend"), hint: root.invitesAvailable ? "" : qsTr("Not available"), icon: "desktop-user-plus.svg", enabled: root.invitesAvailable },
        2: { title: root.modeOn ? qsTr("Switch to desktop mode") : qsTr("Switch to console mode"), hint: root.modePending ? qsTr("Switching…") : "", icon: "desktop-gamepad.svg", enabled: !root.modePending },
        3: { title: root.fullscreen ? qsTr("Exit full screen") : qsTr("Full screen"), hint: "F11", icon: root.fullscreen ? "desktop-collapse.svg" : "desktop-expand.svg", enabled: true },
        4: { title: qsTr("Exit game"), hint: "Ctrl+Shift+Q", icon: "desktop-logout.svg", enabled: true },
        5: { title: qsTr("Statistics overlay"), hint: root.statsShortcut, icon: "desktop-sliders.svg", enabled: true },
        6: { title: ShellStore.microphoneToggleAvailable ? ShellStore.microphoneActionLabel : qsTr("Microphone"),
             hint: ShellStore.microphoneEnabled ? qsTr("On") : ShellStore.microphoneLabel,
             icon: ShellStore.microphoneEnabled ? "desktop-mic.svg" : "desktop-mic-off.svg", enabled: ShellStore.microphoneCanToggle }
    })

    function runAction(index) {
        if (closing) return
        const action = root.actions[index]
        if (action && !action.enabled) return
        if (index === 6) {
            ShellStore.toggleMicrophone()
            return
        }
        pendingAction = index
        closing = true
        if (reveal.progress === 0) Qt.callLater(root.finishAction)
    }
    function finishAction() {
        // A hidden callback or rapid toggle must never dispatch the action twice.
        const action = pendingAction
        pendingAction = -1
        if (action === 0) resumeRequested()
        else if (action === 1) inviteRequested()
        else if (action === 2) consoleModeRequested(!root.modeOn)
        else if (action === 3) {
            fullscreenRequested()
            resumeRequested()
        }
        else if (action === 4) endSessionRequested()
        else if (action === 5) statsRequested()
    }
    function moveSelection(step) {
        const order = root.actionOrder
        let position = Math.max(0, order.indexOf(root.selectedIndex))
        for (let i = 0; i < order.length; ++i) {
            position = Math.max(0, Math.min(order.length - 1, position + step))
            if (root.actions[order[position]].enabled) break
        }
        root.selectedIndex = order[position]
    }
    function clockText() {
        if (ShellStore.streamStartedAtMs <= 0)
            return "—"
        const total = Math.floor(Math.max(0, nowMs - ShellStore.streamStartedAtMs) / 1000)
        const hours = Math.floor(total / 3600)
        const minutes = Math.floor((total % 3600) / 60)
        const seconds = total % 60
        return (hours > 0 ? hours + ":" + String(minutes).padStart(2, "0") : String(minutes))
            + ":" + String(seconds).padStart(2, "0")
    }

    MotionProgress {
        id: reveal
        objectName: "streamMenuMotion"
        shown: root.opened && !root.closing
        exitDuration: 120
        onHidden: if (root.closing && root.pendingAction >= 0) root.finishAction()
    }

    // Dim the game so the panel reads clearly; clicking outside resumes.
    Rectangle {
        anchors.fill: parent
        color: "#000000"
        opacity: 0.55 * reveal.progress
        TapHandler { onTapped: root.runAction(0) }
    }

    Rectangle {
        id: panel
        width: Math.min(DesktopTokens.px(400), root.width)
        height: root.height
        x: -width * (1 - reveal.progress)
        color: Theme.surface
        Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: Theme.seam }

        // Game header.
        Item {
            id: header
            x: DesktopTokens.px(28)
            y: DesktopTokens.px(36)
            width: parent.width - DesktopTokens.px(56)
            height: DesktopTokens.px(84)
            RoundedArtwork {
                width: DesktopTokens.px(63)
                height: DesktopTokens.px(84)
                artwork: String(root.game.imageUrl || root.game.heroImageUrl || "")
                cornerRadius: DesktopTokens.radius
                scrimStart: 1
                fallbackColor: Theme.surfaceRaised
            }
            Column {
                x: DesktopTokens.px(81)
                width: parent.width - x
                anchors.verticalCenter: parent.verticalCenter
                spacing: DesktopTokens.px(6)
                Text {
                    width: parent.width
                    text: String(root.game.title || qsTr("GeForce NOW"))
                    color: Theme.label
                    font.family: Theme.displayFont
                    font.pixelSize: DesktopTokens.headingSize
                    font.weight: Font.Bold
                    elide: Text.ElideRight
                    maximumLineCount: 2
                    wrapMode: Text.WordWrap
                }
                Text {
                    text: qsTr("Session time %1").arg(root.clockText())
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                    font.features: { "tnum": 1 }
                }
            }
        }

        Rectangle { id: headerRule; x: 0; y: header.y + header.height + DesktopTokens.px(24); width: parent.width; height: 1; color: DesktopTokens.seamSoft }

        // Actions.
        Column {
            id: actionList
            x: DesktopTokens.px(16)
            y: headerRule.y + DesktopTokens.px(16)
            width: parent.width - DesktopTokens.px(32)
            spacing: 0
            Repeater {
                model: root.actionOrder
                delegate: Item {
                    id: actionRow
                    required property int modelData
                    readonly property var action: root.actions[modelData]
                    readonly property bool selected: root.selectedIndex === modelData
                    readonly property bool exitAction: modelData === 4
                    width: actionList.width
                    height: DesktopTokens.px(exitAction ? 73 : 56)
                    opacity: action.enabled ? 1 : 0.4
                    Accessible.role: Accessible.Button
                    Accessible.name: action.title
                    Rectangle {
                        visible: actionRow.exitAction
                        x: DesktopTokens.px(12); y: DesktopTokens.px(8); width: parent.width - DesktopTokens.px(24); height: 1
                        color: DesktopTokens.seamSoft
                    }
                    Rectangle {
                        id: actionBackground
                        y: actionRow.exitAction ? DesktopTokens.px(17) : 0
                        width: parent.width
                        height: DesktopTokens.px(56)
                        radius: DesktopTokens.radius
                        color: actionRow.selected && actionRow.action.enabled ? Theme.surfaceHover : "transparent"
                        Rectangle {
                            visible: actionRow.selected && actionRow.action.enabled
                            x: 0; y: DesktopTokens.px(12)
                            width: DesktopTokens.px(4); height: parent.height - DesktopTokens.px(24)
                            radius: width / 2
                            color: actionRow.exitAction ? Theme.coral : Theme.focus
                        }
                        DesktopGlyph {
                            x: DesktopTokens.px(18)
                            anchors.verticalCenter: parent.verticalCenter
                            width: DesktopTokens.px(22); height: width
                            icon: actionRow.action.icon
                        }
                        Text {
                            x: DesktopTokens.px(56)
                            width: hintLabel.x - x - DesktopTokens.px(12)
                            anchors.verticalCenter: parent.verticalCenter
                            text: actionRow.action.title
                            color: actionRow.exitAction ? Theme.coral : Theme.label
                            font.family: Theme.bodyFont
                            font.pixelSize: DesktopTokens.bodySize
                            font.weight: actionRow.selected ? Font.DemiBold : Font.Medium
                            elide: Text.ElideRight
                        }
                        Text {
                            id: hintLabel
                            anchors.right: parent.right
                            anchors.rightMargin: DesktopTokens.px(18)
                            anchors.verticalCenter: parent.verticalCenter
                            text: actionRow.action.hint
                            color: Theme.textMuted
                            font.family: Theme.bodyFont
                            font.pixelSize: DesktopTokens.smallSize
                        }
                        HoverHandler { onHoveredChanged: if (hovered && actionRow.action.enabled) root.selectedIndex = actionRow.modelData }
                        TapHandler { onTapped: root.runAction(actionRow.modelData) }
                    }
                }
            }
        }

        // Stream details, plain label/value rows.
        Column {
            x: DesktopTokens.px(28)
            anchors.bottom: parent.bottom
            anchors.bottomMargin: DesktopTokens.px(32)
            width: parent.width - DesktopTokens.px(56)
            spacing: DesktopTokens.px(4)
            Text {
                text: qsTr("Stream")
                color: Theme.label
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.bodySize
                font.weight: Font.DemiBold
                bottomPadding: DesktopTokens.px(8)
            }
            Repeater {
                model: [
                    { label: qsTr("Resolution"), value: root.resolutionText },
                    { label: qsTr("Frame rate"), value: root.fpsText },
                    { label: qsTr("Bit rate"), value: root.bitrateText },
                    { label: qsTr("Video codec"), value: root.codecText },
                    { label: qsTr("Decoder"), value: root.decoderText }
                ]
                delegate: Item {
                    id: detailRow
                    required property var modelData
                    width: parent.width
                    height: DesktopTokens.px(30)
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: detailRow.modelData.label
                        color: Theme.textMuted
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                    }
                    Text {
                        anchors.right: parent.right
                        anchors.verticalCenter: parent.verticalCenter
                        text: detailRow.modelData.value
                        color: Theme.label
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                        font.features: { "tnum": 1 }
                    }
                }
            }
        }
    }

    Timer { interval: 1000; repeat: true; running: root.visible; onTriggered: root.nowMs = Date.now() }
    onOpenedChanged: if (opened) {
        closing = false
        pendingAction = -1
        selectedIndex = 0
        forceActiveFocus()
    }
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.runAction(0)
            event.accepted = true
        } else if (event.key === Qt.Key_Down) {
            root.moveSelection(1)
            event.accepted = true
        } else if (event.key === Qt.Key_Up) {
            root.moveSelection(-1)
            event.accepted = true
        } else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
            root.runAction(root.selectedIndex)
            event.accepted = true
        } else if (event.key === Qt.Key_Q
                && (event.modifiers & (Qt.ControlModifier | Qt.ShiftModifier
                    | Qt.AltModifier | Qt.MetaModifier))
                    === (Qt.ControlModifier | Qt.ShiftModifier)) {
            root.runAction(4)
            event.accepted = true
        }
    }
}
