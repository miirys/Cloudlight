import QtQuick
import QtQuick.Window
import OpenNOW

// The panel's first page, in GeForce NOW's order: session time, gallery, capture,
// game filters, microphone, then Statistics and Quit pinned to the bottom.
OverlayPage {
    id: page
    property var menu
    pageName: "main"
    title: "Cloudlight"

    readonly property var bindings: ShellStore.streamShortcutBindings()
    function binding(action) {
        const keys = page.bindings[action] || []
        return keys.length ? String(keys[0]) : ""
    }
    function hint(action, verb) {
        const key = page.binding(action)
        return key === "" ? verb : verb === "" ? key : key + " - " + verb
    }
    readonly property bool streaming: String((ShellStore.streamer || {}).status || "") === "streaming"

    // Session time remaining: the tier's session length, capped by membership hours left.
    readonly property var subscription: ShellStore.subscription || ({})
    readonly property string tier: String(subscription.membershipTier || "").toUpperCase()
    readonly property string tierName: tier === "" ? "" : tier.charAt(0) + tier.slice(1).toLowerCase()
    readonly property int tierLimitSeconds: tier === "ULTIMATE" ? 8 * 3600
        : tier === "PERFORMANCE" || tier === "PRIORITY" ? 6 * 3600
        : tier === "FREE" ? 3600 : 0
    readonly property real membershipSeconds: subscription.isUnlimited === true
        || subscription.remainingHours === undefined || subscription.remainingHours === null
        ? Infinity : Math.max(0, Number(subscription.remainingHours) * 3600)
    readonly property int elapsedSeconds: ShellStore.streamStartedAtMs > 0
        ? Math.floor(Math.max(0, menu.nowMs - ShellStore.streamStartedAtMs) / 1000) : 0
    readonly property bool countdown: tierLimitSeconds > 0 && ShellStore.streamStartedAtMs > 0
    readonly property int remainingSeconds: countdown
        ? Math.max(0, Math.min(tierLimitSeconds - elapsedSeconds, membershipSeconds - elapsedSeconds)) : 0
    function clock(total) {
        const hours = Math.floor(total / 3600)
        const minutes = Math.floor((total % 3600) / 60)
        const seconds = total % 60
        return String(hours).padStart(2, "0") + ":" + String(minutes).padStart(2, "0")
            + ":" + String(seconds).padStart(2, "0")
    }

    // Newest captures first, for the strip.
    readonly property var captures: ShellStore.mediaItems.slice()
        .sort((a, b) => Number(b.createdAtMs || 0) - Number(a.createdAtMs || 0)).slice(0, 12)

    readonly property var filterState: ShellStore.settings.gameFilters || ({})
    readonly property int activeStyle: Number(filterState.active || 0)
    readonly property string activeStyleName: {
        if (activeStyle <= 0) return qsTr("None")
        const style = (filterState.styles || [])[activeStyle - 1] || {}
        return String(style.name || "") !== "" ? String(style.name) : qsTr("Style %1").arg(activeStyle)
    }

    OverlayRow {
        objectName: "overlaySessionTime"
        icon: "clock"
        title: page.countdown ? qsTr("Session time remaining") : qsTr("Session time")
        subtitle: page.tierName
        trailing: "text"
        valueText: page.countdown ? page.clock(page.remainingSeconds) : page.clock(page.elapsedSeconds)
        activeFocusOnTab: false
        showHighlight: false
        titleCenter: OverlayStyle.u(34)
        height: OverlayStyle.u(97)
    }
    OverlayDivider {}

    OverlayRow {
        objectName: "overlayGallery"
        icon: "gallery"
        title: qsTr("Gallery")
        trailing: "chevron"
        height: OverlayStyle.u(66)
        onActivated: page.menu.openPage("gallery")
    }
    // Thumbnail strip: Left and Right pick a capture, Return opens it.
    OverlayFocusable {
        id: strip
        objectName: "overlayGalleryStrip"
        width: parent.width
        height: OverlayStyle.u(126)
        visible: page.captures.length > 0
        available: page.captures.length > 0
        showHighlight: false
        property int current: 0
        readonly property int thumb: OverlayStyle.u(115)
        readonly property int gap: OverlayStyle.u(11)
        readonly property int visibleCount: 4
        property int first: 0
        Accessible.role: Accessible.List
        Accessible.name: qsTr("Recent captures")
        onCurrentChanged: {
            if (current < first) first = current
            else if (current >= first + visibleCount) first = current - visibleCount + 1
        }
        onStepped: direction => current = Math.max(0, Math.min(page.captures.length - 1, current + direction))
        onActivated: {
            const item = page.captures[current]
            if (item && item.filePath) AppController.openLocalPath(String(item.filePath), false)
        }
        OverlayIcon {
            x: OverlayStyle.u(61) - width / 2
            anchors.verticalCenter: viewport.verticalCenter
            width: OverlayStyle.u(28); height: width
            name: "chevron-left"
            ink: strip.first > 0 ? OverlayStyle.text : OverlayStyle.disabled
            TapHandler {
                gesturePolicy: TapHandler.ReleaseWithinBounds
                grabPermissions: PointerHandler.CanTakeOverFromAnything
                onTapped: strip.first = Math.max(0, strip.first - strip.visibleCount)
            }
        }
        Item {
            id: viewport
            x: OverlayStyle.u(96)
            y: OverlayStyle.u(11)
            width: strip.thumb * strip.visibleCount + strip.gap * (strip.visibleCount - 1)
            height: strip.thumb
            clip: true
            Row {
                x: -strip.first * (strip.thumb + strip.gap)
                spacing: strip.gap
                Behavior on x { NumberAnimation { duration: OverlayStyle.pageDuration; easing.type: Easing.OutCubic } }
                Repeater {
                    model: page.captures
                    delegate: Rectangle {
                        id: capture
                        required property var modelData
                        required property int index
                        width: strip.thumb
                        height: strip.thumb
                        color: "#000000"
                        Image {
                            anchors.fill: parent
                            source: String(capture.modelData.kind === "recording"
                                ? capture.modelData.thumbnailUrl || "" : capture.modelData.mediaUrl || "")
                            fillMode: Image.PreserveAspectCrop
                            asynchronous: true
                            sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
                        }
                        OverlayIcon {
                            visible: capture.modelData.kind === "recording"
                            x: OverlayStyle.u(8); y: OverlayStyle.u(8)
                            width: OverlayStyle.u(24); height: width
                            name: "video"
                            ink: "#FFFFFF"
                        }
                        Rectangle {
                            anchors.fill: parent
                            color: "transparent"
                            border.width: Math.max(1, Math.round(OverlayStyle.uf(3)))
                            border.color: OverlayStyle.accent
                            opacity: strip.activeFocus && strip.current === capture.index ? 1 : 0
                            Behavior on opacity { NumberAnimation { duration: OverlayStyle.fastDuration } }
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            gesturePolicy: TapHandler.ReleaseWithinBounds
                            grabPermissions: PointerHandler.CanTakeOverFromAnything
                            onTapped: {
                                strip.current = capture.index
                                if (capture.modelData.filePath)
                                    AppController.openLocalPath(String(capture.modelData.filePath), false)
                            }
                        }
                    }
                }
            }
        }
        OverlayIcon {
            x: OverlayStyle.u(621) - width / 2
            anchors.verticalCenter: viewport.verticalCenter
            width: OverlayStyle.u(28); height: width
            name: "chevron-right"
            readonly property bool more: strip.first + strip.visibleCount < page.captures.length
            ink: more ? OverlayStyle.text : OverlayStyle.disabled
            TapHandler {
                gesturePolicy: TapHandler.ReleaseWithinBounds
                grabPermissions: PointerHandler.CanTakeOverFromAnything
                onTapped: strip.first = Math.min(Math.max(0, page.captures.length - strip.visibleCount),
                                                 strip.first + strip.visibleCount)
            }
        }
    }
    Text {
        visible: page.captures.length === 0
        x: OverlayStyle.textInset
        width: parent.width - x - OverlayStyle.gutter
        bottomPadding: OverlayStyle.u(18)
        text: ShellStore.mediaState === "loading" ? qsTr("Loading captures…")
            : qsTr("Your screenshots and recordings will appear here.")
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        wrapMode: Text.WordWrap
    }
    OverlayDivider {}

    OverlayRow {
        objectName: "overlayRecord"
        icon: "record"
        title: ShellStore.streamRecordingActive ? qsTr("Recording") : qsTr("Record")
        subtitle: page.hint("toggle-recording", ShellStore.streamRecordingActive ? qsTr("Stop") : qsTr("Start"))
        trailing: ShellStore.streamRecordingActive ? "stop" : "play"
        available: ShellStore.streamRecordingActive || page.streaming
        onActivated: page.menu.runAction(8)
    }
    OverlayRow {
        objectName: "overlayInstantReplay"
        icon: "instant-replay"
        title: qsTr("Instant Replay")
        readonly property bool requested: ShellStore.replayBufferRequested
        readonly property int seconds: Number(ShellStore.settings.replayBufferSeconds || 30)
        subtitle: ShellStore.streamReplayEnabled && requested
            ? qsTr("Keeping the last %1 seconds").arg(seconds)
            : requested ? qsTr("Starts with your next session") : qsTr("Off")
        trailing: "toggle"
        checked: requested
        onActivated: ShellStore.setSetting("replayBufferEnabled", !requested)
    }
    OverlayRow {
        objectName: "overlaySaveReplay"
        visible: ShellStore.streamReplayEnabled && ShellStore.replayBufferRequested
        icon: "replay"
        title: qsTr("Save Instant Replay")
        subtitle: page.hint("save-clip", qsTr("Save last %1 seconds").arg(Number(ShellStore.settings.replayBufferSeconds || 30)))
        available: page.streaming && !ShellStore.streamClipBusy
        onActivated: page.menu.runAction(9)
    }
    OverlayRow {
        objectName: "overlayScreenshot"
        icon: "screenshot"
        title: qsTr("Screenshot")
        subtitle: page.binding("screenshot")
        onActivated: page.menu.runAction(7)
    }
    OverlayRow {
        objectName: "overlayGameFilters"
        icon: "filters"
        title: qsTr("Game filters")
        subtitle: page.activeStyleName
        trailing: "chevron"
        onActivated: page.menu.openPage("filters")
    }
    OverlayDivider {}

    OverlayRow {
        objectName: "overlayMicrophone"
        icon: ShellStore.microphoneEnabled ? "mic" : "mic-off"
        title: qsTr("Microphone")
        subtitle: ShellStore.microphoneToggleAvailable
            ? page.hint("toggle-microphone", qsTr("Toggle on/off")) : ShellStore.microphoneLabel
        trailing: "toggle"
        checked: ShellStore.microphoneEnabled
        available: ShellStore.microphoneCanToggle
        onActivated: ShellStore.toggleMicrophone()
    }

    footer: [
        OverlayDivider {},
        Item { width: 1; height: OverlayStyle.u(13) },
        OverlayRow {
            objectName: "overlayStatistics"
            height: OverlayStyle.u(109)
            icon: "statistics"
            title: qsTr("Statistics")
            subtitle: page.menu.statsShortcut === "" ? "" : page.menu.statsShortcut + " - " + qsTr("Change format")
            trailing: "stepper"
            readonly property var modes: ["off", "compact", "expanded"]
            readonly property var labels: [qsTr("Off"), qsTr("Compact"), qsTr("Detailed")]
            valueText: labels[Math.max(0, modes.indexOf(page.menu.statsMode))]
            onStepped: direction => {
                const index = Math.max(0, modes.indexOf(page.menu.statsMode))
                page.menu.statsModeRequested(modes[(index + direction + modes.length) % modes.length])
            }
            onActivated: stepped(1)
        },
        OverlayRow {
            objectName: "overlayQuit"
            icon: "quit"
            title: String((ShellStore.selectedGame || {}).title || "") !== ""
                ? qsTr("Quit %1").arg(String(ShellStore.selectedGame.title)) : qsTr("Quit game")
            subtitle: page.binding("stop-stream")
            onActivated: page.menu.runAction(4)
        },
        Item { width: 1; height: OverlayStyle.u(11) }
    ]
}
