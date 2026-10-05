import QtQuick
import OpenNOW

// Settings index, grouped like GeForce NOW's: app and play settings, then capture.
OverlayPage {
    id: page
    property var menu
    pageName: "settings"
    title: qsTr("Settings")

    component Entry: OverlayRow {
        required property string target
        trailing: "chevron"
        onActivated: page.menu.openPage(target)
    }
    Item { width: 1; height: OverlayStyle.u(15) }
    Entry { objectName: "overlaySettingsGeneral"; target: "general"; icon: "info"; title: qsTr("General") }
    Entry { target: "gameplay"; icon: "gameplay"; title: qsTr("Gameplay") }
    Entry { target: "system"; icon: "system"; title: qsTr("System") }
    Entry { target: "shortcuts"; icon: "keyboard"; title: qsTr("Shortcuts") }
    Entry { target: "hud"; icon: "hud"; title: qsTr("Heads up display") }
    Entry { target: "notifications"; icon: "notifications"; title: qsTr("Notifications") }
    OverlayDivider { height: OverlayStyle.u(54) }
    Entry { target: "capture"; icon: "video"; title: qsTr("Video capture") }
    Entry { target: "files"; icon: "storage"; title: qsTr("Files and disk space") }
}
