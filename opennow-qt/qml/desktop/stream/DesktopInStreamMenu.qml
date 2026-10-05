pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Window
import OpenNOW

// In-game panel (Ctrl+G), modelled on GeForce NOW's sidebar: an opaque panel docked to
// the left edge over a dimmed game. The game and session time sit at the top, then the
// capture tiles, quick toggles and the gallery and shortcut pages, with the live stream
// details and Exit game at the bottom. Every entry here drives a real stream action;
// features the session cannot do are left out rather than shown disabled.
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
    // 3 fullscreen, 4 exit game, 5 statistics overlay, 6 microphone, 7 screenshot,
    // 8 recording, 9 save instant replay, 10 anti-AFK, 11 gallery page,
    // 12 shortcuts page, 13 open captures folder, 14 back to the main page.
    property int selectedIndex: 7
    property int pendingAction: -1
    property bool closing: false
    property string page: ""
    property double nowMs: Date.now()
    readonly property var game: ShellStore.selectedGame || ({})
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var profile: session.negotiatedStreamProfile || session.streamProfile || ({})
    readonly property var live: ShellStore.streamer || ({})
    readonly property bool streaming: String(live.status || "") === "streaming"
    readonly property bool liveTelemetryAvailable: ["starting", "negotiating", "connecting", "streaming", "error"]
        .indexOf(String(live.status || "")) >= 0
    readonly property bool modePending: DesktopTokens.consoleModePending(Window.window)
    readonly property bool modeOn: DesktopTokens.consoleModeOn(Window.window)
    readonly property bool fullscreen: Window.window
        && Window.window.visibility === Window.FullScreen
    readonly property int outputWidth: Number(profile.width || 0)
    readonly property int outputHeight: Number(profile.height || 0)
    readonly property bool invitesAvailable: Boolean(ShellStore.socialCapabilities && ShellStore.socialCapabilities.invitesAvailable)
    readonly property bool replayAvailable: ShellStore.streamReplayEnabled && ShellStore.replayBufferRequested
    readonly property var bindings: ShellStore.streamShortcutBindings()
    readonly property string statsShortcut: String(ShellStore.settings.shortcutToggleStats ?? "Ctrl+N")

    function binding(action) {
        const keys = root.bindings[action] || []
        return keys.length ? String(keys[0]) : ""
    }
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
    readonly property string remainingText: {
        const sub = ShellStore.subscription
        if (!sub || sub.remainingHours === undefined || sub.remainingHours === null)
            return ""
        return qsTr("%1 h left").arg(Math.max(0, Number(sub.remainingHours)).toFixed(1))
    }

    // Everything the panel can show, by action id. `kind` picks the row style.
    readonly property var actions: ({
        0: { title: qsTr("Resume game"), icon: "close", enabled: true },
        1: { title: qsTr("Invite a friend"), icon: "invite", kind: "row", enabled: root.invitesAvailable },
        2: { title: root.modeOn ? qsTr("Switch to desktop mode") : qsTr("Switch to console mode"),
             value: root.modePending ? qsTr("Switching…") : "", icon: "arrows", kind: "row", enabled: !root.modePending },
        3: { title: qsTr("Full screen"), icon: root.fullscreen ? "collapse" : "expand", kind: "switch",
             on: root.fullscreen, hint: root.binding("toggle-fullscreen"), enabled: true },
        4: { title: qsTr("Exit game"), icon: "exit", enabled: true },
        5: { title: qsTr("Statistics overlay"), icon: "chart", kind: "row", hint: root.statsShortcut, enabled: true },
        6: { title: qsTr("Microphone"), icon: ShellStore.microphoneEnabled ? "mic" : "mic-off", kind: "switch",
             on: ShellStore.microphoneEnabled, hint: root.binding("toggle-microphone"), enabled: ShellStore.microphoneCanToggle },
        7: { title: qsTr("Screenshot"), icon: "camera", kind: "tile", hint: root.binding("screenshot"), enabled: true },
        8: { title: ShellStore.streamRecordingActive ? qsTr("Stop recording") : qsTr("Record"),
             icon: ShellStore.streamRecordingActive ? "stop" : "record", kind: "tile",
             hint: root.binding("toggle-recording"), enabled: ShellStore.streamRecordingActive || root.streaming },
        9: { title: qsTr("Save replay"), icon: "replay", kind: "tile", hint: root.binding("save-clip"),
             enabled: root.replayAvailable && root.streaming && !ShellStore.streamClipBusy },
        10: { title: qsTr("Anti-AFK"), icon: "pulse", kind: "switch", on: ShellStore.antiAfkEnabled,
              hint: root.binding("toggle-anti-afk"), enabled: true },
        11: { title: qsTr("Gallery"), icon: "image", kind: "page",
              value: ShellStore.mediaItems.length ? String(ShellStore.mediaItems.length) : "", enabled: true },
        12: { title: qsTr("Keyboard shortcuts"), icon: "keys", kind: "page", enabled: true },
        13: { title: qsTr("Open captures folder"), icon: "folder", kind: "row", enabled: ShellStore.mediaRootPath !== "" },
        14: { title: qsTr("Back"), icon: "back", kind: "row", enabled: true }
    })

    // Keyboard and controller order, one inner array per row. Tiles share a row.
    readonly property var captureRow: root.replayAvailable ? [7, 8, 9] : [7, 8]
    readonly property var toggleRows: ShellStore.microphoneToggleAvailable ? [6, 5, 3, 10] : [5, 3, 10]
    readonly property var moreRows: root.invitesAvailable ? [11, 12, 2, 1] : [11, 12, 2]
    readonly property var grid: page === "gallery" ? [[14], [13]]
        : page === "shortcuts" ? [[14]]
        : [root.captureRow].concat(root.toggleRows.map(id => [id]), root.moreRows.map(id => [id]), [[4]])

    function closesPanel(index) {
        return [0, 1, 2, 3, 4, 5, 7, 8, 9].indexOf(index) >= 0
    }
    function runAction(index) {
        if (closing) return
        const action = root.actions[index]
        if (action && !action.enabled) return
        if (index === 6) {
            ShellStore.toggleMicrophone()
            return
        }
        if (index === 10) {
            ShellStore.applyStreamShortcutAction("toggle-anti-afk")
            return
        }
        if (index === 11 || index === 12) {
            if (index === 11) ShellStore.refreshMedia()
            root.openPage(index === 11 ? "gallery" : "shortcuts")
            return
        }
        if (index === 13) {
            AppController.openLocalPath(ShellStore.mediaRootPath, false)
            return
        }
        if (index === 14) {
            root.openPage("")
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
        else if (action === 7) {
            // Capture only once the panel and its dimming are gone from the frame.
            resumeRequested()
            screenshotTimer.restart()
        }
        else if (action === 8) {
            resumeRequested()
            ShellStore.toggleStreamRecording()
        }
        else if (action === 9) {
            resumeRequested()
            ShellStore.saveStreamClip()
        }
    }
    function openPage(name) {
        root.page = name
        root.selectedIndex = name === "" ? (root.lastMainIndex >= 0 ? root.lastMainIndex : 7) : 14
        mainFlick.contentY = 0
    }
    property int lastMainIndex: -1
    function locate(index) {
        for (let row = 0; row < root.grid.length; ++row) {
            const column = root.grid[row].indexOf(index)
            if (column >= 0) return { row: row, column: column }
        }
        return { row: 0, column: 0 }
    }
    function moveSelection(rowStep, columnStep) {
        const here = root.locate(root.selectedIndex)
        if (columnStep !== 0) {
            const cells = root.grid[here.row]
            let column = here.column
            for (let i = 0; i < cells.length; ++i) {
                column = Math.max(0, Math.min(cells.length - 1, column + columnStep))
                if (root.actions[cells[column]].enabled) break
            }
            if (root.actions[cells[column]].enabled) root.selectedIndex = cells[column]
            return
        }
        let row = here.row
        for (let i = 0; i < root.grid.length; ++i) {
            row = Math.max(0, Math.min(root.grid.length - 1, row + rowStep))
            const cells = root.grid[row]
            const pick = cells[Math.min(here.column, cells.length - 1)]
            if (root.actions[pick].enabled) {
                root.selectedIndex = pick
                break
            }
            const any = cells.find(id => root.actions[id].enabled)
            if (any !== undefined) {
                root.selectedIndex = any
                break
            }
        }
    }
    onSelectedIndexChanged: {
        if (page === "") lastMainIndex = selectedIndex
        Qt.callLater(root.revealSelection)
    }
    function revealSelection() {
        const item = root.selectedItem
        if (!item || !item.visible) return
        const top = item.mapToItem(mainFlick.contentItem, 0, 0).y
        const margin = DesktopTokens.px(12)
        const maximumY = Math.max(0, mainFlick.contentHeight - mainFlick.height)
        if (top < mainFlick.contentY + margin)
            mainFlick.contentY = Math.max(0, top - margin)
        else if (top + item.height > mainFlick.contentY + mainFlick.height - margin)
            mainFlick.contentY = Math.min(maximumY, top + item.height + margin - mainFlick.height)
    }
    property Item selectedItem: null
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
    Timer {
        id: screenshotTimer
        interval: 160
        onTriggered: ShellStore.captureStreamScreenshot()
    }

    // Dim the game so the panel reads clearly; clicking outside resumes.
    Rectangle {
        anchors.fill: parent
        color: "#000000"
        opacity: 0.5 * reveal.progress
        TapHandler { onTapped: root.runAction(0) }
    }

    component SectionLabel: Text {
        color: Theme.textMuted
        font.family: Theme.bodyFont
        font.pixelSize: DesktopTokens.smallSize
        font.weight: Font.Bold
        font.letterSpacing: DesktopTokens.px(1.2)
        topPadding: DesktopTokens.px(20)
        bottomPadding: DesktopTokens.px(10)
        leftPadding: DesktopTokens.px(4)
    }

    // One list entry: icon, title, then a switch, a value or a page chevron.
    component ActionRow: Item {
        id: actionRow
        required property int actionId
        readonly property var action: root.actions[actionId]
        readonly property bool selected: root.selectedIndex === actionId
        width: parent ? parent.width : 0
        height: DesktopTokens.px(50)
        opacity: action.enabled ? 1 : 0.4
        objectName: "streamMenuAction" + actionId
        onSelectedChanged: if (selected) root.selectedItem = actionRow
        Accessible.role: action.kind === "switch" ? Accessible.CheckBox : Accessible.Button
        Accessible.name: action.title
        Accessible.checked: action.on === true
        Rectangle {
            anchors.fill: parent
            radius: DesktopTokens.radius
            color: actionRow.selected && actionRow.action.enabled ? Theme.surfaceHover : "transparent"
            border.width: actionRow.selected && actionRow.action.enabled && AppController.inputMode !== "mouse" ? DesktopTokens.px(2) : 0
            border.color: Theme.focus
        }
        DesktopSettingsIcon {
            x: DesktopTokens.px(14)
            anchors.verticalCenter: parent.verticalCenter
            width: DesktopTokens.px(20); height: width
            glyph: actionRow.action.icon
            ink: Theme.label
        }
        Text {
            x: DesktopTokens.px(48)
            width: trailing.x - x - DesktopTokens.px(12)
            anchors.verticalCenter: parent.verticalCenter
            text: actionRow.action.title
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            font.weight: actionRow.selected ? Font.DemiBold : Font.Medium
            elide: Text.ElideRight
        }
        Row {
            id: trailing
            anchors.right: parent.right
            anchors.rightMargin: DesktopTokens.px(14)
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(12)
            Text {
                anchors.verticalCenter: parent.verticalCenter
                visible: text !== ""
                text: actionRow.action.value || actionRow.action.hint || ""
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.smallSize
            }
            Rectangle {
                id: track
                visible: actionRow.action.kind === "switch"
                anchors.verticalCenter: parent.verticalCenter
                width: DesktopTokens.px(38); height: DesktopTokens.px(22)
                radius: height / 2
                color: actionRow.action.on ? Theme.focus : Theme.surfaceRaised
                border.width: actionRow.action.on ? 0 : 1
                border.color: Theme.seam
                Rectangle {
                    width: DesktopTokens.px(16); height: width; radius: width / 2
                    anchors.verticalCenter: parent.verticalCenter
                    x: actionRow.action.on ? parent.width - width - DesktopTokens.px(3) : DesktopTokens.px(3)
                    color: actionRow.action.on ? Theme.focusText : Theme.textMuted
                    Behavior on x { NumberAnimation { duration: AppController.reducedMotion ? 0 : DesktopTokens.quickDuration } }
                }
            }
            DesktopSettingsIcon {
                visible: actionRow.action.kind === "page"
                anchors.verticalCenter: parent.verticalCenter
                width: DesktopTokens.px(14); height: width
                glyph: "chevron"
                ink: Theme.textMuted
            }
        }
        HoverHandler { onHoveredChanged: if (hovered && actionRow.action.enabled) root.selectedIndex = actionRow.actionId }
        TapHandler { onTapped: root.runAction(actionRow.actionId) }
    }

    Rectangle {
        id: panel
        objectName: "streamMenuPanel"
        width: Math.min(DesktopTokens.px(420), root.width)
        height: root.height
        x: -width * (1 - reveal.progress)
        color: Theme.surface
        Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: Theme.seam }

        // Header: the game and the session clock, or the open page's title.
        Item {
            id: header
            x: DesktopTokens.px(24)
            y: DesktopTokens.px(28)
            width: parent.width - DesktopTokens.px(48)
            height: DesktopTokens.px(72)
            RoundedArtwork {
                id: boxArt
                visible: root.page === ""
                width: DesktopTokens.px(54)
                height: DesktopTokens.px(72)
                artwork: String(root.game.imageUrl || root.game.heroImageUrl || "")
                cornerRadius: DesktopTokens.radius
                scrimStart: 1
                fallbackColor: Theme.surfaceRaised
            }
            Column {
                x: root.page === "" ? DesktopTokens.px(70) : 0
                width: closeButton.x - x - DesktopTokens.px(12)
                anchors.verticalCenter: parent.verticalCenter
                spacing: DesktopTokens.px(4)
                Text {
                    width: parent.width
                    text: root.page === "gallery" ? qsTr("Gallery")
                        : root.page === "shortcuts" ? qsTr("Keyboard shortcuts")
                        : String(root.game.title || qsTr("GeForce NOW"))
                    color: Theme.label
                    font.family: Theme.displayFont
                    font.pixelSize: DesktopTokens.headingSize
                    font.weight: Font.Bold
                    elide: Text.ElideRight
                    maximumLineCount: 2
                    wrapMode: Text.WordWrap
                }
                Text {
                    width: parent.width
                    text: root.page !== "" ? String(root.game.title || "")
                        : [qsTr("Session %1").arg(root.clockText()), root.remainingText].filter(Boolean).join("  ·  ")
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                    font.features: { "tnum": 1 }
                    elide: Text.ElideRight
                }
            }
            Rectangle {
                id: closeButton
                objectName: "streamMenuClose"
                anchors.right: parent.right
                anchors.top: parent.top
                width: DesktopTokens.px(36); height: width
                radius: width / 2
                color: closeHover.hovered ? Theme.surfaceHover : "transparent"
                Accessible.role: Accessible.Button
                Accessible.name: qsTr("Resume game")
                DesktopSettingsIcon {
                    anchors.centerIn: parent
                    width: DesktopTokens.px(18); height: width
                    glyph: "close"
                    ink: Theme.textMuted
                }
                HoverHandler { id: closeHover }
                TapHandler { onTapped: root.runAction(0) }
            }
        }

        Flickable {
            id: mainFlick
            objectName: "streamMenuScroll"
            x: DesktopTokens.px(16)
            y: header.y + header.height + DesktopTokens.px(8)
            width: parent.width - DesktopTokens.px(32)
            height: footer.y - y - DesktopTokens.px(8)
            contentWidth: width
            contentHeight: root.page === "" ? mainColumn.implicitHeight
                : root.page === "gallery" ? galleryColumn.implicitHeight : shortcutsColumn.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            flickableDirection: Flickable.VerticalFlick

            // Main page.
            Column {
                id: mainColumn
                visible: root.page === ""
                width: parent.width
                SectionLabel { text: qsTr("CAPTURE") }
                Row {
                    id: tiles
                    width: parent.width
                    spacing: DesktopTokens.px(8)
                    Repeater {
                        model: root.captureRow
                        delegate: Item {
                            id: tile
                            required property int modelData
                            readonly property var action: root.actions[modelData]
                            readonly property bool selected: root.selectedIndex === modelData
                            objectName: "streamMenuAction" + modelData
                            width: Math.floor((tiles.width - tiles.spacing * (root.captureRow.length - 1)) / root.captureRow.length)
                            height: DesktopTokens.px(96)
                            opacity: action.enabled ? 1 : 0.4
                            onSelectedChanged: if (selected) root.selectedItem = tile
                            Accessible.role: Accessible.Button
                            Accessible.name: action.title
                            Rectangle {
                                anchors.fill: parent
                                radius: DesktopTokens.radiusLarge
                                color: tile.selected && tile.action.enabled ? Theme.surfaceHover : Theme.surfaceRaised
                                border.width: tile.selected && tile.action.enabled ? DesktopTokens.px(2) : 0
                                border.color: Theme.focus
                            }
                            DesktopSettingsIcon {
                                anchors.horizontalCenter: parent.horizontalCenter
                                y: DesktopTokens.px(18)
                                width: DesktopTokens.px(26); height: width
                                glyph: tile.action.icon
                                ink: tile.modelData === 8 && ShellStore.streamRecordingActive ? Theme.coral : Theme.label
                            }
                            Column {
                                anchors.horizontalCenter: parent.horizontalCenter
                                anchors.bottom: parent.bottom
                                anchors.bottomMargin: DesktopTokens.px(12)
                                width: parent.width - DesktopTokens.px(12)
                                spacing: DesktopTokens.px(2)
                                Text {
                                    width: parent.width
                                    horizontalAlignment: Text.AlignHCenter
                                    text: tile.action.title
                                    color: Theme.label
                                    font.family: Theme.bodyFont
                                    font.pixelSize: DesktopTokens.captionSize
                                    font.weight: Font.DemiBold
                                    elide: Text.ElideRight
                                }
                                Text {
                                    width: parent.width
                                    horizontalAlignment: Text.AlignHCenter
                                    visible: text !== ""
                                    text: tile.action.hint || ""
                                    color: Theme.textMuted
                                    font.family: Theme.bodyFont
                                    font.pixelSize: DesktopTokens.microSize
                                    elide: Text.ElideRight
                                }
                            }
                            HoverHandler { onHoveredChanged: if (hovered && tile.action.enabled) root.selectedIndex = tile.modelData }
                            TapHandler { onTapped: root.runAction(tile.modelData) }
                        }
                    }
                }
                Text {
                    width: parent.width
                    visible: !root.replayAvailable
                    topPadding: DesktopTokens.px(10)
                    leftPadding: DesktopTokens.px(4)
                    rightPadding: DesktopTokens.px(4)
                    text: qsTr("Instant replay is off. Turn on replay buffering in Settings, Recording, before your next session.")
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.smallSize
                    wrapMode: Text.WordWrap
                }
                SectionLabel { text: qsTr("QUICK SETTINGS") }
                Repeater {
                    model: root.toggleRows
                    delegate: ActionRow { required property int modelData; actionId: modelData }
                }
                SectionLabel { text: qsTr("MORE") }
                Repeater {
                    model: root.moreRows
                    delegate: ActionRow { required property int modelData; actionId: modelData }
                }
            }

            // Gallery page: the latest captures, newest first, and the folder they live in.
            Column {
                id: galleryColumn
                visible: root.page === "gallery"
                width: parent.width
                spacing: DesktopTokens.px(4)
                readonly property var recent: ShellStore.mediaItems.slice()
                    .sort((a, b) => Number(b.createdAtMs || 0) - Number(a.createdAtMs || 0)).slice(0, 6)
                ActionRow { actionId: 14 }
                ActionRow { actionId: 13 }
                Item { width: 1; height: DesktopTokens.px(8) }
                Text {
                    width: parent.width
                    visible: galleryColumn.recent.length === 0
                    leftPadding: DesktopTokens.px(4)
                    text: ShellStore.mediaState === "loading" ? qsTr("Loading captures…")
                        : qsTr("No captures yet. Screenshots, recordings and saved replays appear here.")
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                    wrapMode: Text.WordWrap
                }
                Grid {
                    columns: 2
                    spacing: DesktopTokens.px(8)
                    Repeater {
                        model: galleryColumn.recent
                        delegate: Column {
                            id: capture
                            required property var modelData
                            width: Math.floor((galleryColumn.width - DesktopTokens.px(8)) / 2)
                            spacing: DesktopTokens.px(6)
                            Rectangle {
                                width: parent.width
                                height: Math.round(width * 9 / 16)
                                radius: DesktopTokens.radius
                                color: Theme.surfaceRaised
                                clip: true
                                Image {
                                    anchors.fill: parent
                                    source: String(capture.modelData.thumbnailUrl || "")
                                    fillMode: Image.PreserveAspectCrop
                                    asynchronous: true
                                    sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
                                }
                                Rectangle {
                                    anchors.fill: parent
                                    radius: parent.radius
                                    color: "transparent"
                                    border.width: captureHover.hovered ? DesktopTokens.px(2) : 0
                                    border.color: Theme.focus
                                }
                                HoverHandler { id: captureHover; cursorShape: Qt.PointingHandCursor }
                                TapHandler {
                                    onTapped: if (capture.modelData.filePath)
                                        AppController.openLocalPath(String(capture.modelData.filePath), false)
                                }
                                DesktopSettingsIcon {
                                    visible: capture.modelData.kind !== "screenshot"
                                    anchors.right: parent.right; anchors.bottom: parent.bottom
                                    anchors.margins: DesktopTokens.px(6)
                                    width: DesktopTokens.px(16); height: width
                                    glyph: "play"
                                    ink: "#FFFFFF"
                                }
                            }
                            Text {
                                width: parent.width
                                text: String(capture.modelData.fileName || "")
                                color: Theme.textMuted
                                font.family: Theme.bodyFont
                                font.pixelSize: DesktopTokens.microSize
                                elide: Text.ElideMiddle
                            }
                        }
                    }
                }
            }

            // Shortcuts page: the bindings this session is using right now.
            Column {
                id: shortcutsColumn
                visible: root.page === "shortcuts"
                width: parent.width
                readonly property var rows: [
                    {label: qsTr("Open this panel"), key: root.binding("guide")},
                    {label: qsTr("Screenshot"), key: root.binding("screenshot")},
                    {label: qsTr("Start or stop recording"), key: root.binding("toggle-recording")},
                    {label: qsTr("Save instant replay"), key: root.binding("save-clip")},
                    {label: qsTr("Microphone"), key: root.binding("toggle-microphone")},
                    {label: qsTr("Statistics overlay"), key: root.statsShortcut},
                    {label: qsTr("Full screen"), key: root.binding("toggle-fullscreen")},
                    {label: qsTr("Lock the mouse to the game"), key: root.binding("toggle-pointer-lock")},
                    {label: qsTr("Anti-AFK"), key: root.binding("toggle-anti-afk")},
                    {label: qsTr("Exit game"), key: root.binding("stop-stream")}
                ].filter(row => row.key !== "")
                ActionRow { actionId: 14 }
                Item { width: 1; height: DesktopTokens.px(8) }
                Repeater {
                    model: shortcutsColumn.rows
                    delegate: Item {
                        id: shortcutRow
                        required property var modelData
                        width: shortcutsColumn.width
                        height: DesktopTokens.px(42)
                        Text {
                            x: DesktopTokens.px(14)
                            width: keyGlyph.x - x - DesktopTokens.px(12)
                            anchors.verticalCenter: parent.verticalCenter
                            text: shortcutRow.modelData.label
                            color: Theme.label
                            font.family: Theme.bodyFont
                            font.pixelSize: DesktopTokens.captionSize
                            elide: Text.ElideRight
                        }
                        KeyboardGlyph {
                            id: keyGlyph
                            anchors.right: parent.right
                            anchors.rightMargin: DesktopTokens.px(14)
                            anchors.verticalCenter: parent.verticalCenter
                            shortcut: shortcutRow.modelData.key
                            keySize: DesktopTokens.px(22)
                            ink: Theme.textMuted
                        }
                        Rectangle {
                            anchors.bottom: parent.bottom
                            x: DesktopTokens.px(14); width: parent.width - DesktopTokens.px(28); height: 1
                            color: DesktopTokens.seamSoft
                        }
                    }
                }
            }
        }

        // Fade the list's lower edge while more entries sit below it.
        Rectangle {
            x: mainFlick.x
            width: mainFlick.width
            height: DesktopTokens.px(40)
            y: mainFlick.y + mainFlick.height - height
            visible: mainFlick.contentY + mainFlick.height < mainFlick.contentHeight - 1
            gradient: Gradient {
                GradientStop { position: 0; color: Qt.alpha(Theme.surface, 0) }
                GradientStop { position: 1; color: Theme.surface }
            }
        }

        // Footer: live stream details, then Exit game.
        Column {
            id: footer
            x: DesktopTokens.px(16)
            y: parent.height - height - DesktopTokens.px(24)
            width: parent.width - DesktopTokens.px(32)
            spacing: DesktopTokens.px(12)
            Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
            Column {
                objectName: "streamMenuDetails"
                x: DesktopTokens.px(4)
                width: parent.width - DesktopTokens.px(8)
                spacing: DesktopTokens.px(4)
                Text {
                    width: parent.width
                    text: [root.resolutionText, root.fpsText, root.bitrateText].join("  ·  ")
                    color: Theme.label
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                    font.features: { "tnum": 1 }
                    elide: Text.ElideRight
                }
                Text {
                    width: parent.width
                    text: qsTr("%1 video, %2 decoder").arg(root.codecText).arg(root.decoderText)
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.smallSize
                    elide: Text.ElideRight
                }
            }
            Item {
                id: exitRow
                objectName: "streamMenuAction4"
                readonly property bool selected: root.selectedIndex === 4
                width: parent.width
                height: DesktopTokens.px(50)
                onSelectedChanged: if (selected) root.selectedItem = null
                Accessible.role: Accessible.Button
                Accessible.name: qsTr("Exit game")
                Rectangle {
                    anchors.fill: parent
                    radius: DesktopTokens.radius
                    color: exitRow.selected ? Qt.alpha(Theme.coral, 0.16) : "transparent"
                    border.width: exitRow.selected ? DesktopTokens.px(2) : 1
                    border.color: exitRow.selected ? Theme.coral : Theme.seam
                }
                Row {
                    anchors.centerIn: parent
                    spacing: DesktopTokens.px(10)
                    DesktopSettingsIcon {
                        anchors.verticalCenter: parent.verticalCenter
                        width: DesktopTokens.px(20); height: width
                        glyph: "exit"
                        ink: Theme.coral
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: qsTr("Exit game")
                        color: Theme.coral
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.bodySize
                        font.weight: Font.DemiBold
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: root.binding("stop-stream")
                        color: Theme.textMuted
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.smallSize
                    }
                }
                HoverHandler { onHoveredChanged: if (hovered) root.selectedIndex = 4 }
                TapHandler { onTapped: root.runAction(4) }
            }
        }
    }

    Timer { interval: 1000; repeat: true; running: root.visible; onTriggered: root.nowMs = Date.now() }
    onOpenedChanged: if (opened) {
        closing = false
        pendingAction = -1
        page = ""
        lastMainIndex = -1
        selectedIndex = 7
        mainFlick.contentY = 0
        forceActiveFocus()
    }
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            if (root.page !== "") root.openPage("")
            else root.runAction(0)
            event.accepted = true
        } else if (event.key === Qt.Key_Backspace && root.page !== "") {
            root.openPage("")
            event.accepted = true
        } else if (event.key === Qt.Key_Down) {
            root.moveSelection(1, 0)
            event.accepted = true
        } else if (event.key === Qt.Key_Up) {
            root.moveSelection(-1, 0)
            event.accepted = true
        } else if (event.key === Qt.Key_Right) {
            root.moveSelection(0, 1)
            event.accepted = true
        } else if (event.key === Qt.Key_Left) {
            root.moveSelection(0, -1)
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
