import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopSettingsScreen"
    anchors.fill: parent
    clip: true

    property int selectedSection: 3
    property string searchQuery: ""
    property bool advancedOpen: false
    // Isolated visual acceptance only; never selects alternate production UI.
    property string acceptancePanel: ""
    readonly property var acceptancePanels: ({stats:statsSettingsPage, audio:audioPage,
        interface:interfacePage, console:consolePage, shortcuts:shortcutsPage,
        controllers:controllersPage, subscription:subscriptionPage, recording:recordingPage})
    readonly property bool compactNavigation: width < DesktopTokens.px(1050)
    readonly property int selectedGroup: sections.findIndex(section => section.page ===
        ([0,1,2].indexOf(selectedSection) >= 0 ? 0 : selectedSection === 10 ? 5 : selectedSection === 7 ? 8 : selectedSection))
    onSelectedSectionChanged: { advancedOpen = false }
    signal requestConsoleMode(bool enabled)

    TenBitWarningDialog { id: tenBitWarning; settingsStore: ShellStore }

    readonly property var sections: [
        {label: qsTr("Account"), detail: qsTr("Profile, membership, game stores"), icon: "person", page: 0, keywords: "profile subscription membership stores steam epic xbox ubisoft battle gaijin connections"},
        {label: qsTr("Streaming quality"), detail: qsTr("Resolution, frame rate, bit rate"), icon: "monitor", page: 3, keywords: "resolution fps frame rate hdr color precision stats overlay timer bitrate bit rate codec g-sync gsync vrr backend gpu directx vulkan steam big picture launch gamepad fullscreen session ready persistent in-game graphics settings background reminder afk taskbar upscaling"},
        {label: qsTr("Server location"), detail: qsTr("Region, network test, proxy"), icon: "globe", page: 6, keywords: "server region location ping network test proxy"},
        {label: qsTr("Audio"), detail: qsTr("Output and microphone"), icon: "wave", page: 4, keywords: "sound audio volume output microphone mute focus"},
        {label: qsTr("Controls"), detail: qsTr("Controllers, mouse, keyboard, shortcuts"), icon: "controller", page: 5, keywords: "controller gyroscope steam sensitivity mouse keyboard language shortcuts"},
        {label: qsTr("Capture"), detail: qsTr("Screenshots, recording, replay"), icon: "image", page: 12, keywords: "recording capture clip replay buffer memory duration folder resolution fps quality shortcuts F12 screenshot"},
        {label: qsTr("Interface"), detail: qsTr("Theme, accent colour, language"), icon: "palette", page: 8, keywords: "theme accent color colour interface language scale motion sidebar tiles appearance"},
        {label: qsTr("Console mode"), detail: qsTr("Controller-first full-screen interface"), icon: "controller", page: 9, keywords: "console fullscreen gamepad startup big screen tv"},
        {label: qsTr("Experimental"), detail: qsTr("Features still being tested"), icon: "flask", page: 13, keywords: "experimental frame generation l4s steam deck beta preview test lab advanced"},
        {label: qsTr("About"), detail: qsTr("Version, updates, diagnostics"), icon: "info", page: 11, keywords: "version release update diagnostics onboarding introduction replay setup restart reset help support"}
    ]
    readonly property var pageTitles: [qsTr("Account"), qsTr("Account"), qsTr("Account"), qsTr("Streaming quality"), qsTr("Audio"), qsTr("Controls"), qsTr("Server location"), qsTr("Interface"), qsTr("Interface"), qsTr("Console mode"), qsTr("Controls"), qsTr("About"), qsTr("Capture"), qsTr("Experimental")]
    readonly property var pageComponents: [accountGroup, accountGroup, accountGroup, streamPage, audioPage, controlsGroup, networkPage, lookGroup, lookGroup, consolePage, controlsGroup, aboutPage, recordingPage, experimentalPage]

    // Every setting row by page, so search finds individual settings on pages that
    // are not loaded. tst_embeddedorchestration keeps it in step with the pages.
    readonly property var searchIndex: [
        {title: qsTr("Membership"), detail: qsTr("Your plan, playtime left and when it resets"), page: 0},
        {title: qsTr("Refresh membership"), detail: qsTr("Reload your plan and playtime from NVIDIA."), page: 0},
        {title: qsTr("Profiles"), detail: qsTr("Manage saved account profiles"), page: 0},
        {title: qsTr("Sign out"), detail: "", page: 0},
        {title: qsTr("Mode"), detail: "", page: 3},
        {title: qsTr("Resolution"), detail: qsTr("Stream resolution. The picture is scaled to fit your display."), page: 3},
        {title: qsTr("Frame rate"), detail: "", page: 3},
        {title: qsTr("Max bit rate"), detail: qsTr("Upper limit for the stream. Higher looks better but needs a faster connection."), page: 3},
        {title: qsTr("Video codec"), detail: "", page: 3},
        {title: qsTr("Graphics processor"), detail: "", page: 3},
        {title: qsTr("Adjust for network conditions"), detail: "", page: 3},
        {title: qsTr("HDR"), detail: "", page: 3},
        {title: qsTr("Color precision"), detail: "", page: 3},
        {title: qsTr("Reset details"), detail: "", page: 3},
        {title: qsTr("Upscaling"), detail: "", page: 3},
        {title: qsTr("Clarity"), detail: "", page: 3},
        {title: qsTr("Noise Reduction"), detail: qsTr("Smooth noise before MetalFX upscaling. Set to 0 to disable."), page: 3},
        {title: qsTr("Reflex"), detail: "", page: 3},
        {title: qsTr("Cloud G-SYNC"), detail: qsTr("Variable frame pacing with lower latency. Only turn this on if your display and graphics driver run variable refresh rate (G-SYNC or FreeSync); on a fixed-refresh display it causes stutter."), page: 3},
        {title: qsTr("Full screen when a game starts"), detail: qsTr("Press F11 to switch between full screen and window while playing."), page: 3},
        {title: qsTr("Steam Big Picture mode"), detail: qsTr("Open Steam games in Big Picture mode, for controller play."), page: 3},
        {title: qsTr("Save in-game settings"), detail: qsTr("Keep your in-game graphics settings between sessions, in supported games."), page: 3},
        {title: qsTr("Background stream reminder"), detail: qsTr("Flash the taskbar every 5 minutes while a game streams in the background. Doesn't prevent idle timeouts."), page: 3},
        {title: qsTr("Overlay contents and position"), detail: qsTr("Press Ctrl+N while playing to show or hide it."), page: 3},
        {title: qsTr("Video decoder"), detail: "", page: 3},
        {title: qsTr("Show on stream launch"), detail: qsTr("Cycle compact bar, extended panel and off with your statistics shortcut"), page: 3},
        {title: qsTr("Position"), detail: "", page: 3},
        {title: qsTr("Overlay scale"), detail: "", page: 3},
        {title: qsTr("Background opacity"), detail: "", page: 3},
        {title: qsTr("Standalone session timer"), detail: qsTr("Show a small timer while playing, independently of the statistics overlay."), page: 3},
        {title: qsTr("Customize metrics"), detail: "", page: 3},
        {title: qsTr("Output device"), detail: qsTr("Applies to your next streaming session. A fixed device must be available when the session starts."), page: 4},
        {title: qsTr("Microphone"), detail: "", page: 4},
        {title: qsTr("Mute when out of focus"), detail: qsTr("Silence stream audio while using another app. Audio returns when you switch back to Cloudlight."), page: 4},
        {title: qsTr("Mouse sensitivity"), detail: qsTr("Applied to native relative mouse input"), page: 5},
        {title: qsTr("Keyboard layout"), detail: "", page: 5},
        {title: qsTr("Clipboard paste"), detail: qsTr("Paste local text into the stream with Ctrl+V (Command+V on macOS). Up to 64 KiB per paste. No automatic clipboard sync."), page: 5},
        {title: qsTr("Game language"), detail: "", page: 5},
        {title: qsTr("Shortcuts"), detail: qsTr("Local shortcuts are consumed before gameplay input"), page: 5},
        {title: qsTr("Cursor overlay"), detail: "", page: 5},
        {title: qsTr("Controller input"), detail: qsTr("%1 connected"), page: 5},
        {title: qsTr("Controller input source"), detail: qsTr("Choose one device as Player 1 if a controller appears twice. Selection lasts until app restart; select again after reconnecting."), page: 5},
        {title: qsTr("No controllers connected"), detail: qsTr("Connect a controller to assign a player"), page: 5},
        {title: qsTr("Left stick dead zone"), detail: qsTr("Ignore stick drift during gameplay. Default: 5%. The remaining travel is rescaled to full range."), page: 5},
        {title: qsTr("Right stick dead zone"), detail: qsTr("Ignore stick drift during gameplay. Default: 5%. Set to 0% to leave dead zones to the game."), page: 5},
        {title: qsTr("Controller vibration"), detail: qsTr("Scale game vibration on supported controllers. Set to 0% to disable."), page: 5},
        {title: qsTr("Gyroscope"), detail: qsTr("Motion aiming on supported pads"), page: 5},
        {title: qsTr("Server region"), detail: "", page: 6},
        {title: qsTr("Region latency"), detail: "", page: 6},
        {title: qsTr("Network test"), detail: qsTr("Check latency and packet loss to the GeForce NOW server you'll play on."), page: 6},
        {title: qsTr("Free-tier queue selector"), detail: qsTr("Compare queues and latency before launching a game"), page: 6},
        {title: qsTr("Test network before each launch"), detail: qsTr("Measure this zone's UDP payload reachability before streaming · selected zones only"), page: 6},
        {title: qsTr("Use proxy"), detail: qsTr("Applies to API calls only · the stream always goes direct"), page: 6},
        {title: qsTr("Proxy address"), detail: qsTr("Leave empty to use a direct connection"), page: 6},
        {title: qsTr("Choose a background image"), detail: "", page: 8},
        {title: qsTr("Theme"), detail: qsTr("Applies the pack's appearance, accent and surfaces"), page: 8},
        {title: qsTr("Appearance"), detail: "", page: 8},
        {title: qsTr("Accent"), detail: qsTr("Selection, toggles and keyboard focus"), page: 8},
        {title: qsTr("Translucent interface"), detail: qsTr("Use translucent shell surfaces when supported"), page: 8},
        {title: qsTr("Background"), detail: qsTr("A solid color, game art, gradient or your own image"), page: 8},
        {title: qsTr("Custom image"), detail: "", page: 8},
        {title: qsTr("Image opacity"), detail: qsTr("0% hides the image · 100% shows the full image"), page: 8},
        {title: qsTr("Library tiles"), detail: qsTr("How much art you see per row"), page: 8},
        {title: qsTr("Reduce motion"), detail: qsTr("Cuts parallax and cover animations · follows your OS by default"), page: 8},
        {title: qsTr("Interface language"), detail: "", page: 8},
        {title: qsTr("Interface scale"), detail: "", page: 8},
        {title: qsTr("Use the Windows title bar"), detail: qsTr("Show the standard Windows title bar instead of Cloudlight's own window controls."), page: 8},
        {title: qsTr("One app, two shells"), detail: qsTr("Same session, settings and themes · switching does not restart the stream"), page: 9},
        {title: qsTr("Start in console mode"), detail: qsTr("Remember this choice for the next time Cloudlight launches"), page: 9},
        {title: qsTr("Controller profile picker"), detail: qsTr("Choose a saved profile when console mode starts"), page: 9},
        {title: qsTr("Enter console mode when a gamepad is the only input"), detail: qsTr("Ignored while a mouse has moved in the last 30 seconds"), page: 9},
        {title: qsTr("Leave console mode on keyboard or mouse input"), detail: qsTr("Keeps your place in the grid when the shell swaps"), page: 9},
        {title: qsTr("Automatically check for updates"), detail: qsTr("Check every six hours while no streaming session is active."), page: 11},
        {title: qsTr("Automatically download updates"), detail: qsTr("Download verified updates while idle. Installation always requires your confirmation."), page: 11},
        {title: qsTr("Update channel"), detail: qsTr("Choose which releases Cloudlight checks"), page: 11},
        {title: qsTr("Release notes"), detail: "", page: 11},
        {title: qsTr("Independent client"), detail: qsTr("Cloudlight is not affiliated with, endorsed by or supported by NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation. You bring your own account and subscription."), page: 11},
        {title: qsTr("Replay onboarding"), detail: "", page: 11},
        {title: qsTr("Reset all settings"), detail: "", page: 11},
        {title: qsTr("Replay onboarding?"), detail: "", page: 11},
        {title: qsTr("Save location"), detail: "", page: 12},
        {title: qsTr("Captures folder"), detail: "", page: 12},
        {title: qsTr("Enable replay buffer"), detail: qsTr("Off by default. Keep recent source video and audio in memory to save a clip. Enabling takes effect next session; disabling clears the buffer immediately."), page: 12},
        {title: qsTr("Replay duration"), detail: qsTr("Target clip length. Memory limits and source keyframes may shorten clips or require waiting for a new keyframe. Changes apply next session."), page: 12},
        {title: qsTr("Replay memory limit"), detail: qsTr("Maximum memory for buffered media. Higher stream bitrates fill it sooner. Changes take effect next session."), page: 12},
        {title: qsTr("Frame generation"), detail: qsTr("Targets 120 displayed FPS from a 60 FPS stream. Requires a fast GPU and 120 Hz display; adds latency and artifacts."), page: 13},
        {title: qsTr("L4S"), detail: qsTr("Request scalable low-latency transport for the next session"), page: 13},
        {title: qsTr("Steam Deck identity"), detail: qsTr("Identify as a Steam Deck to unlock its resolutions and 90 FPS."), page: 13}
    ]
    readonly property var searchResults: {
        const query = searchQuery.trim().toLowerCase()
        if (!query) return []
        const words = query.split(/\s+/)
        const results = []
        for (const entry of searchIndex) {
            const section = sections.find(item => item.page === entry.page) || {label: ""}
            const haystack = (entry.title + " " + entry.detail + " " + section.label).toLowerCase()
            if (!words.every(word => haystack.indexOf(word) >= 0)) continue
            const titleHit = entry.title.toLowerCase().indexOf(query) >= 0
            results.push({title: entry.title, detail: entry.detail, page: entry.page, section: section.label,
                          score: (entry.title.toLowerCase().startsWith(query) ? 0 : titleHit ? 1 : 2)})
        }
        return results.sort((a, b) => a.score - b.score)
    }
    property string pendingReveal: ""
    function openSearchResult(result) {
        pendingReveal = result.title
        searchQuery = ""
        if (selectedSection === result.page) revealPendingSetting()
        else selectedSection = result.page
    }
    function findTitled(item, title) {
        if (!item) return null
        if (item.title === title && item.visible) return item
        for (const child of item.children || []) {
            const found = findTitled(child, title)
            if (found) return found
        }
        return null
    }
    function revealPendingSetting() {
        const title = pendingReveal
        pendingReveal = ""
        if (!title || !pageLoader.item) return
        const target = findTitled(pageLoader.item, title)
        if (!target) return
        const y = target.mapToItem(pageLoader, 0, 0).y
        contentFlick.contentY = Math.max(0, Math.min(contentFlick.contentHeight - contentFlick.height, y - DesktopTokens.px(24)))
    }

    function matchesSection(section) {
        const query = searchQuery.trim().toLowerCase()
        return !query || (section.label + " " + section.detail + " " + section.keywords).toLowerCase().indexOf(query) >= 0
    }

    Component.onCompleted: {
        ShellStore.refreshRegions()
        ShellStore.refreshGameAccounts()
    }

    function boolSetting(key, fallbackValue) {
        return ShellStore.settings[key] === undefined ? fallbackValue : Boolean(ShellStore.settings[key])
    }

    function valueSetting(key, fallbackValue) {
        if (key === "region") return ShellStore.selectedRegion
        const value = ShellStore.settings[key]
        return value === undefined || value === null || value === "" ? fallbackValue : value
    }

    function setSetting(key, value) {
        ShellStore.setSetting(key, value)
        if (key === "resolution")
            Qt.callLater(root.clampFpsToEntitlement)
        // Editing any value a preset owns turns the mode into Custom, as in
        // GeForce NOW.
        if (!root.applyingPreset && root.streamPresetKeys.indexOf(key) >= 0
                && String(root.valueSetting("streamingMode", "custom")) !== "custom")
            ShellStore.setSetting("streamingMode", "custom")
    }

    property bool applyingPreset: false
    readonly property var streamPresetKeys: ["resolution", "fps", "maxBitrateMbps", "networkAdjust", "enableReflex", "colorQuality"]
    readonly property var streamingModes: [
        {value: "datasaver", label: qsTr("Data saver"), detail: qsTr("Uses less data. Good for metered or slower connections.")},
        {value: "balanced", label: qsTr("Balanced"), detail: qsTr("A good mix of image quality and smooth play for most connections.")},
        {value: "competitive", label: qsTr("Competitive"), detail: qsTr("The highest frame rate your membership allows, with Reflex, for the lowest latency.")},
        {value: "cinematic", label: qsTr("Cinematic"), detail: qsTr("The best image quality: higher resolution, higher bit rate and richer colour.")},
        {value: "custom", label: qsTr("Custom"), detail: qsTr("Adjust your streaming settings for a custom experience. Some combinations of values may cause connection issues.")}
    ]

    // Client-side bundles of individual settings; nothing named "mode" is sent.
    function streamingPreset(mode) {
        const fastest = (resolution, fallback) => {
            const rates = ShellStore.entitledFpsForResolution(resolution).map(Number).filter(rate => rate > 0)
            return rates.length ? Math.max(...rates) : fallback
        }
        const cinematicColor = ShellStore.settingsOwnerState.colorQualityGate("10bit_444") === ""
            && ShellStore.settingsOwnerState.colorQualityItems.some(item => item.value === "10bit_444" && !item.disabled)
            ? "10bit_444" : ShellStore.tenBitAllowedByMembership() ? "10bit_420" : "8bit_420"
        switch (mode) {
        case "datasaver": return {resolution: "1280x720", fps: 60, maxBitrateMbps: 15, networkAdjust: "latency", enableReflex: true, colorQuality: "8bit_420"}
        case "balanced": return {resolution: "1920x1080", fps: 60, maxBitrateMbps: 50, networkAdjust: "latency", enableReflex: true, colorQuality: "8bit_420"}
        case "competitive": return {resolution: "1920x1080", fps: fastest("1920x1080", 120), maxBitrateMbps: 75, networkAdjust: "latency", enableReflex: true, colorQuality: "8bit_420"}
        case "cinematic": return {resolution: "2560x1440", fps: 60, maxBitrateMbps: 100, networkAdjust: "quality", enableReflex: true, colorQuality: cinematicColor}
        default: return {resolution: "1920x1080", fps: 60, maxBitrateMbps: 75, networkAdjust: "off", enableReflex: true, colorQuality: "8bit_420"}
        }
    }

    function applyStreamingMode(mode) {
        root.applyingPreset = true
        const preset = root.streamingPreset(mode)
        if (preset.colorQuality !== "8bit_420" && ["h264", "av1"].indexOf(String(root.valueSetting("codec", "auto"))) >= 0)
            ShellStore.setSetting("codec", "auto")
        for (const key of root.streamPresetKeys) {
            if (preset[key] !== undefined && root.valueSetting(key, null) !== preset[key])
                root.setSetting(key, preset[key])
        }
        ShellStore.setSetting("streamingMode", mode)
        root.applyingPreset = false
    }

    // Resets the Details rows to the current mode's values (Custom resets to
    // Cloudlight's defaults).
    function resetStreamingDetails() {
        const mode = String(root.valueSetting("streamingMode", "custom"))
        root.applyStreamingMode(mode)
        root.applyingPreset = true
        if (mode === "custom") {
            for (const [key, value] of [["codec", "auto"], ["enableHdr", false], ["enableCloudGsync", false]])
                if (root.valueSetting(key, null) !== value) root.setSetting(key, value)
        }
        root.applyingPreset = false
    }

    // Typical data use, estimated from resolution, frame rate and codec (NVIDIA
    // publishes no formula). Uses the average, not the bit-rate cap.
    function dataUsageGbPerHour() {
        const size = String(root.valueSetting("resolution", "1920x1080")).split("x").map(Number)
        const fps = Number(root.valueSetting("fps", 60)) || 60
        const codec = String(root.valueSetting("codec", "auto"))
        const color = String(root.valueSetting("colorQuality", "8bit_420"))
        const bpp = codec === "h264" ? 0.18 : codec === "av1" ? 0.12 : 0.15
        let mbps = Math.min(Number(root.valueSetting("maxBitrateMbps", 75)) || 75,
                            (size[0] || 1920) * (size[1] || 1080) * fps * bpp / 1e6)
        mbps *= (color.endsWith("_444") ? 1.15 : 1) * (color.indexOf("10bit") === 0 ? 1.05 : 1)
        return Math.max(1, Math.round(mbps * 0.45))
    }

    function setChoice(key, value) {
        const currentQuality = String(root.valueSetting("colorQuality", "8bit_420"))
        root.setSetting(key, value)
        if (key === "colorQuality")
            tenBitWarning.notifySelection(currentQuality, value)
    }

    function choices(values) {
        return values.map(value => typeof value === "object" ? value : ({ kind: "choice", label: String(value), value: value }))
    }

    function colorQualityItems() {
        return ShellStore.settingsOwnerState.colorQualityItems
    }

    // Combinations the stream cannot honour as chosen. Each entry says what
    // actually happens, so nothing silently differs from the settings page.
    function compatibilityWarnings(area) {
        const warnings = []
        const push = (scope, text) => { if (!area || area === scope) warnings.push(text) }
        const codec = String(root.valueSetting("codec", "auto"))
        const color = String(root.valueSetting("colorQuality", "8bit_420"))
        const hdr = root.boolSetting("enableHdr", false)
        const tenBit = color.indexOf("10bit") === 0
        const chroma444 = color.endsWith("_444")
        if (codec === "h264" && (tenBit || chroma444))
            push("stream", qsTr("H.264 only carries 8-bit 4:2:0 colour, so the stream will use 8-bit 4:2:0. Choose H.265 for this colour precision."))
        else if (codec === "av1" && chroma444)
            push("stream", qsTr("AV1 streams don't support 4:4:4, so the stream will use 4:2:0. Choose H.265 for 4:4:4."))
        if (hdr && codec === "h264")
            push("stream", qsTr("HDR needs H.265 or AV1. With H.264 the stream will be SDR."))
        else if (hdr && ["software", "ffmpeg"].indexOf(String(root.valueSetting("nativeVideoBackend", "auto"))) >= 0)
            push("stream", qsTr("HDR needs hardware video decoding. With the software decoder the stream will be SDR."))
        else if (hdr && !tenBit)
            push("stream", qsTr("HDR streams always use 10-bit colour, whatever colour precision is set here."))
        if (hdr && Qt.platform.os !== "osx" && String(root.valueSetting("upscaling", "off")) !== "off")
            push("stream", qsTr("Upscaling is skipped while HDR is on."))
        const fps = Number(root.valueSetting("fps", 60))
        if (fps > 0 && root.lockedFpsValues().some(value => Number(root.optionValueOf(value)) === fps))
            push("stream", qsTr("%1 FPS isn't included in your membership at this resolution, so the stream will run at the highest rate your plan allows.").arg(fps))
        if (String(root.valueSetting("frameGeneration", "off")) === "2x") {
            if (fps > 60)
                push("experimental", qsTr("Frame generation pauses while the stream runs above 60 FPS."))
            if (hdr)
                push("experimental", qsTr("Frame generation doesn't run on HDR streams."))
        }
        return warnings
    }

    function optionValueOf(option) {
        return typeof option === "object" && option !== null && option.value !== undefined ? option.value : option
    }

    function colorQualityFooter() {
        const current = colorQualityItems().find(item => item.value === root.valueSetting("colorQuality", "8bit_420"))
        return current ? current.detail : ShellStore.settingsOwnerState.colorDescription
    }

    function liveTierBadge() {
        const tier = ShellStore.subscription ? String(ShellStore.subscription.membershipTier || "") : ""
        return tier === "" ? "" : tier.toUpperCase()
    }

    function profileUser() {
        return ShellStore.authSession && ShellStore.authSession.user ? ShellStore.authSession.user : ({})
    }

    function profileName() {
        return String(root.profileUser().displayName || qsTr("Guest"))
    }

    function profileInitial() {
        const name = root.profileName()
        return name.length ? name.charAt(0).toUpperCase() : "?"
    }

    function profileEmail() {
        return String(root.profileUser().email || "")
    }

    function profileSubtitle() {
        if (!ShellStore.signedIn)
            return qsTr("Not signed in")
        const email = root.profileEmail()
        if (email !== "")
            return qsTr("%1 · NVIDIA account linked").arg(email)
        return qsTr("NVIDIA account linked")
    }

    function maskedEmail() {
        const email = root.profileEmail()
        const at = email.indexOf("@")
        if (at <= 0)
            return ShellStore.signedIn ? qsTr("NVIDIA account") : qsTr("Not signed in")
        return email.charAt(0) + "•••••" + email.slice(at)
    }

    function regionGroup(name) {
        const n = String(name || "").trim().toLowerCase()
        if (/^(us|ca)\b|\b(usa|canada|north america)\b/.test(n))
            return qsTr("North America")
        if (/^(eu|uk|tr)\b|\b(europe|united kingdom|sweden|netherlands|germany|france|poland|bulgaria|turkey|türkiye|london|frankfurt|amsterdam)\b/.test(n))
            return qsTr("Europe")
        if (/^(jp|kr|sg|au|tw|my|th)\b|\b(asia|japan|korea|taiwan|malaysia|thailand|australia|new zealand|tokyo|seoul|singapore|sydney|india)\b/.test(n))
            return qsTr("Asia Pacific")
        if (/^br\b|\b(latam|south america|brazil|sao|são|chile|colombia|uruguay)\b/.test(n))
            return qsTr("South America")
        if (/^me\b|\b(middle east|uae|saudi|riyadh)\b/.test(n))
            return qsTr("Middle East")
        if (/\b(africa|johannesburg)\b/.test(n))
            return qsTr("Africa")
        return qsTr("Other")
    }

    function regionChoiceItems() {
        const items = [{ kind: "choice", label: qsTr("Automatic"), detail: qsTr("Lowest latency"), value: "" }]
        const groups = [qsTr("Europe"), qsTr("North America"), qsTr("Asia Pacific"),
                        qsTr("South America"), qsTr("Middle East"), qsTr("Africa"), qsTr("Other")]
        // Regions keep the service's own order inside each continent.
        const regions = ShellStore.regions || []
        for (const group of groups) {
            const members = regions.filter(region => root.regionGroup(region.name) === group)
            if (!members.length)
                continue
            items.push({ kind: "heading", label: group })
            for (const region of members) {
                const ping = root.regionPingMs(region.url)
                items.push({
                    kind: "choice",
                    label: region.name,
                    detail: ShellStore.regionPingBusy ? qsTr("Measuring…") : ping !== null ? (ping + " ms")
                        : ShellStore.regionPingResults[region.url] === null ? qsTr("No response") : qsTr("Not measured"),
                    detailColor: ping === null ? "" : root.pingRankColor(ping),
                    value: region.url
                })
            }
        }
        return items
    }

    function currentRegionLabel() {
        const selected = String(valueSetting("region", ""))
        if (selected === "")
            return qsTr("Automatic")
        const regions = ShellStore.regions || []
        for (let i = 0; i < regions.length; ++i) {
            if (regions[i].name === selected || regions[i].url === selected) {
                const ping = ShellStore.regionPingResults ? ShellStore.regionPingResults[regions[i].url] : undefined
                const name = regions[i].name
                return ping === undefined || ping === null || ping === "" ? name : (name + " · " + ping + " ms")
            }
        }
        return selected
    }

    function regionPingMs(url) {
        const raw = ShellStore.regionPingResults ? ShellStore.regionPingResults[url] : undefined
        const n = Number(raw)
        return raw === undefined || raw === null || raw === "" || !Number.isFinite(n) ? null : n
    }

    function currentRegionPing() {
        const selected = String(root.valueSetting("region", ""))
        if (selected === "") {
            const measured = (ShellStore.regions || []).map(region => root.regionPingMs(region.url))
                .filter(ping => ping !== null)
            return measured.length ? Math.min.apply(null, measured) : null
        }
        const regions = ShellStore.regions || []
        for (let i = 0; i < regions.length; ++i) {
            if (regions[i].name === selected || regions[i].url === selected)
                return root.regionPingMs(regions[i].url)
        }
        return null
    }

    // Ping quality ramp mirrors Electron's region picker: green <30,
    // lime <80, amber <150, red beyond. Unmeasured regions stay muted.
    function pingRankColor(ms) {
        if (ms === null || ms === undefined)
            return Theme.textMuted
        if (ms < 30)
            return "#58d98a"
        if (ms < 80)
            return "#84cc16"
        if (ms < 150)
            return "#eab308"
        return "#ef4444"
    }

    function pingBarsLit(ms) {
        if (ms === null || ms === undefined)
            return 0
        if (ms < 80)
            return 3
        if (ms < 150)
            return 2
        return 1
    }

    function themeMeta(id) {
        const packs = {
            cloudlight: { name: qsTr("Cloudlight"), blurb: qsTr("Black and pearl white with a soft lilac cast."), accent: "#ECE6F5" },
            aurora: { name: qsTr("Aurora"), blurb: qsTr("Cool teal shell with a mint focus ring."), accent: "#56E6A5" },
            nocturne: { name: qsTr("Nocturne"), blurb: qsTr("Near-black nocturne shell with a sky focus ring."), accent: "#7FD4FF" },
            kraft: { name: qsTr("Kraft"), blurb: qsTr("Warm brown shell with a brass focus ring."), accent: "#C6A46A" },
            phosphor: { name: qsTr("Mint"), blurb: qsTr("Deep green shell with a phosphor focus ring."), accent: "#56E6A5" },
            hibiscus: { name: qsTr("Sunset"), blurb: qsTr("Wine shell with a rose focus ring."), accent: "#FF8A80" },
            chapel: { name: qsTr("Chapel"), blurb: qsTr("Violet night shell with a gold focus ring."), accent: "#FFD166" },
            bone: { name: qsTr("Bone"), blurb: qsTr("Light paper shell for daytime use."), accent: "#C6A46A" },
            cobalt: { name: qsTr("Cobalt"), blurb: qsTr("Light blue-white shell with a cobalt focus ring."), accent: "#7FD4FF" }
        }
        return packs[id] || { name: String(id || qsTr("Theme")), blurb: qsTr("Installed theme pack.") }
    }

    function planChips() {
        const sub = ShellStore.subscription
        if (!sub)
            return [ShellStore.signedIn ? qsTr("Entitlements unavailable") : qsTr("Sign in to load entitlements")]
        const chips = []
        const resolutions = sub.entitledResolutions || []
        for (let i = 0; i < Math.min(4, resolutions.length); ++i) {
            const item = resolutions[i]
            const width = Number(item.width || 0)
            const height = Number(item.height || 0)
            const fps = Number(item.fps || 0)
            if (width >= 3840)
                chips.push("4K" + (fps ? " · " + fps + " FPS" : ""))
            else if (width >= 2560)
                chips.push("1440P" + (fps ? " · " + fps + " FPS" : ""))
            else if (width >= 1920)
                chips.push("1080P" + (fps ? " · " + fps + " FPS" : ""))
            else if (height > 0)
                chips.push(height + "P" + (fps ? " · " + fps + " FPS" : ""))
        }
        if (sub.isUnlimited)
            chips.push(qsTr("Unlimited"))
        else if (sub.remainingHours !== undefined && sub.remainingHours !== null)
            chips.push(qsTr("%1 h left").arg(Math.max(0, Math.round(Number(sub.remainingHours)))))
        if (chips.length === 0)
            chips.push(sub.membershipTier ? String(sub.membershipTier).charAt(0).toUpperCase() + String(sub.membershipTier).slice(1).toLowerCase() : qsTr("Unavailable"))
        return chips
    }

    // Frame-rate helpers share ShellStore's entitlement logic (mirroring
    // Electron's getFpsForResolution); these are display wrappers only.
    function fpsEntitlementKnown() {
        return Boolean(ShellStore.subscription)
    }

    function currentResolutionValue() {
        return String(root.valueSetting("resolution", "1920x1080"))
    }

    function unentitledFpsValues() {
        return ShellStore.unentitledFpsValues(root.currentResolutionValue())
    }

    function lockedFpsValues() {
        return ShellStore.lockedFpsValues(root.currentResolutionValue())
    }

    function fpsEntitlementNote() {
        if (!root.fpsEntitlementKnown())
            return ShellStore.signedIn
                ? qsTr("Loading your membership entitlements…")
                : qsTr("Sign in to unlock rates beyond 60 FPS")
        const entitled = ShellStore.entitledFpsForResolution(root.currentResolutionValue())
        const tier = root.liveTierBadge() || qsTr("Membership")
        if (entitled.length === 0)
            return qsTr("%1 · no exact entitlement for this resolution").arg(tier)
        const selectable = ShellStore.selectableFpsValues(root.currentResolutionValue())
        const max = selectable.length ? selectable[selectable.length - 1] : entitled[entitled.length - 1]
        return qsTr("%1 · up to %2 FPS at %3").arg(tier).arg(max)
            .arg(root.currentResolutionValue().replace("x", "×"))
    }

    function fpsLockedHint(value) {
        // Per rate: the membership text for rates the plan does not include,
        // the device reason for the rest.
        if (value !== undefined) {
            const unentitled = root.unentitledFpsValues().map(item => String(root.optionValueOf(item)))
            if (unentitled.indexOf(String(value)) < 0) {
                const reason = ShellStore.lockedFpsReason()
                if (reason !== "") return reason
            }
        } else if (!root.unentitledFpsValues().length) {
            const reason = ShellStore.lockedFpsReason()
            if (reason !== "")
                return reason
        } else if (root.lockedFpsValues().length > root.unentitledFpsValues().length) {
            const reason = ShellStore.lockedFpsReason()
            if (reason !== "")
                return reason
        }
        const tier = root.liveTierBadge()
        return tier
            ? qsTr("Not included in %1").arg(tier.charAt(0) + tier.slice(1).toLowerCase())
            : qsTr("Not included in your membership")
    }

    function clampFpsToEntitlement() {
        ShellStore.clampFpsToEntitlement()
    }

    function storeLetter(account) {
        const provider = String(account.provider || account.label || "").trim()
        return provider ? provider.charAt(0).toUpperCase() : ""
    }

    function storeIcon(account) {
        return DesktopTokens.storeIconUrl(account.provider || account.label)
            || "qrc:/qt/qml/OpenNOW/res/icons/desktop-nav-store.svg"
    }

    function storeAccent(account) {
        const provider = String(account.provider || account.label || "").toLowerCase()
        if (provider.indexOf("steam") >= 0) return Theme.cartSteam
        if (provider.indexOf("epic") >= 0) return Theme.cartEpic
        if (provider.indexOf("ubisoft") >= 0 || provider.indexOf("uplay") >= 0) return Theme.cartUbisoft
        if (provider.indexOf("xbox") >= 0) return Theme.cartXbox
        if (provider.indexOf("gog") >= 0) return Theme.cartGog
        if (provider.indexOf("battle") >= 0) return Theme.cartBattlenet
        return Theme.surfaceStrong
    }

    function storeStatus(account) {
        const action = ShellStore.gameAccountAction(account)
        if (account.status === "expired")
            return { text: qsTr("Expired"), color: Theme.yellow, action: qsTr("Reconnect"), connected: false, primary: true }
        if (account.status === "sync_error")
            return { text: qsTr("Sync problem"), color: Theme.coral, action: action === "link" ? qsTr("Reconnect") : qsTr("Sync library"), connected: true }
        if (account.isConnected || account.status === "connected")
            return { text: qsTr("Connected"), color: DesktopTokens.green, action: account.supportsSync ? qsTr("Sync") : qsTr("Disconnect"), connected: true }
        return { text: qsTr("Not connected"), color: Theme.textMuted, action: action === "sync" ? qsTr("Sync library") : qsTr("Connect"), connected: false }
    }

    function storeDescription(account) {
        if (account.capabilitySource === "fallback" || account.capabilitySource === "stale")
            return qsTr("Store capabilities could not be refreshed. Retry before changing this connection.")
        if (account.status === "sync_error") {
            if (account.provider === "STEAM" && account.syncState === "SYNC_DENIED") return qsTr("Make your store profile and game library public, then sync again.")
            if (account.syncState === "PROFILE_NOT_CREATED") return qsTr("Create your store profile, then sync again.")
            if (account.syncState === "SYNC_DENIED") return qsTr("Store authorization was denied. Reconnect this account.")
            return qsTr("The store reported a sync error: %1").arg(account.syncState || qsTr("Unknown"))
        }
        const subscriptions = ShellStore.storeSubscriptionLabels(account)
        if (subscriptions) return qsTr("Active store subscriptions: %1").arg(subscriptions)
        if (account.displayName)
            return account.displayName
        if (account.isConnected && account.syncedGames !== undefined && account.syncedGames !== null)
            return qsTr("%1 games reported by the last store sync").arg(account.syncedGames)
        if (account.isConnected)
            return qsTr("Connected through your NVIDIA account")
        return qsTr("Link this store on NVIDIA to add its games to your library")
    }

    function runStoreAction(account) {
        const action = ShellStore.gameAccountAction(account)
        if (action === "sync")
            ShellStore.syncGameAccount(account.provider)
        else if (action === "unlink")
            ShellStore.unlinkGameAccount(account.provider)
        else if (action === "link")
            ShellStore.startAccountLink(account.provider)
    }

    function projectLinks() {
        return [
            {id: "diagnostics", label: qsTr("Copy diagnostics"), hint: "NO PERSONAL DATA"},
            {id: "source", label: qsTr("Source on GitHub"), hint: "↗"}
        ]
    }

    function runProjectLink(link) {
        if (!link)
            return
        if (link.id === "source")
            AppController.openExternalUrl("https://github.com/miirys/OpenNOW")
        else if (link.id === "diagnostics")
            ShellStore.exportDiagnostics()
    }

    function resolutionItems() {
        return ShellStore.resolutionItems()
    }

    Rectangle {
        // Navigation column background, full height, like GeForce NOW's settings list.
        visible: !root.compactNavigation
        x: 0; y: 0
        width: settingsRail.x + settingsRail.width + DesktopTokens.px(16)
        height: root.height
        color: Theme.surface
    }

    Column {
        id: settingsRail
        x: DesktopTokens.px(20)
        y: DesktopTokens.px(28)
        width: root.compactNavigation ? root.width - x * 2 : DesktopTokens.px(240)
        spacing: DesktopTokens.px(14)

        Text {
            visible: !root.compactNavigation
            leftPadding: DesktopTokens.px(10)
            bottomPadding: DesktopTokens.px(2)
            text: qsTr("Settings")
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.px(26)
            font.weight: Font.DemiBold
        }

        TextField {
            id: settingsSearch
            objectName: "settingsSearch"
            width: parent.width
            height: DesktopTokens.px(36)
            placeholderText: qsTr("Search settings")
            text: root.searchQuery
            onTextEdited: root.searchQuery = text
            color: Theme.label
            placeholderTextColor: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(14)
            leftPadding: DesktopTokens.px(34)
            DesktopGlyph { x: DesktopTokens.px(12); anchors.verticalCenter: parent.verticalCenter; width: DesktopTokens.px(14); height: width; icon: "desktop-search.svg" }
            background: Rectangle { radius: DesktopTokens.px(10); color: settingsSearch.activeFocus ? Theme.surfaceHover : Theme.surfaceRaised; Behavior on color { ColorAnimation { duration: 140 } } }
            onAccepted: {
                if (root.searchResults.length) { root.openSearchResult(root.searchResults[0]); return }
                for (let i = 0; i < root.sections.length; ++i) {
                    if (root.matchesSection(root.sections[i])) {
                        root.selectedSection = root.sections[i].page
                        break
                    }
                }
            }
        }
        Flickable {
            width: parent.width
            height: root.compactNavigation ? navigation.implicitHeight : Math.max(0, root.height - y - settingsRail.y - DesktopTokens.px(16))
            contentWidth: width
            contentHeight: navigation.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            // One selection pill that springs between entries instead of each entry
            // switching its own fill, so changing section reads as a move.
            Rectangle {
                id: selectionPill
                objectName: "settingsSelectionPill"
                readonly property Item target: navRepeater.count > root.selectedGroup && root.selectedGroup >= 0
                    ? navRepeater.itemAt(root.selectedGroup) : null
                visible: target !== null && target.visible
                x: target ? target.x : 0
                y: target ? target.y : 0
                width: target ? target.width : 0
                height: target ? target.height : 0
                radius: DesktopTokens.px(10)
                color: Theme.focus
                Behavior on y { enabled: !AppController.reducedMotion; NumberAnimation { duration: Theme.springDuration * 0.75; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
                Behavior on x { enabled: !AppController.reducedMotion; NumberAnimation { duration: Theme.springDuration * 0.75; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
                Behavior on width { enabled: !AppController.reducedMotion; NumberAnimation { duration: Theme.springDuration * 0.75; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
            }
            Flow {
                id: navigation
                width: parent.width
                height: implicitHeight
                spacing: DesktopTokens.px(root.compactNavigation ? 4 : 2)
                Repeater {
                    id: navRepeater
                    model: root.sections
                    delegate: Button {
                        id: navItem
                        required property var modelData
                        required property int index
                        readonly property bool current: root.selectedGroup === index
                        objectName: "settingsNavigation-" + modelData.page
                        Accessible.name: modelData.label
                        visible: root.matchesSection(modelData)
                        width: root.compactNavigation ? navLabel.implicitWidth + DesktopTokens.px(28) : settingsRail.width
                        height: DesktopTokens.px(38)
                        padding: 0
                        hoverEnabled: true
                        onClicked: root.selectedSection = modelData.page
                        // macOS System Settings style: a small icon tile and a
                        // filled selection, no edge bars or outlines.
                        background: Rectangle {
                            radius: DesktopTokens.px(10)
                            color: !navItem.current && navItem.hovered ? DesktopTokens.hover : "transparent"
                            border.width: navItem.activeFocus && AppController.inputMode !== "pointer" ? 2 : 0
                            border.color: Theme.label
                            Behavior on color { ColorAnimation { duration: 140 } }
                        }
                        contentItem: Item {
                            implicitWidth: navLabel.implicitWidth
                            Item {
                                id: navTile
                                visible: !root.compactNavigation
                                x: DesktopTokens.px(10)
                                anchors.verticalCenter: parent.verticalCenter
                                width: DesktopTokens.px(22); height: width
                                DesktopSettingsIcon {
                                    anchors.centerIn: parent
                                    width: DesktopTokens.px(18); height: width
                                    glyph: navItem.modelData.icon
                                    ink: navItem.current ? Theme.focusText : Theme.label
                                }
                            }
                            Text {
                                id: navLabel
                                anchors.fill: parent
                                leftPadding: root.compactNavigation ? DesktopTokens.px(14) : DesktopTokens.px(42)
                                rightPadding: DesktopTokens.px(14)
                                verticalAlignment: Text.AlignVCenter
                                text: navItem.modelData.label
                                color: navItem.current ? Theme.focusText : Theme.label
                                font.family: Theme.bodyFont
                                font.pixelSize: DesktopTokens.px(14)
                                font.weight: navItem.current ? Font.DemiBold : Font.Medium
                                elide: Text.ElideRight
                            }
                        }
                    }
                }
            }
        }
    }

    Item {
        id: contentLane
        x: root.compactNavigation ? settingsRail.x : settingsRail.x + settingsRail.width + DesktopTokens.px(56)
        y: root.compactNavigation ? settingsRail.y + settingsRail.height + DesktopTokens.px(16) : settingsRail.y
        // A readable measure: rows stay close enough that a label and its control
        // read as one line, as on GeForce NOW.
        width: Math.min(DesktopTokens.px(780), root.width - x - DesktopTokens.px(40))
        height: root.height - y
        Text {
            id: pageTitle
            objectName: "settingsPageTitle"
            visible: !root.compactNavigation
            width: parent.width
            text: root.pageTitles[root.selectedSection] || ""
            opacity: searchResultsView.visible ? 0 : 1
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.px(26)
            font.weight: Font.DemiBold
        }
        Flickable {
            id: contentFlick
            objectName: "desktopSettingsContent"
            anchors.fill: parent
            visible: !searchResultsView.visible
            anchors.topMargin: pageTitle.visible ? pageTitle.height + DesktopTokens.px(8) : 0
            contentWidth: width
            contentHeight: pageLoader.height + DesktopTokens.px(48)
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
            Loader {
                id: pageLoader
                objectName: "settingsPageLoader"
                width: contentFlick.width
                sourceComponent: SmokeTestMode && root.acceptancePanel !== ""
                    ? root.acceptancePanels[root.acceptancePanel] : root.pageComponents[root.selectedSection]
                opacity: sectionEntrance.pageOpacity
                scale: sectionEntrance.pageScale
                transformOrigin: Item.Top
                PageEntrance { id: sectionEntrance; objectName: "settingsPageEntrance" }
                onLoaded: {
                    contentFlick.contentY = 0
                    sectionEntrance.restart()
                    if (root.pendingReveal !== "") Qt.callLater(root.revealPendingSetting)
                }
            }
        }
        // Search results replace the page while a query is typed, like GeForce NOW.
        Rectangle {
            id: searchResultsView
            objectName: "settingsSearchResults"
            visible: root.searchQuery.trim() !== ""
            anchors.fill: contentFlick
            anchors.topMargin: -(pageTitle.visible ? pageTitle.height + DesktopTokens.px(4) : 0)
            color: "transparent"
            Text {
                id: resultsHeading
                text: root.searchResults.length
                    ? qsTr("Results for \u201c%1\u201d").arg(root.searchQuery.trim())
                    : qsTr("No settings match \u201c%1\u201d").arg(root.searchQuery.trim())
                color: Theme.label
                font.family: Theme.displayFont
                font.pixelSize: DesktopTokens.px(28)
                font.weight: Font.Bold
                width: parent.width
                elide: Text.ElideRight
            }
            ListView {
                id: resultsList
                anchors.fill: parent
                anchors.topMargin: resultsHeading.height + DesktopTokens.px(16)
                clip: true
                spacing: DesktopTokens.px(4)
                boundsBehavior: Flickable.StopAtBounds
                model: root.searchResults
                ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
                delegate: AbstractButton {
                    id: result
                    required property var modelData
                    objectName: "settingsSearchResult-" + modelData.title
                    width: resultsList.width
                    height: DesktopTokens.px(modelData.detail ? 62 : 46)
                    hoverEnabled: true
                    Accessible.name: modelData.section + ", " + modelData.title
                    onClicked: root.openSearchResult(modelData)
                    Keys.onReturnPressed: event => { result.clicked(); event.accepted = true }
                    background: Rectangle {
                        radius: DesktopTokens.px(10)
                        color: result.hovered || result.activeFocus ? DesktopTokens.hover : Qt.rgba(Theme.surface.r, Theme.surface.g, Theme.surface.b, 0.6)
                        Behavior on color { ColorAnimation { duration: 140 } }
                    }
                    Column {
                        anchors.verticalCenter: parent.verticalCenter
                        x: DesktopTokens.px(16); width: parent.width - DesktopTokens.px(32) - sectionTag.width
                        spacing: DesktopTokens.px(3)
                        Text { width: parent.width; text: result.modelData.title; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(15); font.weight: Font.DemiBold; elide: Text.ElideRight }
                        Text { visible: text !== ""; width: parent.width; text: result.modelData.detail; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12); elide: Text.ElideRight }
                    }
                    Text {
                        id: sectionTag
                        anchors.right: parent.right; anchors.rightMargin: DesktopTokens.px(16)
                        anchors.verticalCenter: parent.verticalCenter
                        text: result.modelData.section
                        color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12)
                    }
                }
            }
        }
    }



    Connections {
        target: ShellStore
        function onSubscriptionChanged() { root.clampFpsToEntitlement() }
    }

    Component {
        id: accountGroup
        DesktopSettingsAccountPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
            profilePageComponent: profilePage
            subscriptionPageComponent: subscriptionPage
            storesPageComponent: storesPage
        }
    }
    Component {
        id: controlsGroup
        DesktopSettingsControlsPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
            controllersPageComponent: controllersPage
            shortcutsPageComponent: shortcutsPage
        }
    }

    Component {
        id: lookGroup
        DesktopSettingsLookPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
            interfacePageComponent: interfacePage
        }
    }

    Component {
        id: statsSettingsPage
        DesktopSettingsStatsPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: profilePage
        DesktopSettingsProfilePage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: subscriptionPage
        DesktopSettingsSubscriptionPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: storesPage
        DesktopSettingsStoresPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: streamPage
        DesktopSettingsStreamPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
            statsSettingsPageComponent: statsSettingsPage
        }
    }

    Component {
        id: audioPage
        DesktopSettingsAudioPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: controllersPage
        DesktopSettingsControllersPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: networkPage
        DesktopSettingsNetworkPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: interfacePage
        DesktopSettingsInterfacePage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }


    Component {
        id: consolePage
        DesktopSettingsConsolePage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: shortcutsPage
        DesktopSettingsShortcutsPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: recordingPage
        DesktopSettingsRecordingPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: experimentalPage
        DesktopSettingsExperimentalPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

    Component {
        id: aboutPage
        DesktopSettingsAboutPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

}
