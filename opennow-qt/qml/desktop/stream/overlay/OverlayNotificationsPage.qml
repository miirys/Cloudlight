import QtQuick
import OpenNOW

// Which in-stream notices appear, with one master switch.
OverlayPage {
    id: page
    property var menu
    pageName: "notifications"
    title: qsTr("Notifications")
    readonly property bool master: ShellStore.settings.streamNotifications !== false

    component Check: OverlayCheckRow {
        required property string setting
        available: ShellStore.settings.streamNotifications !== false
        checked: ShellStore.settings[setting] !== false
        onActivated: ShellStore.setSetting(setting, !checked)
    }

    OverlayDescribedToggle {
        objectName: "overlayNotificationsMaster"
        title: qsTr("Show notifications")
        checked: page.master
        onActivated: ShellStore.setSetting("streamNotifications", !checked)
    }
    OverlayDivider {}
    OverlaySectionLabel { text: qsTr("Network"); strong: true; topPadding: OverlayStyle.u(30); bottomPadding: OverlayStyle.u(19) }
    Check { title: qsTr("Connection status"); setting: "notifyConnection" }
    OverlaySectionLabel { text: qsTr("Gallery"); strong: true; topPadding: OverlayStyle.u(18); bottomPadding: OverlayStyle.u(19) }
    Check { title: qsTr("Recording saved"); setting: "notifyRecordingSaved" }
    Check { title: qsTr("Instant Replay saved"); setting: "notifyReplaySaved" }
    Check { title: qsTr("Screenshot saved"); setting: "notifyScreenshotSaved" }
    OverlaySectionLabel { text: qsTr("Record and Instant Replay"); strong: true; topPadding: OverlayStyle.u(18); bottomPadding: OverlayStyle.u(19) }
    Check { title: qsTr("Instant Replay is on/off"); setting: "notifyReplayState" }
    Check { title: qsTr("Recording has started"); setting: "notifyRecordingStarted" }
    OverlaySectionLabel { text: qsTr("Devices and display"); strong: true; topPadding: OverlayStyle.u(18); bottomPadding: OverlayStyle.u(19) }
    Check { title: qsTr("Controller connected"); setting: "notifyController" }
    Check { title: qsTr("Color format changed"); setting: "notifyColorFormat" }
}
