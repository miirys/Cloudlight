import QtQuick
import OpenNOW

Column {
    id: page
    objectName: "desktopStreamSettings"
    required property real availableWidth
    required property var settingsScreen
    required property Component statsSettingsPageComponent
    property bool statisticsOpen: false

    width: page.availableWidth; spacing: 0
    DesktopSettingsNotice {
        id: streamNotice
        objectName: "streamCompatibilityNotice"
        width: parent.width
        messages: page.settingsScreen.compatibilityWarnings("stream")
    }
    Item { width: 1; height: DesktopTokens.px(12); visible: streamNotice.visible }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Quality"); description: qsTr("Applies to the next session you start.") }
        DesktopSettingsRow {
            id: modeRow
            objectName: "streamingModeRow"
            readonly property string mode: String(page.settingsScreen.valueSetting("streamingMode", "custom"))
            readonly property var current: page.settingsScreen.streamingModes.find(item => item.value === mode) || page.settingsScreen.streamingModes[4]
            width: parent.width; paperStyle: true; glyph: "sliders"; title: qsTr("Mode")
            description: current.detail + "\n" + qsTr("Data usage is ~%1 GB per hour. Actual data use will vary.").arg(page.settingsScreen.dataUsageGbPerHour())
            DesktopSettingsSegmented {
                objectName: "streamingModeControl"
                options: page.settingsScreen.streamingModes.map(item => ({label: item.label, value: item.value, width: 96}))
                selectedIndex: options.findIndex(item => item.value === modeRow.mode)
                onSelected: (index, item) => page.settingsScreen.applyStreamingMode(item.value)
            }
        }
    }
    Item { width: 1; height: DesktopTokens.px(12) }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Details") }
        DesktopSettingsChoice {
            objectName: "resolutionChoice"
            width: parent.width; title: qsTr("Resolution")
            description: qsTr("Stream resolution. The picture is scaled to fit your display.")
            filterPlaceholder: qsTr("Filter resolutions…")
            items: page.settingsScreen.resolutionItems()
            value: page.settingsScreen.currentResolutionValue()
            valueLabel: value.replace("x", " × ")
            onSelected: value => page.settingsScreen.setSetting("resolution", value)
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Frame rate")
            description: page.settingsScreen.fpsEntitlementNote()
            DesktopSettingsSegmented {
                objectName: "desktopFrameRateControl"
                readonly property var canonical: ShellStore.canonicalFpsValues().map(value => String(value))
                readonly property string current: Number(page.settingsScreen.valueSetting("fps",60)) === 0 ? "AUTO" : String(page.settingsScreen.valueSetting("fps",60))
                options: canonical.indexOf(current) >= 0 ? canonical : [current].concat(canonical)
                optionWidth: 50; selectedIndex: options.indexOf(current)
                disabledValues: page.settingsScreen.lockedFpsValues(); disabledHint: page.settingsScreen.fpsLockedHint()
                onSelected: (index,value) => page.settingsScreen.setSetting("fps",value === "AUTO" ? 0 : Number(value))
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "wave"; title: qsTr("Max bit rate"); description: qsTr("Upper limit for the stream. Higher looks better but needs a faster connection.")
            DesktopSettingsSlider {
                from: 0.22; to: 200; stepSize: 0.01; decimals: 0
                value: Number(page.settingsScreen.valueSetting("maxBitrateMbps",75)); suffix: " Mbps"
                onCommitted: value => page.settingsScreen.setSetting("maxBitrateMbps", Math.round(value * 100) / 100)
            }
        }
        DesktopSettingsRow {
            objectName: "codecSettingsRow"
            width: parent.width; paperStyle: true; glyph: "chip"; title: qsTr("Video codec")
            description: ShellStore.streamerDetectionMessage
            DesktopSettingsSegmented {
                options: [{label:qsTr("Auto"),value:"auto"},{label:"AV1",value:"av1",enabled:ShellStore.codecAvailable("av1") && !ShellStore.codecDisabledByProfile("av1")},{label:"H.265",value:"h265",enabled:ShellStore.codecAvailable("h265") && !ShellStore.codecDisabledByProfile("h265")},{label:"H.264",value:"h264",enabled:ShellStore.codecAvailable("h264") && !ShellStore.codecDisabledByProfile("h264")}]
                disabledHint: qsTr("Not supported by your video decoder or colour precision setting")
                optionWidth: 64; selectedIndex: options.findIndex(item => item.value === page.settingsScreen.valueSetting("codec","auto"))
                onSelected: (index,item) => page.settingsScreen.setChoice("codec",item.value)
            }
        }
        DesktopSettingsHevcHelp {
            width: parent.width
            runtimeReady: ShellStore.nativeRuntimeReady
            capabilities: ShellStore.nativeRuntimeCapabilities
            onOpenStoreRequested: url => Qt.openUrlExternally(url)
        }
        DesktopSettingsChoice {
            objectName: "graphicsProcessorSelector"
            visible: GraphicsDevices.selectorVisible
            width: parent.width
            title: qsTr("Graphics processor")
            description: GraphicsDevices.savedDeviceUnavailable
                ? qsTr("The saved graphics card isn't available, so the first one that can decode video is used. Takes effect after restarting Cloudlight.")
                : qsTr("Graphics card used to decode and show the stream. Takes effect after restarting Cloudlight.")
            glyph: "monitor"
            items: GraphicsDevices.choices
            maximumColumns: 2
            readonly property string preferredId: String(page.settingsScreen.valueSetting("windowsGpuDeviceId", ""))
            value: items.some(item => item.value === preferredId && !item.disabled) ? preferredId : ""
            onSelected: value => ShellStore.setSetting("windowsGpuDeviceId", value)
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "drop"; title: qsTr("Adjust for network conditions")
            description: String(page.settingsScreen.valueSetting("networkAdjust", "off")) === "quality"
                ? qsTr("Keeps the resolution and lowers the frame rate when your connection can't keep up.")
                : String(page.settingsScreen.valueSetting("networkAdjust", "off")) === "latency"
                ? qsTr("Keeps the frame rate and lowers the resolution when your connection can't keep up.")
                : qsTr("Holds your chosen quality. The stream may stutter if your connection can't keep up.")
            DesktopSettingsSegmented {
                objectName: "networkAdjustControl"
                options: [{label: qsTr("Off"), value: "off", width: 64}, {label: qsTr("Optimal latency"), value: "latency", width: 128}, {label: qsTr("Optimal quality"), value: "quality", width: 128}]
                selectedIndex: options.findIndex(item => item.value === String(page.settingsScreen.valueSetting("networkAdjust", "off")))
                onSelected: (index, item) => page.settingsScreen.setSetting("networkAdjust", item.value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "sun"; title: qsTr("HDR")
            description: !ShellStore.tenBitAllowedByMembership() ? qsTr("HDR10 requires a Performance or Ultimate membership.")
                : HdrOutput.supported && !ShellStore.hdrDecoderAvailable()
                ? qsTr("HDR needs a graphics card that can decode 10-bit H.265 or AV1.") : HdrOutput.status
            DesktopSettingsToggle {
                objectName: "enableHdrToggle"
                checked: page.settingsScreen.boolSetting("enableHdr", false)
                enabled: ((HdrOutput.supported && ShellStore.hdrDecoderAvailable()) || checked) && (ShellStore.tenBitAllowedByMembership() || checked)
                opacity: enabled ? 1 : 0.45
                Accessible.name: qsTr("HDR")
                onValueChangedByUser: value => page.settingsScreen.setSetting("enableHdr", value)
            }
        }
        DesktopSettingsChoice {
            objectName: "colorQualityChoice"
            width: parent.width; glyph: "sun"; title: qsTr("Color precision")
            description: page.settingsScreen.colorQualityFooter()
            items: ShellStore.settingsOwnerState.colorQualityItems
            value: page.settingsScreen.valueSetting("colorQuality", "8bit_420")
            onSelected: value => page.settingsScreen.setChoice("colorQuality", value)
        }
        DesktopSettingsRow {
            objectName: "streamingResetRow"
            width: parent.width; paperStyle: true; glyph: "reset"; title: qsTr("Reset details")
            description: String(page.settingsScreen.valueSetting("streamingMode", "custom")) === "custom"
                ? qsTr("Return these settings to Cloudlight's defaults.")
                : qsTr("Return these settings to the selected mode's values.")
            showDivider: false
            DesktopSettingsButton {
                objectName: "streamingResetButton"
                text: qsTr("Reset")
                onClicked: page.settingsScreen.resetStreamingDetails()
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Display") }
        DesktopSettingsRow {
            objectName: "upscalingSettingsRow"
            width: parent.width; paperStyle: true; glyph: "monitor"; title: qsTr("Upscaling")
            description: Qt.platform.os === "osx"
                ? qsTr("Sharper upscaling when the stream is smaller than your display. Uses extra GPU time.")
                : qsTr("Sharper upscaling (FSR 1) when the stream is smaller than your display. Uses extra GPU time; not used with HDR.")
            DesktopSettingsSegmented {
                objectName: "upscalingSelector"
                readonly property string mode: Qt.platform.os === "osx" ? "metalfx" : "fsr1"
                readonly property string current: String(page.settingsScreen.valueSetting("upscaling", "off")) === mode ? mode : "off"
                options: [{label: qsTr("Off"), value: "off"}, {label: Qt.platform.os === "osx" ? "MetalFX" : "FSR 1", value: mode}]
                optionWidth: 90; selectedIndex: options.findIndex(item => item.value === current)
                onSelected: (index,item) => page.settingsScreen.setSetting("upscaling", item.value)
            }
        }
        DesktopSettingsRow {
            objectName: "upscalingSharpnessRow"
            enabled: page.settingsScreen.valueSetting("upscaling", "off") === (Qt.platform.os === "osx" ? "metalfx" : "fsr1")
            visible: enabled
            width: parent.width; paperStyle: true; glyph: "sun"; title: qsTr("Clarity")
            description: Qt.platform.os === "osx"
                ? qsTr("Sharpen details before MetalFX upscaling. Set to 0 to disable.")
                : qsTr("Sharpen details after FSR 1 upscaling. Set to 0 to disable.")
            DesktopSettingsSlider {
                objectName: "upscalingSharpnessSlider"
                accessibleName: qsTr("Clarity")
                from: 0; to: 15; stepSize: 1; suffix: ""
                value: Number(page.settingsScreen.valueSetting("upscalingSharpness", 10))
                onCommitted: value => page.settingsScreen.setSetting("upscalingSharpness", Math.round(value))
            }
        }
        DesktopSettingsRow {
            objectName: "upscalingDenoiseRow"
            enabled: Qt.platform.os === "osx" && page.settingsScreen.valueSetting("upscaling", "off") === "metalfx"
            visible: enabled
            width: parent.width; paperStyle: true; glyph: "drop"; title: qsTr("Noise Reduction")
            description: qsTr("Smooth noise before MetalFX upscaling. Set to 0 to disable.")
            DesktopSettingsSlider {
                objectName: "upscalingDenoiseSlider"
                accessibleName: qsTr("Noise Reduction")
                from: 0; to: 20; stepSize: 1; suffix: ""
                value: Number(page.settingsScreen.valueSetting("upscalingDenoise", 0))
                onCommitted: value => page.settingsScreen.setSetting("upscalingDenoise", Math.round(value))
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Latency") }
        DesktopSettingsRow {
            id: reflexRow
            objectName: "reflexRow"
            readonly property bool available: Number(page.settingsScreen.valueSetting("fps", 60)) >= 120
            readonly property bool forced: page.settingsScreen.boolSetting("enableCloudGsync", false)
            width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("Reflex")
            description: forced ? qsTr("Always on while Cloud G-SYNC is on.")
                : available ? qsTr("Lowers the game's render latency on the server.")
                : qsTr("Available at 120 FPS and above.")
            DesktopSettingsToggle {
                objectName: "reflexToggle"
                Accessible.name: qsTr("Reflex")
                enabled: reflexRow.available && !reflexRow.forced
                opacity: enabled ? 1 : 0.45
                checked: reflexRow.forced || (reflexRow.available && page.settingsScreen.boolSetting("enableReflex", true))
                onValueChangedByUser: value => page.settingsScreen.setSetting("enableReflex", value)
            }
        }
        DesktopSettingsRow {
            objectName: "cloudGsyncRow"
            width: parent.width; paperStyle: true; glyph: "bolt"; title: qsTr("Cloud G-SYNC")
            description: qsTr("Variable frame pacing with lower latency. Only turn this on if your display and graphics driver run variable refresh rate (G-SYNC or FreeSync); on a fixed-refresh display it causes stutter.")
            showDivider: false
            DesktopSettingsToggle {
                objectName: "cloudGsyncToggle"
                Accessible.name: qsTr("Cloud G-SYNC")
                checked: page.settingsScreen.boolSetting("enableCloudGsync",false)
                onValueChangedByUser: value => page.settingsScreen.setSetting("enableCloudGsync",value)
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Session") }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "monitor"; title: qsTr("Full screen when a game starts")
            description: qsTr("Press F11 to switch between full screen and window while playing.")
            DesktopSettingsToggle {
                objectName: "autoFullScreenToggle"
                checked: page.settingsScreen.boolSetting("autoFullScreen", true)
                Accessible.name: qsTr("Full screen when a game starts")
                onValueChangedByUser: value => page.settingsScreen.setSetting("autoFullScreen", value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Steam Big Picture mode")
            description: qsTr("Open Steam games in Big Picture mode, for controller play.")
            DesktopSettingsToggle {
                objectName: "steamBigPictureToggle"
                checked: page.settingsScreen.boolSetting("steamBigPictureMode", false)
                onValueChangedByUser: value => page.settingsScreen.setSetting("steamBigPictureMode", value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "controller"; title: qsTr("Save in-game settings")
            description: qsTr("Keep your in-game graphics settings between sessions, in supported games.")
            DesktopSettingsToggle {
                objectName: "persistentInGameSettingsToggle"
                checked: page.settingsScreen.boolSetting("enablePersistingInGameSettings", true)
                Accessible.name: qsTr("Save in-game settings")
                onValueChangedByUser: value => page.settingsScreen.setSetting("enablePersistingInGameSettings", value)
            }
        }
        DesktopSettingsRow {
            width: parent.width; paperStyle: true; glyph: "info"
            title: qsTr("Background stream reminder")
            description: qsTr("Flash the taskbar every 5 minutes while a game streams in the background. Doesn't prevent idle timeouts.")
            showDivider: false
            DesktopSettingsToggle {
                objectName: "backgroundStreamReminderToggle"
                checked: page.settingsScreen.valueSetting("backgroundStreamReminder", false) === true
                onValueChangedByUser: value => page.settingsScreen.setSetting("backgroundStreamReminder", value)
            }
        }
    }
    DesktopSettingsPanel {
        width: parent.width; paperStyle: true
        DesktopSettingsSection { text: qsTr("Statistics overlay") }
        DesktopSettingsRow {
            objectName: "statisticsOverlaySection"
            width: parent.width; paperStyle: true; glyph: "speed"; title: qsTr("Overlay contents and position")
            description: qsTr("Press Ctrl+N while playing to show or hide it.")
            expandable: true; expanded: page.statisticsOpen; showDivider: false
            onExpansionRequested: page.statisticsOpen = !page.statisticsOpen
        }
    }
    DesktopSettingsDisclosure {
        objectName: "streamStatsDisclosure"
        width: parent.width; expanded: page.statisticsOpen
        sourceComponent: page.statsSettingsPageComponent
    }
    DesktopSettingsAdvanced {
        detail: qsTr("Video decoder")
        expanded: page.settingsScreen.advancedOpen
        onClicked: page.settingsScreen.advancedOpen = !page.settingsScreen.advancedOpen
    }
    DesktopSettingsDisclosure {
        width: parent.width; expanded: page.settingsScreen.advancedOpen
        sourceComponent: DesktopSettingsPanel {
            width: page.availableWidth; paperStyle: true
            DesktopSettingsChoice {
                objectName: "streamBackendChoice"
                width: parent.width; glyph: "chip"; title: qsTr("Video decoder")
                description: Qt.platform.os === "windows"
                    ? qsTr("How the stream is decoded. Auto uses DirectX 11 hardware decoding. Applies to the next session.")
                    : qsTr("How the stream is decoded. Choose Software if hardware decoding is slow or stutters. Applies to the next session.")
                items: ShellStore.videoBackendItems()
                value: page.settingsScreen.valueSetting("nativeVideoBackend", "auto")
                onSelected: value => page.settingsScreen.setSetting("nativeVideoBackend", value)
            }
        }
    }
}
