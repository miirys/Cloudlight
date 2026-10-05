import QtQuick
import QtQuick.Window
import OpenNOW

// Session controls that GeForce NOW keeps elsewhere but Cloudlight can change live.
OverlayPage {
    id: page
    property var menu
    pageName: "gameplay"
    title: qsTr("Gameplay")
    readonly property var bindings: ShellStore.streamShortcutBindings()
    function binding(action) {
        const keys = page.bindings[action] || []
        return keys.length ? String(keys[0]) : ""
    }

    Item { width: 1; height: OverlayStyle.u(8) }
    OverlayRow {
        objectName: "overlayFullscreen"
        icon: "hud"
        title: qsTr("Full screen")
        subtitle: page.binding("toggle-fullscreen")
        trailing: "toggle"
        checked: page.menu.fullscreen
        onActivated: page.menu.runAction(3)
    }
    OverlayRow {
        objectName: "overlayAntiAfk"
        icon: "clock"
        title: qsTr("Anti-AFK")
        subtitle: qsTr("Keeps the session from timing out while you're away")
        trailing: "toggle"
        checked: ShellStore.antiAfkEnabled
        onActivated: ShellStore.applyStreamShortcutAction("toggle-anti-afk")
    }
    OverlayRow {
        objectName: "overlaySessionClock"
        icon: "clock"
        title: qsTr("Session clock")
        subtitle: qsTr("Shows how long you've been playing")
        trailing: "toggle"
        checked: ShellStore.settings.sessionCounterEnabled === true
        onActivated: ShellStore.setSetting("sessionCounterEnabled", !checked)
    }
    OverlayDivider {}
    OverlayRow {
        objectName: "overlayConsoleMode"
        icon: "gameplay"
        title: page.menu.modeOn ? qsTr("Switch to desktop mode") : qsTr("Switch to console mode")
        subtitle: qsTr("Changes the Cloudlight interface around the game")
        available: !DesktopTokens.consoleModePending(page.Window.window)
        onActivated: page.menu.runAction(2)
    }
    OverlayRow {
        objectName: "overlayInvite"
        visible: Boolean(ShellStore.socialCapabilities && ShellStore.socialCapabilities.invitesAvailable)
        icon: "notifications"
        title: qsTr("Invite a friend")
        trailing: "chevron"
        onActivated: page.menu.runAction(1)
    }
}
