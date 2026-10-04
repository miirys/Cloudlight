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
        {label: qsTr("Account"), detail: qsTr("Profile, membership, game stores"), icon: "person", page: 0, keywords: "profile subscription membership stores steam epic xbox ubisoft battle gaijin privacy connections"},
        {label: qsTr("Streaming quality"), detail: qsTr("Resolution, frame rate, bit rate"), icon: "monitor", page: 3, keywords: "resolution fps frame rate hdr color precision stats overlay timer bitrate bit rate codec g-sync gsync vrr backend gpu directx vulkan steam big picture launch gamepad fullscreen session ready persistent in-game graphics settings background reminder afk taskbar upscaling"},
        {label: qsTr("Server location"), detail: qsTr("Region, network test, proxy"), icon: "globe", page: 6, keywords: "server region location ping network test proxy l4s"},
        {label: qsTr("Audio"), detail: qsTr("Output and microphone"), icon: "wave", page: 4, keywords: "sound audio volume output microphone mute focus"},
        {label: qsTr("Controls"), detail: qsTr("Controllers, mouse, keyboard, shortcuts"), icon: "controller", page: 5, keywords: "controller gyroscope steam sensitivity mouse keyboard language shortcuts"},
        {label: qsTr("Capture"), detail: qsTr("Screenshots, recording, replay"), icon: "image", page: 12, keywords: "recording capture clip replay buffer memory duration folder resolution fps quality shortcuts F12 screenshot"},
        {label: qsTr("Interface"), detail: qsTr("Theme, accent colour, language"), icon: "palette", page: 8, keywords: "theme accent color colour interface language scale motion sidebar tiles appearance"},
        {label: qsTr("Console mode"), detail: qsTr("Controller-first full-screen interface"), icon: "controller", page: 9, keywords: "console fullscreen gamepad startup big screen tv"},
        {label: qsTr("About"), detail: qsTr("Version, updates, diagnostics"), icon: "info", page: 11, keywords: "version release update diagnostics onboarding introduction replay setup restart reset help support"}
    ]
    readonly property var pageTitles: [qsTr("Account"), qsTr("Account"), qsTr("Account"), qsTr("Streaming quality"), qsTr("Audio"), qsTr("Controls"), qsTr("Server location"), qsTr("Interface"), qsTr("Interface"), qsTr("Console mode"), qsTr("Controls"), qsTr("About"), qsTr("Capture")]
    readonly property var pageComponents: [accountGroup, accountGroup, accountGroup, streamPage, audioPage, controlsGroup, networkPage, lookGroup, lookGroup, consolePage, controlsGroup, aboutPage, recordingPage]

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
        const regions = (ShellStore.regions || []).slice().sort((a, b) => String(a.name).localeCompare(String(b.name)))
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

    function fpsLockedHint() {
        if (!root.unentitledFpsValues().length) {
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
            ? qsTr("Not entitled on %1 — upgrade on NVIDIA to unlock").arg(tier)
            : qsTr("Not entitled on your current membership")
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
            {id: "issues", label: qsTr("Report an issue"), hint: "↗"},
            {id: "source", label: qsTr("Source on GitHub"), hint: "↗"}
        ]
    }

    function runProjectLink(link) {
        if (!link)
            return
        if (link.id === "source")
            AppController.openExternalUrl("https://github.com/OpenCloudGaming/OpenNOW")
        else if (link.id === "issues")
            AppController.openExternalUrl("https://github.com/OpenCloudGaming/OpenNOW/issues")
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
        Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: DesktopTokens.seamSoft }
    }

    Column {
        id: settingsRail
        x: DesktopTokens.px(16)
        y: DesktopTokens.px(20)
        width: root.compactNavigation ? root.width - x * 2 : DesktopTokens.px(232)
        spacing: DesktopTokens.px(12)

        Text {
            visible: !root.compactNavigation
            leftPadding: DesktopTokens.px(12)
            text: qsTr("Settings")
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.titleSize
            font.weight: Font.Bold
        }

        TextField {
            id: settingsSearch
            objectName: "settingsSearch"
            width: parent.width
            height: DesktopTokens.px(34)
            placeholderText: qsTr("Search settings")
            text: root.searchQuery
            onTextEdited: root.searchQuery = text
            color: Theme.label
            placeholderTextColor: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(13)
            leftPadding: DesktopTokens.px(34)
            DesktopGlyph { x: DesktopTokens.px(12); anchors.verticalCenter: parent.verticalCenter; width: DesktopTokens.px(14); height: width; icon: "desktop-search.svg" }
            background: Rectangle { radius: DesktopTokens.radius; color: Theme.surfaceRaised; border.width: 1; border.color: settingsSearch.activeFocus ? Theme.focus : Theme.seam }
            onAccepted: {
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
            Flow {
                id: navigation
                width: parent.width
                height: implicitHeight
                spacing: root.compactNavigation ? DesktopTokens.px(4) : 0
                Repeater {
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
                        background: Rectangle {
                            radius: DesktopTokens.radius
                            color: navItem.current ? Theme.surfaceRaised : navItem.hovered ? DesktopTokens.hover : "transparent"
                            border.width: navItem.activeFocus ? 2 : 0
                            border.color: Theme.focus
                            Rectangle {
                                visible: navItem.current
                                x: 0; y: DesktopTokens.px(8)
                                width: DesktopTokens.px(3); height: parent.height - DesktopTokens.px(16)
                                color: Theme.focus
                            }
                        }
                        contentItem: Text {
                            id: navLabel
                            leftPadding: DesktopTokens.px(14)
                            rightPadding: DesktopTokens.px(14)
                            verticalAlignment: Text.AlignVCenter
                            text: navItem.modelData.label
                            color: navItem.current ? Theme.label : DesktopTokens.textBody
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

    Item {
        id: contentLane
        x: root.compactNavigation ? settingsRail.x : settingsRail.x + settingsRail.width + DesktopTokens.px(48)
        y: root.compactNavigation ? settingsRail.y + settingsRail.height + DesktopTokens.px(16) : DesktopTokens.px(20)
        width: Math.min(DesktopTokens.px(860), root.width - x - DesktopTokens.px(32))
        height: root.height - y
        Text {
            id: pageTitle
            objectName: "settingsPageTitle"
            visible: !root.compactNavigation
            width: parent.width
            text: root.pageTitles[root.selectedSection] || ""
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.titleSize
            font.weight: Font.Bold
        }
        Flickable {
            id: contentFlick
            objectName: "desktopSettingsContent"
            anchors.fill: parent
            anchors.topMargin: pageTitle.visible ? pageTitle.height + DesktopTokens.px(4) : 0
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
                transform: Translate { y: sectionEntrance.offset }
                PageEntrance { id: sectionEntrance; objectName: "settingsPageEntrance" }
                onLoaded: { contentFlick.contentY = 0; sectionEntrance.restart() }
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
        id: aboutPage
        DesktopSettingsAboutPage {
            availableWidth: contentFlick.width - DesktopTokens.px(16)
            settingsScreen: root
        }
    }

}
