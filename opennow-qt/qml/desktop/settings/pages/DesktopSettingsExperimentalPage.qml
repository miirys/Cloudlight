import QtQuick
import OpenNOW

// Features that work but are still being tuned. Kept apart so the main pages
// only carry settings that behave the same on every machine.
Column {
    id: page
    objectName: "desktopExperimentalSettings"
    required property real availableWidth
    required property var settingsScreen

    width: page.availableWidth; spacing: DesktopTokens.px(12)
    DesktopSettingsNotice {
        width: parent.width
        messages: page.settingsScreen.compatibilityWarnings("experimental")
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection {
            text: qsTr("Experimental")
            description: qsTr("These may add latency, artifacts or connection problems on some setups.")
        }
        DesktopSettingsRow {
            objectName: "frameGenerationRow"
            width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Frame generation")
            description: qsTr("Targets 120 displayed FPS from a 60 FPS stream. Requires a fast GPU and 120 Hz display; adds latency and artifacts.")
            DesktopSettingsSegmented {
                readonly property string current: String(page.settingsScreen.valueSetting("frameGeneration", "off")) === "2x" ? "2x" : "off"
                options: [{label: qsTr("Off"), value: "off"}, {label: qsTr("2×"), value: "2x"}]
                optionWidth: 64; selectedIndex: options.findIndex(item => item.value === current)
                onSelected: (index,item) => page.settingsScreen.setSetting("frameGeneration", item.value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("L4S")
            description: qsTr("Request scalable low-latency transport for the next session")
            DesktopSettingsToggle { checked: page.settingsScreen.boolSetting("enableL4S",false); onValueChangedByUser: value => page.settingsScreen.setSetting("enableL4S",value) }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Steam Deck identity"); description: qsTr("Identify as a Steam Deck to unlock its resolutions and 90 FPS.")
            showDivider: false
            DesktopSettingsToggle { checked: page.settingsScreen.boolSetting("identifyAsSteamDeck",false); onValueChangedByUser: value => page.settingsScreen.setSetting("identifyAsSteamDeck",value) }
        }
    }
}
