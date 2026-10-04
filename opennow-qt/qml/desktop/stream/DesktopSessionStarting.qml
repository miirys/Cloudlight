pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopSessionStarting"
    width: 1440
    height: 900
    Accessible.role: Accessible.Pane
    Accessible.name: qsTr("Starting session")

    signal cancelRequested()
    signal retryRequested()

    readonly property var game: ShellStore.selectedGame || ({})
    readonly property var session: ShellStore.activeSession || ({})
    readonly property var streamer: ShellStore.streamer || ({})
    readonly property bool connecting: AppController.route === "stream"
    readonly property string phase: String(ShellStore.streamState || "preparing")
    readonly property bool stopping: phase === "stopping"
    readonly property bool failed: !stopping && (phase === "failed" || phase === "error"
        || (connecting && (streamer.status === "error" || streamer.status === "stopped")))
    readonly property bool reconnecting: phase === "reconnecting"
        || ShellStore.streamerRestartAttempts > 0 || ShellStore.sessionReconnectAttempts > 0
    SessionSetupProgress {
        id: setupProgress
        session: root.session
    }
    readonly property string statusText: {
        if (stopping) return qsTr("Closing your session")
        if (failed) return ShellStore.launchConflictDetected
            ? qsTr("Your game is still running") : qsTr("Session could not start")
        if (reconnecting) return qsTr("Reconnecting to your session")
        if (connecting) return qsTr("Connecting to your game")
        if (setupProgress.queued) return setupProgress.title
        if (phase === "checking") return qsTr("Checking session availability")
        if (phase === "requesting") return qsTr("Requesting your session")
        if (phase === "resuming") return qsTr("Reconnecting to your game")
        return setupProgress.title
    }
    readonly property string detailText: {
        if (stopping) return qsTr("Waiting for your session to close.")
        if (failed) return String((connecting && streamer.message) || ShellStore.streamMessage
            || qsTr("Please try again or return to your library."))
        if (reconnecting) return qsTr("Your stream will return when the connection is restored.")
        if (phase === "resuming" || phase === "checking" || phase === "conflict")
            return ShellStore.streamMessage
        if (connecting) {
            if (streamer.status === "starting") return qsTr("Initializing the native streaming connection.")
            if (streamer.status === "streaming") return qsTr("Connected. Waiting for the first video frame.")
            return qsTr("Your game will appear here as soon as the video is ready.")
        }
        return setupProgress.detail
    }

    function restoreFocus() {
        if (root.visible && root.enabled
                && !ShellStore.streamOverlayBlocksGameplayInput(AppController.overlay))
            cancelButton.forceActiveFocus()
    }

    ArtworkSource {
        id: artwork
        sourceUrl: DesktopTokens.decodeArtworkUrl(String(root.game.heroImageUrl || root.game.imageUrl || ""))
        active: root.visible
    }

    // GeForce NOW style launch screen: the game's key art fills the screen
    // behind a left and bottom scrim, with the title block on the safe margin.
    Rectangle { anchors.fill: parent; color: "#04060A" }
    Image {
        anchors.fill: parent
        source: artwork.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
        opacity: status === Image.Ready ? 0.7 : 0
        scale: status === Image.Ready && !AppController.reducedMotion ? 1 : 1.04
        Behavior on opacity { NumberAnimation { duration: 700; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: 1400; easing.type: Easing.OutCubic } }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#F00A0A0A" }
            GradientStop { position: 0.45; color: "#A00A0A0A" }
            GradientStop { position: 1; color: "#300A0A0A" }
        }
    }
    Rectangle {
        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
        height: parent.height * 0.55
        gradient: Gradient {
            GradientStop { position: 0; color: "#000A0A0A" }
            GradientStop { position: 1; color: "#F20A0A0A" }
        }
    }

    DesktopBrandLockup {
        x: DesktopTokens.safeX
        y: DesktopTokens.px(32)
        markHeight: DesktopTokens.px(14)
        fontPixelSize: DesktopTokens.navSize
        spacing: DesktopTokens.px(10)
        ink: Theme.mediaForeground
        onMedia: true
    }

    Column {
        x: DesktopTokens.safeX
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(72)
        width: Math.min(DesktopTokens.px(720), root.width - DesktopTokens.safeX * 2)
        spacing: 0

        Text {
            text: root.stopping ? qsTr("ENDING SESSION")
                : root.failed ? qsTr("SESSION INTERRUPTED") : qsTr("STARTING SESSION")
            color: root.failed ? DesktopTokens.danger : Theme.mediaAccent
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            font.weight: Font.Bold
            font.letterSpacing: DesktopTokens.px(2)
        }
        Text {
            width: parent.width
            topPadding: DesktopTokens.px(10)
            text: String(root.game.title || qsTr("GeForce NOW"))
            color: Theme.mediaForeground
            font.family: DesktopTokens.displayFont
            font.pixelSize: root.width < 800 ? DesktopTokens.titleSize : DesktopTokens.displaySize
            font.weight: Font.Bold
            font.letterSpacing: -DesktopTokens.px(0.5)
            maximumLineCount: 2
            wrapMode: Text.WordWrap
            elide: Text.ElideRight
            lineHeight: 1.05
        }
        Item { width: 1; height: DesktopTokens.px(28) }
        Text {
            objectName: "sessionLaunchStatus"
            width: parent.width
            text: root.statusText
            color: root.failed ? DesktopTokens.danger : Theme.mediaForeground
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            font.weight: Font.DemiBold
            wrapMode: Text.WordWrap
        }
        // Indeterminate progress: a short accent bar sweeping a thin track.
        Item {
            width: Math.min(parent.width, DesktopTokens.px(480))
            height: visible ? DesktopTokens.px(16) + track.height : 0
            visible: !root.failed
            Rectangle {
                id: track
                y: DesktopTokens.px(16)
                width: parent.width
                height: DesktopTokens.px(4)
                radius: height / 2
                color: "#33FFFFFF"
                clip: true
                Rectangle {
                    id: sweep
                    width: parent.width * 0.3
                    height: parent.height
                    radius: height / 2
                    color: DesktopTokens.focus
                    x: AppController.reducedMotion ? 0 : -width
                    NumberAnimation on x {
                        running: root.visible && !root.failed && !AppController.reducedMotion
                        loops: Animation.Infinite
                        from: -sweep.width
                        to: track.width
                        duration: 1400
                        easing.type: Easing.InOutCubic
                    }
                }
            }
        }
        Text {
            width: parent.width
            topPadding: DesktopTokens.px(14)
            text: root.detailText
            visible: text !== ""
            color: Theme.mediaMuted
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            lineHeight: 1.35
            wrapMode: Text.WordWrap
            maximumLineCount: 5
            elide: Text.ElideRight
        }
        Item { width: 1; height: DesktopTokens.px(32) }
        Flow {
            width: parent.width
            spacing: DesktopTokens.px(12)
            DesktopButton {
                onMediaBackground: true
                visible: root.failed && (root.connecting || ShellStore.activeSession !== null
                    || ShellStore.pendingLaunchParams !== null || ShellStore.conflictSession !== null)
                enabled: !ShellStore.streamBusy
                text: root.connecting ? qsTr("Retry connection") : qsTr("Try again")
                primary: true
                onClicked: root.retryRequested()
            }
            DesktopButton {
                id: cancelButton
                onMediaBackground: true
                enabled: !root.stopping
                text: root.failed && !ShellStore.activeSession ? qsTr("Back") : qsTr("Cancel session")
                shortcutText: qsTr("Esc")
                onClicked: root.cancelRequested()
            }
        }
    }

    onVisibleChanged: if (visible) Qt.callLater(root.restoreFocus)
    onEnabledChanged: if (enabled) Qt.callLater(root.restoreFocus)
    Connections {
        target: AppController
        function onOverlayChanged() { Qt.callLater(root.restoreFocus) }
    }
    Keys.onPressed: event => {
        if (event.isAutoRepeat || root.stopping)
            return
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.cancelRequested()
            event.accepted = true
        }
    }
}
