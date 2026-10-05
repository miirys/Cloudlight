import QtQuick
import OpenNOW

OverlayPage {
    id: page
    property var menu
    pageName: "system"
    title: qsTr("System")

    OverlayDescribedToggle {
        objectName: "overlayClipboard"
        caps: true
        title: qsTr("Clipboard")
        description: qsTr("Turn off to block pasting text from the clipboard with <b>Ctrl+V</b> during gameplay. Text is typed into the game and is not stored.")
        checked: ShellStore.settings.clipboardPaste === true
        onActivated: ShellStore.setSetting("clipboardPaste", !checked)
    }
    OverlayDivider {}
    OverlayDropdown {
        label: qsTr("Keyboard layout")
        items: ShellStore.keyboardLayoutItems
        value: String(ShellStore.settings.keyboardLayout || "en-US")
        onPicked: value => ShellStore.setSetting("keyboardLayout", value)
    }
}
