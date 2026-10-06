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
        if (!component) return
        if (name === "gallery") ShellStore.refreshMedia()
        stack.push(component, true)
    }
    function back() {
        if (stack.depth > 1) stack.pop(true)
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

    // Dim the game; clicking it resumes. Pointer handlers only take passive grabs,
    // so a tap on a panel row also reached this layer and closed the whole menu as
    // the sub-page opened. Only taps beside the panel close it.
    Rectangle {
        anchors.fill: parent
        color: "#000000"
        opacity: 0.45 * reveal.progress
        TapHandler {
            onTapped: eventPoint => {
                if (eventPoint.position.x >= panel.x + panel.width) root.runAction(0)
            }
        }
    }

    Rectangle {
        id: panel
        objectName: "streamMenuPanel"
        width: Math.min(OverlayStyle.panelWidth, root.width)
        height: root.height
        x: -width * Math.max(0, 1 - reveal.progress)
        color: OverlayStyle.body
        clip: true
        // Accept every press that rows leave, so it never reaches the dim layer.
        MouseArea {
            z: -1
            anchors.fill: parent
            acceptedButtons: Qt.AllButtons
            onWheel: wheel => wheel.accepted = false
        }

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
        }

        // A small page stack of our own: pages are created on push and
        // destroyed after they leave, and a new push or pop finishes any motion
        // still running instead of being ignored (StackView dropped pushes
        // while "busy", which left sub-pages unopenable).
        Item {
            id: stack
            objectName: "overlayStack"
            y: header.height
            width: parent.width
            height: parent.height - header.height
            clip: true
            focus: true
            property var items: []
            readonly property int depth: items.length
            readonly property Item currentItem: items.length > 0 ? items[items.length - 1] : null
            readonly property bool busy: motion.running
            onCurrentItemChanged: Qt.callLater(root.focusPage)

            function create(component) {
                const item = component.createObject(stack, {menu: root})
                if (!item) {
                    console.warn("Could not open overlay page:", component.errorString())
                    return null
                }
                item.width = Qt.binding(() => stack.width)
                item.height = Qt.binding(() => stack.height)
                // Pages are transparent; give each an opaque backing so the page
                // underneath never shows through while one slides over another.
                pageBacking.createObject(item)
                return item
            }
            function settle() {
                if (motion.running) {
                    motion.complete()
                    motion.cleanup()
                }
            }
            function push(component, animated) {
                settle()
                const incoming = create(component)
                if (!incoming) return
                const outgoing = currentItem
                items = items.concat([incoming])
                if (!outgoing || !animated || AppController.reducedMotion) {
                    incoming.x = 0
                    if (outgoing) outgoing.visible = false
                    return
                }
                motion.begin(incoming, outgoing, true)
            }
            function pop(animated) {
                settle()
                if (items.length <= 1) return
                const outgoing = currentItem
                const remaining = items.slice(0, items.length - 1)
                const incoming = remaining[remaining.length - 1]
                items = remaining
                incoming.visible = true
                if (!animated || AppController.reducedMotion) {
                    incoming.x = 0
                    incoming.opacity = 1
                    outgoing.destroy()
                    return
                }
                motion.begin(incoming, outgoing, false)
            }
            function popToRoot() {
                settle()
                while (items.length > 1) {
                    const top = items[items.length - 1]
                    items = items.slice(0, items.length - 1)
                    top.destroy()
                }
                if (currentItem) {
                    currentItem.visible = true
                    currentItem.x = 0
                    currentItem.opacity = 1
                }
            }
            Component.onCompleted: {
                const first = create(mainPage)
                if (first) items = [first]
            }

            // iOS-style navigation: the new page travels in from the right edge on
            // a spring while the old one drifts a quarter width and dims; pop is
            // the mirror image.
            ParallelAnimation {
                id: motion
                property Item incoming: null
                property Item outgoing: null
                property bool forward: true
                function begin(incomingItem, outgoingItem, isForward) {
                    incoming = incomingItem
                    outgoing = outgoingItem
                    forward = isForward
                    incomingX.from = isForward ? stack.width : -stack.width * 0.25
                    outgoingX.to = isForward ? -stack.width * 0.25 : stack.width
                    incomingFade.from = isForward ? 1 : 0.4
                    outgoingFade.to = isForward ? 0.4 : 1
                    incomingItem.z = isForward ? 2 : 1
                    outgoingItem.z = isForward ? 1 : 2
                    // Place the incoming page before the first frame so it never
                    // flashes in at its resting position.
                    incomingItem.x = incomingX.from
                    incomingItem.opacity = incomingFade.from
                    start()
                }
                NumberAnimation { id: incomingX; target: motion.incoming; property: "x"; to: 0
                    duration: OverlayStyle.pageDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.springSoft }
                NumberAnimation { id: incomingFade; target: motion.incoming; property: "opacity"; to: 1
                    duration: OverlayStyle.pageDuration; easing.type: Easing.OutCubic }
                NumberAnimation { id: outgoingX; target: motion.outgoing; property: "x"; from: 0
                    duration: OverlayStyle.pageDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.springSoft }
                NumberAnimation { id: outgoingFade; target: motion.outgoing; property: "opacity"; from: 1
                    duration: OverlayStyle.pageDuration; easing.type: Easing.OutCubic }
                onFinished: cleanup()
                function cleanup() {
                    if (!outgoing) return
                    if (forward) {
                        outgoing.visible = false
                        outgoing.x = 0
                        outgoing.opacity = 1
                    } else {
                        outgoing.destroy()
                    }
                    outgoing = null
                    incoming = null
                }
            }
        }
    }

    Component { id: pageBacking; Rectangle { z: -1; anchors.fill: parent; color: OverlayStyle.body } }
    Component { id: mainPage; OverlayMainPage { menu: root } }
    Component { id: galleryPage; OverlayGalleryPage { menu: root } }
    Component { id: filtersPage; OverlayFiltersPage { menu: root } }
    Component { id: settingsPage; OverlaySettingsPage { menu: root } }
    Component { id: generalPage; OverlayGeneralPage { menu: root } }
    Component { id: gameplayPage; OverlayGameplayPage { menu: root } }
    Component { id: systemPage; OverlaySystemPage { menu: root } }
    Component { id: shortcutsPage; OverlayShortcutsPage { menu: root } }
    Component { id: hudPage; OverlayHudPage { menu: root } }
    Component { id: notificationsPage; OverlayNotificationsPage { menu: root } }
    Component { id: capturePage; OverlayCapturePage { menu: root } }
    Component { id: filesPage; OverlayFilesPage { menu: root } }

    onOpenedChanged: if (opened) {
        closing = false
        pendingAction = -1
        stack.popToRoot()
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
