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

    // A compact GeForce NOW-style modal: plain dark card, left-aligned copy, the safe
    // choice and the ending choice side by side on the right, and the keyboard keys as
    // a quiet hint instead of chips inside the buttons.
    Rectangle {
        id: card
        objectName: "streamExitCard"
        opacity: reveal.progress
        scale: 0.96 + 0.04 * reveal.progress
        anchors.centerIn: parent
        width: Math.min(DesktopTokens.px(480), root.width - DesktopTokens.px(48))
        height: dialogColumn.implicitHeight + DesktopTokens.px(56)
        radius: DesktopTokens.px(16)
        color: Theme.surface
        MouseArea { z: -1; anchors.fill: parent; acceptedButtons: Qt.AllButtons }

        Column {
            id: dialogColumn
            x: DesktopTokens.px(28); y: DesktopTokens.px(28)
            width: parent.width - DesktopTokens.px(56)
            spacing: 0

            Text {
                width: parent.width
                text: root.quittingApplication ? qsTr("Quit Cloudlight?") : qsTr("End this cloud session?")
                color: DesktopTokens.text
                font.family: DesktopTokens.displayFont
                font.pixelSize: DesktopTokens.px(22)
                font.weight: Font.DemiBold
                wrapMode: Text.WordWrap
            }
            Text {
                width: parent.width
                topPadding: DesktopTokens.px(10)
                text: root.quittingApplication
                    ? qsTr("Cloudlight will close and disconnect from any active cloud session.")
                    : AppController.route === "inserting"
                    ? qsTr("Your session request will be cancelled and you will leave the queue.")
                    : qsTr("Your game will close on the remote rig. This session cannot be resumed after it ends.")
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.px(14)
                wrapMode: Text.WordWrap
                lineHeight: 1.3
            }

            Item { width: 1; height: DesktopTokens.px(24) }
            Item {
                width: parent.width
                height: buttons.height
                Text {
                    anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter
                    width: Math.max(0, parent.width - buttons.width - DesktopTokens.px(16))
                    text: qsTr("Esc to go back · Enter to confirm")
                    color: DesktopTokens.textMuted
                    font.family: DesktopTokens.bodyFont
                    font.pixelSize: DesktopTokens.px(12)
                    elide: Text.ElideRight
                }
                Row {
                    id: buttons
                    anchors.right: parent.right
                    spacing: DesktopTokens.px(10)
                    DesktopButton {
                        id: keepPlayingButton
                        objectName: root.quittingApplication ? "quitConfirmKeepOpen" : "streamExitKeepPlaying"
                        height: DesktopTokens.px(40)
                        text: root.quittingApplication ? qsTr("Keep Cloudlight open")
                            : AppController.route === "inserting" ? qsTr("Keep waiting") : qsTr("Keep playing")
                        onClicked: root.cancelRequested()
                        KeyNavigation.right: endButton
                    }
                    DesktopButton {
                        id: endButton
                        objectName: root.quittingApplication ? "quitConfirmQuit" : "streamExitEndSession"
                        height: DesktopTokens.px(40)
                        text: root.quittingApplication ? qsTr("Quit Cloudlight") : qsTr("End session")
                        primary: true
                        onClicked: root.confirmRequested()
                        KeyNavigation.left: keepPlayingButton
                    }
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
