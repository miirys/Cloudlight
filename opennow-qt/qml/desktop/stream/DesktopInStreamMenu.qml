pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Window
import OpenNOW

// In-game overlay (Ctrl+G, Guide, or holding Start), laid out like GeForce NOW's side
// panel: a flat charcoal column docked to the left over a dimmed game, a lighter header,
// and sub-pages that push in from the right. Every entry drives a real stream action or
// a persisted setting; pages live in desktop/stream/overlay/.
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
    // Statistics shown once the panel closes: "off", "compact" or "expanded".
    property string statsMode: "off"
    signal statsModeRequested(string mode)

    // Actions that close the panel first (ids kept stable for callers and tests):
    // 0 resume, 1 invite, 2 console mode, 3 fullscreen, 4 exit game, 7 screenshot,
    // 8 recording, 9 save instant replay.
    property int pendingAction: -1
    property bool closing: false
    property double nowMs: Date.now()
    readonly property bool modeOn: DesktopTokens.consoleModeOn(Window.window)
    readonly property bool fullscreen: Window.window
        && Window.window.visibility === Window.FullScreen
    readonly property string statsShortcut: String(ShellStore.settings.shortcutToggleStats ?? "Ctrl+N")

    function runAction(index) {
        if (closing) return
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

    readonly property var pages: ({
        gallery: galleryPage, filters: filtersPage, settings: settingsPage,
        general: generalPage, gameplay: gameplayPage, system: systemPage,
        shortcuts: shortcutsPage, hud: hudPage, notifications: notificationsPage,
        capture: capturePage, files: filesPage
    })
    readonly property Item currentPage: stack.currentItem
    readonly property string currentPageName: currentPage ? String(currentPage.pageName || "") : ""

    function openPage(name) {
        const component = root.pages[name]
        if (!component || stack.busy) return
        if (name === "gallery") ShellStore.refreshMedia()
        stack.push(component, {menu: root})
    }
    function back() {
        if (stack.depth > 1 && !stack.busy) stack.pop()
        else if (stack.depth <= 1) root.runAction(0)
    }
    function focusPage() {
        if (root.currentPage && root.currentPage.firstFocus) root.currentPage.firstFocus()
    }

    MotionProgress {
        id: reveal
        objectName: "streamMenuMotion"
        shown: root.opened && !root.closing
        enterDuration: OverlayStyle.panelDuration
        exitDuration: 160
        onHidden: if (root.closing && root.pendingAction >= 0) root.finishAction()
    }
    Timer {
        id: screenshotTimer
        interval: 160
        onTriggered: ShellStore.captureStreamScreenshot()
    }
    Timer { interval: 1000; repeat: true; running: root.visible; onTriggered: root.nowMs = Date.now() }

    // Dim the game; clicking it resumes.
    Rectangle {
        anchors.fill: parent
        color: "#000000"
        opacity: 0.45 * reveal.progress
        TapHandler { onTapped: root.runAction(0) }
    }

    Rectangle {
        id: panel
        objectName: "streamMenuPanel"
        width: Math.min(OverlayStyle.panelWidth, root.width)
        height: root.height
        x: -width * (1 - reveal.progress)
        color: OverlayStyle.body
        clip: true
        // Swallow clicks so they never reach the dim layer behind the panel.
        TapHandler {}

        OverlayHeader {
            id: header
            z: 1
            width: parent.width
            title: root.currentPage ? root.currentPage.title : ""
            showBack: stack.depth > 1
            showActions: stack.depth <= 1
            onBackRequested: root.back()
            onCloseRequested: root.runAction(0)
            onSettingsRequested: root.openPage("settings")
            onFeedbackRequested: AppController.openExternalUrl("https://github.com/miirys/OpenNOW/issues")
        }

        StackView {
            id: stack
            objectName: "overlayStack"
            y: header.height
            width: parent.width
            height: parent.height - header.height
            focus: true
            initialItem: mainPage
            onCurrentItemChanged: Qt.callLater(root.focusPage)

            readonly property int duration: OverlayStyle.pageDuration
            readonly property real shift: width * 0.3
            pushEnter: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "x"; from: stack.shift; to: 0; duration: stack.duration; easing.type: Easing.OutCubic }
                    NumberAnimation { property: "opacity"; from: 0; to: 1; duration: stack.duration; easing.type: Easing.OutCubic }
                }
            }
            pushExit: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "x"; from: 0; to: -stack.shift; duration: stack.duration; easing.type: Easing.OutCubic }
                    NumberAnimation { property: "opacity"; from: 1; to: 0; duration: stack.duration; easing.type: Easing.OutCubic }
                }
            }
            popEnter: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "x"; from: -stack.shift; to: 0; duration: stack.duration; easing.type: Easing.OutCubic }
                    NumberAnimation { property: "opacity"; from: 0; to: 1; duration: stack.duration; easing.type: Easing.OutCubic }
                }
            }
            popExit: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "x"; from: 0; to: stack.shift; duration: stack.duration; easing.type: Easing.OutCubic }
                    NumberAnimation { property: "opacity"; from: 1; to: 0; duration: stack.duration; easing.type: Easing.OutCubic }
                }
            }
            replaceEnter: pushEnter
            replaceExit: pushExit
        }
    }

    Component { id: mainPage; OverlayMainPage { menu: root } }
    Component { id: galleryPage; OverlayGalleryPage {} }
    Component { id: filtersPage; OverlayFiltersPage {} }
    Component { id: settingsPage; OverlaySettingsPage {} }
    Component { id: generalPage; OverlayGeneralPage {} }
    Component { id: gameplayPage; OverlayGameplayPage {} }
    Component { id: systemPage; OverlaySystemPage {} }
    Component { id: shortcutsPage; OverlayShortcutsPage {} }
    Component { id: hudPage; OverlayHudPage {} }
    Component { id: notificationsPage; OverlayNotificationsPage {} }
    Component { id: capturePage; OverlayCapturePage {} }
    Component { id: filesPage; OverlayFilesPage {} }

    onOpenedChanged: if (opened) {
        closing = false
        pendingAction = -1
        if (stack.depth > 1) stack.pop(null, StackView.Immediate)
        forceActiveFocus()
        Qt.callLater(root.focusPage)
    }

    // Keys that rows did not consume: back, close and the exit shortcut.
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back || event.key === Qt.Key_Backspace) {
            root.back()
            event.accepted = true
        } else if (event.key === Qt.Key_Menu) {
            // Start on a controller closes the overlay, as on GeForce NOW.
            root.runAction(0)
            event.accepted = true
        } else if (event.key === Qt.Key_Down || event.key === Qt.Key_Up) {
            root.focusPage()
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
