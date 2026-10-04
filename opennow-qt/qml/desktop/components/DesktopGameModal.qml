import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import OpenNOW

FocusScope {
    id: root
    property var game: ShellStore.selectedGame
    signal closeRequested()
    signal playRequested()
    signal variantSelected(int index)
    objectName: "desktopGameModal"
    property bool opened: false
    visible: reveal.present
    enabled: opened
    focus: opened
    onOpenedChanged: if (opened) Qt.callLater(() => { if (root.opened) primaryAction.forceActiveFocus() })
    MotionProgress { id: reveal; objectName: "gameDetailsMotion"; shown: root.opened }

    readonly property var streamSettings: ShellStore.settings || ({})
    readonly property var selectedVariant: {
        const game = root.game
        if (!game)
            return null
        const variants = game.variants || []
        if (!variants.length)
            return null
        const index = Number(game.selectedVariantIndex || 0)
        return index >= 0 && index < variants.length ? variants[index] : null
    }
    readonly property bool gameAvailable: {
        const game = root.game
        if (!game)
            return false
        return ShellStore.selectedLaunchDecision.status === "ready"
    }
    readonly property bool hasRtx: {
        const game = root.game
        if (!game)
            return false
        const parts = [String(game.title || "")]
        const lists = [game.genres, game.nvidiaTech, game.featureLabels]
        for (let i = 0; i < lists.length; ++i) {
            const list = lists[i] || []
            for (let j = 0; j < list.length; ++j)
                parts.push(String(list[j]))
        }
        const skuTags = game.catalogSkuStrings && game.catalogSkuStrings.SKU_BASED_TAG
        if (skuTags) {
            for (let k = 0; k < skuTags.length; ++k)
                parts.push(String(skuTags[k]))
        }
        return parts.join(" ").toLowerCase().indexOf("rtx") >= 0
    }
    readonly property string lastPlayedText: {
        const game = root.game
        if (!game)
            return qsTr("—")
        if (game.lastPlayedLabel)
            return String(game.lastPlayedLabel)
        const raw = String(game.lastPlayed || (root.selectedVariant && root.selectedVariant.lastPlayedDate) || "")
        if (!raw)
            return qsTr("Not played yet")
        return DesktopTokens.relativeLastPlayed(raw, Date.now()) || qsTr("—")
    }
    readonly property string storesText: {
        const game = root.game
        if (!game)
            return qsTr("—")
        const fromStores = game.availableStores || []
        const stores = fromStores.length
            ? fromStores
            : (game.variants || []).map(variant => variant && variant.store).filter(Boolean)
        return stores.length ? stores.join(" · ") : qsTr("—")
    }
    readonly property bool isOwned: root.selectedVariant
        && ["MANUAL", "PLATFORM_SYNC"].indexOf(root.selectedVariant.libraryStatus) >= 0
    readonly property string ownershipText: {
        const game = root.game
        const variant = root.selectedVariant
        const store = variant && variant.store
            ? String(variant.store)
            : ((game && game.availableStores && game.availableStores[0]) || "")
        if (store && root.isOwned)
            return qsTr("Owned on %1").arg(store.toUpperCase())
        if (store)
            return store.toUpperCase()
        return root.isOwned ? qsTr("In library") : qsTr("Not owned")
    }
    readonly property string membershipText: {
        const sub = ShellStore.subscription
        if (sub && sub.membershipTier)
            return String(sub.membershipTier).toUpperCase()
        const user = ShellStore.authSession && ShellStore.authSession.user
        if (user && user.membershipTier)
            return String(user.membershipTier).toUpperCase()
        return ""
    }
    readonly property string resolutionText: {
        const raw = String(root.streamSettings.resolution || "")
        if (raw.indexOf("x") > 0) {
            const height = Number(raw.split("x")[1])
            if (height >= 2160)
                return "4K"
            if (height > 0)
                return height + "p"
        }
        return raw || qsTr("Auto")
    }
    readonly property string fpsText: {
        const fps = Number(root.streamSettings.fps || 0)
        return fps > 0 ? qsTr("%1 fps").arg(fps) : qsTr("Auto")
    }
    readonly property string codecText: {
        const codec = String(root.streamSettings.codec || "")
        if (!codec || codec.toLowerCase() === "auto")
            return qsTr("Auto")
        return codec.toUpperCase()
    }
    readonly property string regionText: {
        const region = String(root.streamSettings.region || "")
        return region ? region.toUpperCase() : qsTr("Automatic region")
    }
    readonly property string friendsNote: {
        const caps = ShellStore.socialCapabilities || ({})
        if (!caps.friendsAvailable && caps.reason)
            return String(caps.reason)
        return qsTr("Friends activity is not available from GeForce NOW")
    }
    readonly property var badgeLabels: {
        const game = root.game
        const labels = []
        const seen = {}
        function add(label) {
            const text = String(label || "").trim()
            const key = text.toUpperCase()
            if (!text || seen[key])
                return
            seen[key] = true
            labels.push(text)
        }
        if (!game)
            return labels
        if (root.gameAvailable)
            add(qsTr("Ready to play"))
        else if (game.playabilityState)
            add(String(game.playabilityState).replace(/_/g, " "))
        const playType = String(game.playType || "").replace(/_/g, " ")
        if (playType && playType.toUpperCase() !== "READY TO PLAY")
            add(playType)
        const controls = game.supportedControls || []
        for (let i = 0; i < controls.length; ++i) {
            const control = String(controls[i] || "").toUpperCase()
            if (control === "GAMEPAD")
                add(qsTr("Controller"))
            else if (control === "KEYBOARD_MOUSE" || control === "KEYBOARD AND MOUSE")
                add(qsTr("Keyboard"))
            else if (control)
                add(control.replace(/_/g, " "))
        }
        if (root.hasRtx)
            add("RTX")
        if (game.membershipTierLabel)
            add(String(game.membershipTierLabel))
        return labels
    }
    readonly property var factItems: {
        const game = root.game || ({})
        const items = [{l: qsTr("Last played"), v: root.lastPlayedText}]
        if (game.hoursPlayed)
            items.push({l: qsTr("Hours played"), v: qsTr("%1 h").arg(game.hoursPlayed)})
        if (game.sessionCount)
            items.push({l: qsTr("Sessions"), v: String(game.sessionCount)})
        items.push({l: qsTr("Stores"), v: root.storesText})
        items.push({l: qsTr("Available"), v: root.gameAvailable ? qsTr("Yes") : qsTr("No")})
        return items
    }
    readonly property var streamRows: [
        {l: qsTr("Resolution"), v: root.resolutionText},
        {l: qsTr("Frame rate"), v: root.fpsText},
        {l: qsTr("Codec"), v: root.codecText}
    ]

    function revealFocusedControl() {
        if (!root.opened || !root.Window.window)
            return
        const item = root.Window.window.activeFocusItem
        let ancestor = item
        while (ancestor && ancestor !== detailsColumn)
            ancestor = ancestor.parent
        if (!ancestor)
            return
        const top = item.mapToItem(detailsFlick.contentItem, 0, 0).y
        const margin = DesktopTokens.px(8)
        const maximumY = Math.max(0, detailsFlick.contentHeight - detailsFlick.height)
        if (top < detailsFlick.contentY + margin)
            detailsFlick.contentY = Math.max(0, top - margin)
        else if (top + item.height > detailsFlick.contentY + detailsFlick.height - margin)
            detailsFlick.contentY = Math.min(maximumY, top + item.height + margin - detailsFlick.height)
    }
    Connections {
        target: root.Window.window
        function onActiveFocusItemChanged() { Qt.callLater(root.revealFocusedControl) }
    }

    function tune() { AppController.navigate("settings-streaming") }
    readonly property string colorText: {
        const raw = String(streamSettings.colorQuality || "8bit_420")
        const match = raw.match(/^(\d+)bit_(\d)(\d)(\d)$/)
        return match ? qsTr("%1-bit %2:%3:%4").arg(match[1]).arg(match[2]).arg(match[3]).arg(match[4]) : raw.replace(/_/g, " ")
    }
    // Plain-text facts about the stream this launch will ask for. Labels stay short so
    // the row reads at a distance; the detail line carries the secondary value.
    readonly property var summaryCards: [
        {label:qsTr("Stream"), title:resolutionText + " · " + fpsText, detail:codecText + " · " + colorText},
        {label:qsTr("Server"), title:regionLabel(), detail:qsTr("Region selected at launch")},
        {label:qsTr("Membership"), title:membershipText || qsTr("Membership"), detail:ShellStore.subscription && ShellStore.subscription.remainingHours !== undefined
            ? qsTr("%1 h remaining").arg(Math.max(0, Number(ShellStore.subscription.remainingHours)).toFixed(1)) : qsTr("Entitlements checked at launch")}
    ]
    function regionLabel() {
        const value = String(streamSettings.region || "")
        const regions = ShellStore.regions || []
        for (const region of regions)
            if (region.url === value || region.name === value) return String(region.name)
        return value ? qsTr("Selected region") : qsTr("Automatic region")
    }
    readonly property string eyebrowText: {
        const game = root.game
        if (!game)
            return ""
        const parts = []
        const maker = String(game.publisherName || game.publisher || game.developerName || "")
        if (maker)
            parts.push(maker)
        const genres = game.genres || []
        for (let i = 0; i < genres.length && i < 2; ++i)
            parts.push(String(genres[i]))
        return parts.join("  ·  ")
    }
    readonly property string factsText: {
        const parts = [root.lastPlayedText]
        if (root.game && root.game.hoursPlayed)
            parts.push(qsTr("%1 h played").arg(root.game.hoursPlayed))
        for (let i = 0; i < root.badgeLabels.length && i < 3; ++i)
            parts.push(root.badgeLabels[i])
        return parts.filter(Boolean).join("  ·  ")
    }
    readonly property string descriptionText: {
        const game = root.game
        if (!game)
            return ""
        return String(game.shortDescription || game.longDescription || game.description || "").replace(/<[^>]*>/g, "").trim()
    }

    // Full page, not a dialog: the game's art fills the screen and the details sit on
    // the left over a scrim, so the title and Play read from across the room.
    Rectangle {
        id: dialog
        objectName: "gameDetailsDialog"
        anchors.fill: parent
        color: Theme.shell
        opacity: reveal.progress
        transform: Translate { y: Math.round((1 - reveal.progress) * DesktopTokens.px(16)) }
        readonly property int gutter: Math.round(Math.max(DesktopTokens.px(24), Math.min(DesktopTokens.px(88), width * 0.055)))
        readonly property bool narrow: width < DesktopTokens.px(720)
        MouseArea {
            anchors.fill: parent; acceptedButtons: Qt.AllButtons
            hoverEnabled: true; preventStealing: true
            onWheel: wheel => wheel.accepted = true
        }

        Item {
            id: hero
            width: parent.width
            height: Math.round(parent.height * (dialog.narrow ? 0.62 : 0.86))
            RoundedArtwork {
                anchors.fill: parent; artwork: DesktopTokens.artworkUrl(root.game, true)
                cornerRadius: 0; scrimStart: 1; fallbackColor: Theme.shell
            }
            // Left scrim carries the text; the bottom fade hands the art over to the page.
            Rectangle {
                anchors.fill: parent
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: Qt.alpha(Theme.shell, 0.94) }
                    GradientStop { position: dialog.narrow ? 0.7 : 0.42; color: Qt.alpha(Theme.shell, 0.72) }
                    GradientStop { position: dialog.narrow ? 1 : 0.78; color: Qt.alpha(Theme.shell, 0) }
                }
            }
            Rectangle {
                anchors.fill: parent
                gradient: Gradient {
                    GradientStop { position: 0; color: Qt.alpha(Theme.shell, 0.55) }
                    GradientStop { position: 0.2; color: Qt.alpha(Theme.shell, 0) }
                    GradientStop { position: 0.55; color: Qt.alpha(Theme.shell, 0) }
                    GradientStop { position: 1; color: Theme.shell }
                }
            }
        }

        DesktopButton {
            id: backButton
            objectName: "gameDetailsClose"
            x: dialog.gutter - DesktopTokens.px(12)
            y: DesktopTokens.px(24)
            height: DesktopTokens.px(44)
            leftPadding: DesktopTokens.px(12); rightPadding: DesktopTokens.px(16)
            onMediaBackground: false
            themedGlyph: "back"; glyphSize: DesktopTokens.px(20)
            text: qsTr("Back")
            shortcutText: dialog.narrow ? "" : qsTr("Esc"); shortcutSequence: "Esc"
            background: Rectangle {
                radius: DesktopTokens.radius
                color: backButton.down ? DesktopTokens.raisedStrong : backButton.hovered ? DesktopTokens.raised : "transparent"
                Rectangle {
                    anchors.fill: parent; anchors.margins: -DesktopTokens.px(4)
                    radius: parent.radius + DesktopTokens.px(3)
                    color: "transparent"; border.width: DesktopTokens.px(3); border.color: Theme.label
                    visible: backButton.activeFocus
                }
            }
            Accessible.name: qsTr("Close details")
            onClicked: root.closeRequested()
        }

        Flickable {
            id: detailsFlick
            objectName: "gameDetailsScroll"
            anchors.top: backButton.bottom; anchors.topMargin: DesktopTokens.px(12)
            anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
            contentWidth: width; contentHeight: detailsColumn.implicitHeight
            clip: true; boundsBehavior: Flickable.StopAtBounds
            flickableDirection: Flickable.VerticalFlick
            onHeightChanged: Qt.callLater(root.revealFocusedControl)
            onContentHeightChanged: Qt.callLater(root.revealFocusedControl)
            ScrollBar.vertical: ScrollBar {
                policy: detailsFlick.contentHeight > detailsFlick.height ? ScrollBar.AlwaysOn : ScrollBar.AlwaysOff
            }
            Column {
                id: detailsColumn
                x: dialog.gutter
                width: Math.min(DesktopTokens.px(880), detailsFlick.width - dialog.gutter * 2)
                // Push the title down into the art when there is room, but never so far
                // that Play opens below the fold.
                Item {
                    width: parent.width
                    height: Math.round(Math.max(0, Math.min(detailsFlick.height * 0.34,
                        detailsFlick.height - titleBlock.implicitHeight - root.firstFoldHeight - DesktopTokens.px(24))))
                }
                Column {
                    id: titleBlock
                    objectName: "gameDetailsTitleBlock"
                    width: parent.width
                    spacing: DesktopTokens.px(10)
                    bottomPadding: DesktopTokens.px(28)
                    Text {
                        width: parent.width
                        visible: text !== ""
                        text: root.eyebrowText.toUpperCase()
                        color: Theme.textMuted
                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.captionSize
                        font.weight: Font.Bold; font.letterSpacing: DesktopTokens.px(1.5)
                        elide: Text.ElideRight
                    }
                    Text {
                        width: parent.width
                        text: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game")
                        color: DesktopTokens.textHigh; font.family: Theme.displayFont
                        font.pixelSize: dialog.narrow ? DesktopTokens.px(40) : DesktopTokens.px(60)
                        font.weight: Font.Bold
                        lineHeight: 0.95
                        wrapMode: Text.WordWrap; maximumLineCount: 2; elide: Text.ElideRight
                    }
                    Row {
                        width: parent.width
                        spacing: DesktopTokens.px(14)
                        Text {
                            id: ownedText
                            text: root.ownershipText
                            color: DesktopTokens.textHigh
                            font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.Bold
                        }
                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            width: 1; height: DesktopTokens.px(16)
                            color: DesktopTokens.seamSoft
                            visible: factsLine.text !== ""
                        }
                        Text {
                            id: factsLine
                            width: Math.max(0, parent.width - ownedText.width - DesktopTokens.px(28) - 1)
                            text: root.factsText
                            color: DesktopTokens.textBody
                            font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.bodySize
                            elide: Text.ElideRight
                        }
                    }
                    Text {
                        width: Math.min(parent.width, DesktopTokens.px(680))
                        visible: text !== ""
                        topPadding: DesktopTokens.px(6)
                        text: root.descriptionText
                        color: DesktopTokens.textBody
                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.bodySize
                        lineHeight: 1.25
                        wrapMode: Text.WordWrap; maximumLineCount: 3; elide: Text.ElideRight
                    }
                }
                Item {
                    width: parent.width
                    height: bodyContent.implicitHeight + DesktopTokens.px(24)
                    Column {
                        id: bodyContent
                        objectName: "gameDetailsBody"
                        width: parent.width
                        spacing: DesktopTokens.px(28)
                        Column {
                            id: readiness
                            objectName: "gameDetailsReadiness"
                            width: parent.width
                            spacing: DesktopTokens.px(10)
                            Text {
                                id: readinessNoticeLabel
                                objectName: "catalogReadinessNotice"
                                width: Math.min(parent.width, DesktopTokens.px(680))
                                text: I18n.source(ShellStore.cloudMutationMessage || ShellStore.selectedLaunchDecision.message || ShellStore.readinessNotice(root.game), I18n.revision)
                                visible: text !== ""
                                wrapMode: Text.WordWrap
                                color: ShellStore.cloudMutationState === "unconfirmed" ? (Theme.lightMode ? Qt.darker(DesktopTokens.danger, 2) : DesktopTokens.danger) : DesktopTokens.textBody
                                font.family: Theme.bodyFont
                                font.pixelSize: DesktopTokens.captionSize
                            }
                            visible: storeVariants.count > 1 || readinessNoticeLabel.text !== ""
                            Text {
                                text: qsTr("Play from")
                                visible: storeVariants.count > 1
                                color: Theme.textMuted
                                font.family: Theme.bodyFont
                                font.pixelSize: DesktopTokens.captionSize
                                font.weight: Font.Bold
                            }
                            Flow {
                                visible: storeVariants.count > 1
                                width: parent.width
                                spacing: DesktopTokens.px(8)
                                Repeater {
                                    id: storeVariants
                                    model: root.game ? root.game.variants || [] : []
                                    DesktopButton {
                                        id: platformButton
                                        required property var modelData
                                        required property int index
                                        objectName: "desktopStoreVariant" + index
                                        text: String(modelData.store || qsTr("Unknown"))
                                        height: DesktopTokens.px(36)
                                        leftPadding: DesktopTokens.px(12)
                                        rightPadding: DesktopTokens.px(14)
                                        font.pixelSize: DesktopTokens.captionSize
                                        implicitWidth: platformContents.implicitWidth + leftPadding + rightPadding
                                        checkable: true
                                        autoExclusive: true
                                        checked: root.game ? index === Number(root.game.selectedVariantIndex || 0) : false
                                        primary: checked
                                        Accessible.description: ["MANUAL", "PLATFORM_SYNC"].indexOf(modelData.libraryStatus) >= 0
                                            ? qsTr("Owned") : modelData.libraryStatus === "NOT_OWNED" ? qsTr("Not owned") : qsTr("Ownership unconfirmed")
                                        onClicked: root.variantSelected(index)
                                        Keys.onReturnPressed: root.variantSelected(index)
                                        Keys.onEnterPressed: root.variantSelected(index)
                                        contentItem: Row {
                                            id: platformContents
                                            spacing: DesktopTokens.px(8)
                                            Rectangle {
                                                anchors.verticalCenter: parent.verticalCenter
                                                width: DesktopTokens.px(26)
                                                height: width
                                                radius: DesktopTokens.px(5)
                                                visible: platformLogo.source.toString() !== ""
                                                color: platformButton.checked || Theme.lightMode ? "#202634" : "transparent"
                                                Image {
                                                    id: platformLogo
                                                    objectName: "desktopStoreLogo" + platformButton.index
                                                    anchors.centerIn: parent
                                                    width: DesktopTokens.px(20)
                                                    height: width
                                                    source: DesktopTokens.storeIconUrl(platformButton.modelData.store)
                                                    sourceSize: Qt.size(width * Screen.devicePixelRatio, height * Screen.devicePixelRatio)
                                                    fillMode: Image.PreserveAspectFit
                                                }
                                            }
                                            Text {
                                                anchors.verticalCenter: parent.verticalCenter
                                                text: platformButton.text
                                                font: platformButton.font
                                                color: platformButton.ink
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        RowLayout {
                            id: actionRow
                            objectName: "gameDetailsPrimaryActions"
                            width: parent.width
                            spacing: DesktopTokens.px(12)
                            DesktopButton {
                                id: primaryAction
                                objectName: "desktopGamePlay"
                                Layout.fillWidth: dialog.narrow; Layout.minimumWidth: 0
                                Layout.preferredWidth: dialog.narrow ? -1 : Math.max(DesktopTokens.px(320), implicitWidth)
                                Layout.preferredHeight: DesktopTokens.px(56)
                                font.pixelSize: DesktopTokens.headingSize; font.weight: Font.Bold
                                leftPadding: DesktopTokens.px(24); rightPadding: DesktopTokens.px(24)
                                glyphSize: DesktopTokens.px(22)
                                primary: true; themedGlyph: "play"; text: ShellStore.selectedGameActionLabel(); shortcutText: qsTr("Enter"); shortcutSequence: "Enter"
                                enabled: root.game !== null && !ShellStore.cloudMutationBusy && ShellStore.launchInspectRequestId === ""
                                onClicked: root.playRequested()
                            }
                            DesktopButton {
                                Layout.preferredWidth: DesktopTokens.px(56); Layout.preferredHeight: DesktopTokens.px(56)
                                themedGlyph: "star"; glyphSize: DesktopTokens.px(22); leftPadding: 0; rightPadding: 0
                                Accessible.name: root.game && ShellStore.isCloudFavorite(root.game) ? qsTr("Remove from GeForce NOW favorites") : qsTr("Add to GeForce NOW favorites")
                                ToolTip.visible: hovered; ToolTip.text: Accessible.name
                                enabled: ShellStore.signedIn && !ShellStore.cloudMutationBusy
                                onClicked: if (root.game) ShellStore.toggleCloudFavorite(root.game)
                            }
                            DesktopButton {
                                Layout.preferredWidth: DesktopTokens.px(56); Layout.preferredHeight: DesktopTokens.px(56)
                                themedGlyph: "folder"; glyphSize: DesktopTokens.px(22); leftPadding: 0; rightPadding: 0
                                Accessible.name: qsTr("Collections")
                                ToolTip.visible: hovered; ToolTip.text: Accessible.name
                                onClicked: collectionMenu.popup()
                                Menu {
                                    id: collectionMenu
                                    MenuItem { text: qsTr("Pin to Home"); checkable: true; checked: root.game && ShellStore.isFavorite(root.game); onTriggered: if (root.game) ShellStore.toggleFavorite(root.game) }
                                }
                            }
                            DesktopButton {
                                Layout.preferredWidth: DesktopTokens.px(56); Layout.preferredHeight: DesktopTokens.px(56)
                                themedGlyph: "more"; glyphSize: DesktopTokens.px(22); leftPadding: 0; rightPadding: 0
                                Accessible.name: qsTr("More game actions")
                                onClicked: moreMenu.popup()
                                Menu {
                                    id: moreMenu
                                    MenuItem { text: qsTr("Stream settings"); onTriggered: root.tune() }
                                    MenuItem { text: qsTr("Close details"); onTriggered: root.closeRequested() }
                                }
                            }
                            Item { Layout.fillWidth: !dialog.narrow; visible: !dialog.narrow }
                        }
                        CloudLibraryActions {
                            width: parent.width
                            game: root.game
                            showFavorites: false
                            showStatus: false
                        }
                        GridLayout {
                            id: summaryGrid
                            objectName: "gameDetailsSummary"
                            width: parent.width
                            columns: width < DesktopTokens.px(620) ? 2 : 4
                            uniformCellWidths: columns === 2
                            columnSpacing: DesktopTokens.px(28); rowSpacing: DesktopTokens.px(16)
                            Repeater {
                                model: root.summaryCards
                                delegate: Item {
                                    required property var modelData
                                    required property int index
                                    objectName: "gameDetailsSummaryCard"
                                    Layout.fillWidth: true; Layout.minimumWidth: 0
                                    Layout.preferredWidth: DesktopTokens.px(180)
                                    Layout.preferredHeight: factColumn.implicitHeight
                                    Rectangle {
                                        width: 1; height: parent.height
                                        x: -Math.round(summaryGrid.columnSpacing / 2)
                                        visible: index % summaryGrid.columns !== 0
                                        color: Theme.seam
                                    }
                                    Column {
                                        id: factColumn
                                        width: parent.width
                                        spacing: DesktopTokens.px(4)
                                        Text { width: parent.width; text: modelData.label.toUpperCase(); elide: Text.ElideRight; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.smallSize; font.weight: Font.Bold; font.letterSpacing: DesktopTokens.px(1.2) }
                                        Text { width: parent.width; text: modelData.title; elide: Text.ElideRight; color: DesktopTokens.textHigh; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.DemiBold }
                                        Text { width: parent.width; text: modelData.detail; elide: Text.ElideRight; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.captionSize }
                                    }
                                }
                            }
                            DesktopButton {
                                objectName: "gameDetailsTune"
                                Layout.fillWidth: summaryGrid.columns === 2
                                Layout.alignment: Qt.AlignVCenter
                                Layout.preferredWidth: implicitWidth
                                font.pixelSize: DesktopTokens.captionSize
                                text: qsTr("Stream settings"); themedGlyph: "sliders"
                                onClicked: root.tune()
                            }
                        }
                    }
                }
            }
        }
    }
    readonly property int firstFoldHeight: (readiness.visible ? readiness.implicitHeight + bodyContent.spacing : 0) + DesktopTokens.px(56)
    Keys.onEscapePressed: root.closeRequested()
    Keys.onReturnPressed: if (primaryAction.enabled) root.playRequested()
}
