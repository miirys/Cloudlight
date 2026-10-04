import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    property bool opened: false
    property bool quittingApplication: false
    readonly property bool present: reveal.present
    visible: present
    enabled: opened
    focus: opened
    MotionProgress { id: reveal; shown: root.opened }
    Accessible.role: Accessible.Dialog
    Accessible.name: root.quittingApplication ? qsTr("Quit Cloudlight confirmation") : qsTr("End cloud session confirmation")

    signal cancelRequested()
    signal confirmRequested()

    Shortcut {
        sequences: ["Return", "Enter"]
        enabled: root.opened
        context: Qt.WindowShortcut
        autoRepeat: false
        onActivated: root.confirmRequested()
    }

    Rectangle {
        anchors.fill: parent
        color: "#A6000000"
        opacity: reveal.progress
        TapHandler { onTapped: root.cancelRequested() }
    }

    Rectangle {
        id: card
        opacity: reveal.progress
        scale: reveal.zoom
        anchors.centerIn: parent
        width: Math.min(DesktopTokens.px(600), root.width - DesktopTokens.px(48))
        height: dialogColumn.implicitHeight + DesktopTokens.px(80)
        radius: DesktopTokens.radiusLarge
        color: Theme.surface

        Column {
            id: dialogColumn
            x: DesktopTokens.px(40); y: DesktopTokens.px(40)
            width: parent.width - DesktopTokens.px(80)
            spacing: 0

            Text {
                width: parent.width
                text: root.quittingApplication ? qsTr("Quit Cloudlight?") : qsTr("End this cloud session?")
                color: DesktopTokens.text
                font.family: DesktopTokens.displayFont
                font.pixelSize: DesktopTokens.titleSize
                font.weight: Font.Bold
            }
            Text {
                width: parent.width
                topPadding: DesktopTokens.px(14)
                text: root.quittingApplication
                    ? qsTr("Cloudlight will close and disconnect from any active cloud session.")
                    : AppController.route === "inserting"
                    ? qsTr("Your session request will be cancelled and you will leave the queue.")
                    : qsTr("Your game will close on the remote rig. This session cannot be resumed after it ends.")
                color: DesktopTokens.textBody
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.bodySize
                wrapMode: Text.WordWrap
                lineHeight: 1.25
            }

            Item { width: 1; height: DesktopTokens.px(36) }
            Row {
                anchors.right: parent.right
                spacing: DesktopTokens.px(12)
                DesktopButton {
                    id: keepPlayingButton
                    objectName: root.quittingApplication ? "quitConfirmKeepOpen" : "streamExitKeepPlaying"
                    text: root.quittingApplication ? qsTr("Keep Cloudlight open")
                        : AppController.route === "inserting" ? qsTr("Keep waiting") : qsTr("Keep playing")
                    shortcutText: qsTr("Esc")
                    primary: true
                    onClicked: root.cancelRequested()
                    KeyNavigation.right: endButton
                }
                DesktopButton {
                    id: endButton
                    objectName: root.quittingApplication ? "quitConfirmQuit" : "streamExitEndSession"
                    text: root.quittingApplication ? qsTr("Quit Cloudlight") : qsTr("End session")
                    shortcutText: qsTr("Enter")
                    danger: true
                    onClicked: root.confirmRequested()
                    KeyNavigation.left: keepPlayingButton
                }
            }
        }
    }

    onOpenedChanged: if (opened) Qt.callLater(() => { if (root.opened) keepPlayingButton.forceActiveFocus() })
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.cancelRequested()
            event.accepted = true
        }
    }
}
