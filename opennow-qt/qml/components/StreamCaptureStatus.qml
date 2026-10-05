import QtQuick
import OpenNOW

Column {
    id: root
    objectName: "streamCaptureStatus"
    property string notice: ""
    // The desktop stream shows recording time as a corner chip instead (DesktopStreamHud).
    property bool showRecording: true
    readonly property int elapsedSeconds: Math.max(0, Math.floor(ShellStore.streamRecordingElapsedMs / 1000))
    readonly property string elapsedText: {
        const hours = Math.floor(elapsedSeconds / 3600)
        const minutes = Math.floor(elapsedSeconds / 60) % 60
        const seconds = elapsedSeconds % 60
        return (hours > 0 ? String(hours).padStart(2, "0") + ":" : "")
            + String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0")
    }

    anchors.horizontalCenter: parent.horizontalCenter
    anchors.top: parent.top
    anchors.topMargin: 24
    width: Math.min(560, parent.width - 48)
    spacing: 8
    visible: (showRecording && ShellStore.streamRecordingActive) || notice !== ""

    Connections {
        target: ShellStore
        function onStreamCaptureAnnounced(message) {
            root.notice = message
            noticeTimer.restart()
        }
    }

    Timer {
        id: noticeTimer
        interval: 4000
        onTriggered: root.notice = ""
    }

    Rectangle {
        objectName: "streamRecordingIndicator"
        anchors.horizontalCenter: parent.horizontalCenter
        visible: root.showRecording && ShellStore.streamRecordingActive
        width: Math.min(root.width, recordingText.implicitWidth + 48)
        height: 40
        radius: 4
        color: "#1C1C1C"
        Accessible.role: Accessible.StaticText
        Accessible.name: recordingText.text

        Rectangle {
            x: 14; anchors.verticalCenter: parent.verticalCenter
            width: 8; height: 8; radius: 4
            color: "#F2665B"
        }
        Text {
            id: recordingText
            x: 30; anchors.verticalCenter: parent.verticalCenter
            width: parent.width - 44
            text: qsTr("Recording · %1").arg(root.elapsedText)
            color: "#F2F2F2"
            font.family: Theme.bodyFont
            font.pixelSize: 16
            font.weight: Font.DemiBold
            elide: Text.ElideRight
        }
    }

    Rectangle {
        objectName: "streamClipNotice"
        anchors.horizontalCenter: parent.horizontalCenter
        visible: root.notice !== ""
        width: Math.min(root.width, noticeText.implicitWidth + 32)
        height: noticeText.implicitHeight + 24
        radius: 4
        color: "#1C1C1C"
        border.color: "#333333"
        border.width: 0
        Accessible.role: Accessible.StaticText
        Accessible.name: root.notice

        Text {
            id: noticeText
            x: 16; y: 12
            width: parent.width - 32
            text: root.notice
            color: "#F2F2F2"
            font.family: Theme.bodyFont
            font.pixelSize: 16
            font.weight: Font.DemiBold
            wrapMode: Text.Wrap
            maximumLineCount: 3
            elide: Text.ElideRight
        }
    }
}
