import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

Column {
    id: page
    objectName: "desktopAccountSettings"
    required property real availableWidth
    required property var settingsScreen
    required property Component profilePageComponent
    required property Component subscriptionPageComponent
    required property Component storesPageComponent

    width: page.availableWidth; spacing: DesktopTokens.px(12)
    Loader { width: parent.width; sourceComponent: page.profilePageComponent }
    Loader { width: parent.width; sourceComponent: page.subscriptionPageComponent }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "person"; title: qsTr("Profiles")
            description: qsTr("Manage saved account profiles"); showDivider: false
            DesktopSettingsButton { text: qsTr("Manage"); onClicked: AppController.navigate("accounts") }
        }
    }
    Loader { width: parent.width; sourceComponent: page.storesPageComponent }
    DesktopSettingsPanel {
        visible: ShellStore.signedIn
        width: parent.width; paperStyle: true
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "person"; title: qsTr("Sign out"); showDivider: false
            DesktopSettingsButton { text: qsTr("Sign out"); danger: true; onClicked: ShellStore.logout() }
        }
    }
}
