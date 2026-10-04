pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Window
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

    // Where the launch is, as one of four plain stages. Steps 5 and 6 (cleanup, storage)
    // are waits, so they read as the queue stage.
    readonly property var stages: [qsTr("Checking"), qsTr("In queue"), qsTr("Setting up your rig"), qsTr("Connecting")]
    readonly property int stage: {
        if (connecting) return 3
        const step = setupProgress.setupStep
        if (step >= 2 && step <= 4) return 2
        if (setupProgress.queued || step === 1 || step === 5 || step === 6) return 1
        return 0
    }
    readonly property bool showQueueNumber: setupProgress.queued && setupProgress.queuePosition > 0
        && !failed && !stopping && width >= DesktopTokens.px(1000)

    // The game's key art fills the screen behind a left and bottom scrim. The launch
    // details sit on the safe margin, bottom left; the queue number and the mascot
    // slot sit bottom right.
    Rectangle { anchors.fill: parent; color: "#0B0A0E" }
    Image {
        anchors.fill: parent
        source: artwork.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
        sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
        opacity: status === Image.Ready ? 0.72 : 0
        scale: status === Image.Ready && !AppController.reducedMotion ? 1 : 1.04
        Behavior on opacity { NumberAnimation { duration: 700; easing.type: Easing.OutCubic } }
        Behavior on scale { NumberAnimation { duration: 1400; easing.type: Easing.OutCubic } }
    }
    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: "#F20B0A0E" }
            GradientStop { position: 0.5; color: "#990B0A0E" }
            GradientStop { position: 1; color: "#400B0A0E" }
        }
    }
    Rectangle {
        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
        height: parent.height * 0.6
        gradient: Gradient {
            GradientStop { position: 0; color: "#000B0A0E" }
            GradientStop { position: 1; color: "#F50B0A0E" }
        }
    }

    DesktopBrandLockup {
        x: DesktopTokens.safeX
        y: DesktopTokens.px(32)
        markHeight: DesktopTokens.px(22)
        fontPixelSize: DesktopTokens.navSize
        spacing: DesktopTokens.px(10)
        ink: Theme.mediaForeground
        onMedia: true
    }

    Column {
        id: details
        x: DesktopTokens.safeX
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(72)
        width: Math.min(DesktopTokens.px(760), root.width - DesktopTokens.safeX * 2)
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
            font.pixelSize: root.width < 800 ? DesktopTokens.titleSize : DesktopTokens.px(64)
            font.weight: Font.Bold
            font.letterSpacing: -DesktopTokens.px(0.5)
            maximumLineCount: 2
            wrapMode: Text.WordWrap
            elide: Text.ElideRight
            lineHeight: 1.0
        }
        Item { width: 1; height: DesktopTokens.px(36) }
        // Four stages, each a segment of one bar: done segments are filled, the
        // current one carries a short sweep, later ones are bare track.
        Row {
            id: stageRail
            objectName: "sessionLaunchStages"
            visible: !root.failed && !root.stopping
            width: Math.min(parent.width, DesktopTokens.px(680))
            height: visible ? implicitHeight + DesktopTokens.px(22) : 0
            spacing: DesktopTokens.px(8)
            Repeater {
                model: root.stages
                delegate: Column {
                    id: stageItem
                    required property string modelData
                    required property int index
                    readonly property bool done: index < root.stage
                    readonly property bool current: index === root.stage
                    width: Math.floor((stageRail.width - stageRail.spacing * 3) / 4)
                    spacing: DesktopTokens.px(10)
                    Rectangle {
                        id: segment
                        width: parent.width
                        height: DesktopTokens.px(4)
                        radius: height / 2
                        color: "#33FFFFFF"
                        clip: true
                        Rectangle {
                            anchors.fill: parent
                            radius: parent.radius
                            color: Theme.mediaAccent
                            visible: stageItem.done
                        }
                        Rectangle {
                            id: stageSweep
                            visible: stageItem.current
                            width: AppController.reducedMotion ? parent.width / 2 : parent.width * 0.45
                            height: parent.height
                            radius: parent.radius
                            color: Theme.mediaAccent
                            x: 0
                            NumberAnimation on x {
                                running: stageItem.current && root.visible && !root.failed && !AppController.reducedMotion
                                loops: Animation.Infinite
                                from: -stageSweep.width
                                to: segment.width
                                duration: 1500
                                easing.type: Easing.InOutCubic
                            }
                        }
                    }
                    Text {
                        width: parent.width
                        text: stageItem.modelData
                        color: stageItem.current ? Theme.mediaForeground : Theme.mediaMuted
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                        font.weight: stageItem.current ? Font.Bold : Font.Normal
                        elide: Text.ElideRight
                    }
                }
            }
        }
        Text {
            objectName: "sessionLaunchStatus"
            width: parent.width
            text: root.statusText
            color: root.failed ? DesktopTokens.danger : Theme.mediaForeground
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.headingSize
            font.weight: Font.DemiBold
            wrapMode: Text.WordWrap
        }
        Text {
            width: parent.width
            topPadding: DesktopTokens.px(8)
            text: root.detailText
            visible: text !== ""
            color: Theme.mediaMuted
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            lineHeight: 1.3
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

    // Bottom right: the mascot (only once its art ships) above the queue number.
    Column {
        anchors.right: parent.right
        anchors.rightMargin: DesktopTokens.safeX
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(72)
        spacing: DesktopTokens.px(12)
        CloudlightMascot {
            id: mascot
            anchors.right: parent.right
            pose: root.failed ? "error" : "loading"
            visible: hasArt && root.width >= DesktopTokens.px(1000)
            width: DesktopTokens.px(300)
            height: DesktopTokens.px(300)
        }
        Column {
            objectName: "sessionQueueNumber"
            anchors.right: parent.right
            visible: root.showQueueNumber
            spacing: 0
            Text {
                anchors.right: parent.right
                text: qsTr("IN QUEUE")
                color: Theme.mediaMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                font.weight: Font.Bold
                font.letterSpacing: DesktopTokens.px(2)
            }
            Text {
                anchors.right: parent.right
                text: Number(setupProgress.queuePosition).toLocaleString(Qt.locale(), "f", 0)
                color: Theme.mediaForeground
                font.family: Theme.brandFont
                font.pixelSize: DesktopTokens.px(168)
                font.weight: Font.Medium
                font.features: { "lnum": 1, "tnum": 1 }
                lineHeight: 0.85
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
