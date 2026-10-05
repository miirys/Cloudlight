pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// Shortcuts, as on GeForce NOW: collapsible Gamepad and Keyboard sections, grey group
// labels and flat key boxes that record a new chord when activated.
OverlayPage {
    id: page
    property var menu
    pageName: "shortcuts"
    title: qsTr("Shortcuts")
    property bool gamepadOpen: true
    property bool keyboardOpen: true

    readonly property var defaults: ({
        shortcutToggleMicrophone: "Ctrl+Shift+M", shortcutToggleFullscreen: "F11",
        shortcutTogglePointerLock: "F8", shortcutToggleAntiAfk: "Ctrl+Shift+K",
        shortcutStopStream: "Ctrl+Shift+Q", shortcutToggleStats: "Ctrl+N",
        shortcutToggleRecording: "F12", shortcutSaveClip: "Ctrl+F12",
        shortcutScreenshot: "Ctrl+F11", shortcutGameFilter1: "",
        shortcutGameFilter2: "", shortcutGameFilter3: ""
    })
    function bind(key, chord) {
        const change = {}
        change[key] = chord
        ShellStore.updateShortcuts(change)
    }

    component Section: OverlayRow {
        property bool open: true
        height: OverlayStyle.u(96)
        titleInset: OverlayStyle.u(93)
        iconInk: "#FFFFFF"
        trailing: "chevron"
        Accessible.role: Accessible.ButtonDropDown
        // Chevron points down when closed and up when open, like the reference.
        OverlayIcon {
            anchors.right: parent.right
            anchors.rightMargin: OverlayStyle.u(48)
            anchors.verticalCenter: parent.verticalCenter
            width: OverlayStyle.u(28)
            height: width
            name: parent.open ? "expand-less" : "expand-more"
            ink: OverlayStyle.text
        }
    }
    component Key: OverlayKeyField {
        required property string setting
        shortcut: String(ShellStore.settings[setting] ?? page.defaults[setting] ?? "")
        clearable: true
        onCaptured: chord => page.bind(setting, chord)
    }

    Section {
        id: gamepad
        objectName: "overlayShortcutsGamepad"
        icon: "gameplay"
        title: qsTr("Gamepad")
        trailing: "none"
        open: page.gamepadOpen
        onActivated: page.gamepadOpen = !page.gamepadOpen
    }
    OverlayDropdown {
        visible: page.gamepadOpen
        inline: true
        label: qsTr("Open/close the in-game overlay")
        items: [
            {value: true, label: qsTr("Hold Start")},
            {value: false, label: qsTr("Guide only")}
        ]
        value: ShellStore.settings.controllerHoldStartOverlay !== false
        onPicked: value => ShellStore.setSetting("controllerHoldStartOverlay", value)
    }
    Section {
        objectName: "overlayShortcutsKeyboard"
        icon: "keyboard"
        title: qsTr("Keyboard")
        trailing: "none"
        open: page.keyboardOpen
        onActivated: page.keyboardOpen = !page.keyboardOpen
    }
    Column {
        width: parent.width
        visible: page.keyboardOpen
        OverlaySectionLabel { text: qsTr("General"); topPadding: 0 }
        OverlayKeyField {
            title: qsTr("Open/close the in-game overlay")
            shortcut: "Ctrl+G"
            fixed: true
        }
        Key { visible: ShellStore.microphoneToggleAvailable; title: qsTr("Toggle microphone on/off"); setting: "shortcutToggleMicrophone" }
        Key { title: qsTr("Full screen"); setting: "shortcutToggleFullscreen" }
        Key { title: qsTr("Lock the mouse to the game"); setting: "shortcutTogglePointerLock" }
        Key { title: qsTr("Anti-AFK on/off"); setting: "shortcutToggleAntiAfk" }
        Key { title: qsTr("Quit the game"); setting: "shortcutStopStream" }
        OverlaySectionLabel { text: qsTr("Statistics") }
        Key { title: qsTr("Change format"); setting: "shortcutToggleStats" }
        OverlaySectionLabel { text: qsTr("Record") }
        Key { title: qsTr("Toggle record on/off"); setting: "shortcutToggleRecording" }
        Key {
            title: qsTr("Save last %1 seconds recorded").arg(Number(ShellStore.settings.replayBufferSeconds || 30))
            setting: "shortcutSaveClip"
        }
        OverlaySectionLabel { text: qsTr("Capture") }
        Key { title: qsTr("Save a screenshot"); setting: "shortcutScreenshot" }
        OverlaySectionLabel { text: qsTr("Game filters") }
        Key { title: qsTr("Toggle style 1 on/off"); setting: "shortcutGameFilter1" }
        Key { title: qsTr("Toggle style 2 on/off"); setting: "shortcutGameFilter2" }
        Key { title: qsTr("Toggle style 3 on/off"); setting: "shortcutGameFilter3" }
        Text {
            visible: ShellStore.shortcutUpdateError !== ""
            x: OverlayStyle.gutter
            width: parent.width - x * 2
            topPadding: OverlayStyle.u(8)
            text: ShellStore.shortcutUpdateError
            color: "#E88A8A"
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.subtitleSize
            wrapMode: Text.WordWrap
        }
    }
    OverlayDivider {}
    OverlayRow {
        objectName: "overlayShortcutsReset"
        icon: "reset"
        title: qsTr("Reset to defaults")
        onActivated: ShellStore.updateShortcuts(page.defaults)
    }
}
