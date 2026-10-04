pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopLibraryScreen"
    property string searchQuery: ""
    property string activeFilter: "all"
    readonly property var collection: ShellStore.activeCollection
    readonly property var collectionGameIds: new Set(root.collection ? root.collection.gameIds : [])
    readonly property string selectedCollectionId: ShellStore.activeCollectionId
    onSelectedCollectionIdChanged: {
        activeFilter = "all"
        closeContext()
    }
    property var contextGame: null
    property var presentedContextGame: null
    onContextGameChanged: if (contextGame) presentedContextGame = contextGame
    MotionProgress { id: contextMotion; shown: root.contextGame !== null; enterDuration: 120; exitDuration: 120 }
    MotionProgress { id: collectionMotion; shown: root.contextGame !== null && root.collectionOpen; enterDuration: 120; exitDuration: 120 }
    property point contextPoint: Qt.point(0, 0)
    signal detailsRequested(var game)
    signal playRequested(var game)

    function storeBlob(game) {
        return (game.availableStores || []).join(" ").toLocaleLowerCase()
    }
    function hasRtx(game) {
        const blob = ((game.genres || []).join(" ") + " " + String(game.title || "") + " " + String(game.nvidiaTech || "")).toLocaleLowerCase()
        return blob.indexOf("rtx") >= 0 || blob.indexOf("ray tracing") >= 0
    }
    function hasController(game) {
        const controls = (game.supportedControls || []).join(" ").toLocaleLowerCase()
        return controls.indexOf("gamepad") >= 0 || controls.indexOf("controller") >= 0
    }
    function isReady(game) {
        return game.playabilityState === "PLAYABLE" && (game.variants || []).some(variant =>
            variant.gfnStatus === "AVAILABLE" && variant.playStatus !== "NOT_PLAYABLE" && !variant.stateDetails)
    }
    function favoriteLabel() {
        if (root.contextGame && ShellStore.isFavorite(root.contextGame))
            return qsTr("Remove from Home")
        return qsTr("Pin to Home")
    }
    function hideLabel() {
        if (root.contextGame && ShellStore.isHidden(root.contextGame))
            return qsTr("Unhide from library")
        return qsTr("Hide from library")
    }
    function countHidden() {
        return root.countWhere(root.isHiddenGame)
    }
    function isHiddenGame(game) {
        return ShellStore.isHidden(game)
    }
    function countStore(name) {
        return root.countWhere(function(game) { return root.storeBlob(game).indexOf(name) >= 0 })
    }
    function countWhere(predicate) {
        const source = ShellStore.catalogGames || []
        let count = 0
        for (let i = 0; i < source.length; ++i) {
            if (root.collection && !root.collectionGameIds.has(ShellStore.gameIdentity(source[i])))
                continue
            if (predicate(source[i]))
                count += 1
        }
        return count
    }
    function filteredGames() {
        const query = searchQuery.trim().toLocaleLowerCase()
        const source = activeFilter === "cloud-favorites" ? ShellStore.remoteFavorites : ShellStore.catalogGames || []
        const result = []
        for (let index = 0; index < source.length; ++index) {
            const game = source[index]
            if (root.collection && !root.collectionGameIds.has(ShellStore.gameIdentity(game)))
                continue
            if (query !== "" && String(game.title || "").toLocaleLowerCase().indexOf(query) < 0)
                continue
            const hidden = root.isHiddenGame(game)
            if (activeFilter === "hidden") {
                if (!hidden)
                    continue
            } else if (hidden) {
                continue
            }
            const stores = root.storeBlob(game)
            if (activeFilter === "ready" && !root.isReady(game))
                continue
            if (activeFilter === "rtx" && !root.hasRtx(game))
                continue
            if (activeFilter === "controller" && !root.hasController(game))
                continue
            if (activeFilter === "steam" && stores.indexOf("steam") < 0)
                continue
            if (activeFilter === "epic" && stores.indexOf("epic") < 0)
                continue
            if (activeFilter === "gog" && stores.indexOf("gog") < 0)
                continue
            result.push(game)
        }
        return result
    }
    readonly property var games: filteredGames()
    readonly property real tileScale: Math.max(0.75, Math.min(1.5, Number(ShellStore.settings.posterSizeScale || 1.05))) / 1.05
    readonly property int libraryColumns: Math.max(1, Math.floor(grid.width / (DesktopTokens.px(168) * tileScale)))
    readonly property int libraryCellW: Math.max(1, Math.floor(grid.width / libraryColumns))
    // Mirrors DesktopPoster: 2:3 art inside a focus gutter, plus the title block.
    readonly property int libraryCellH: Math.round((libraryCellW - DesktopTokens.px(10) * 2) * 3 / 2)
        + DesktopTokens.px(10) * 2 + DesktopTokens.px(62)

    function editCollection(mode, game) {
        collectionDialog.mode = mode
        collectionDialog.collectionId = mode === "create" ? "" : root.collection.id
        collectionDialog.initialName = mode === "create" ? "" : root.collection.name
        collectionDialog.game = game || null
        root.closeContext()
        collectionDialog.open()
    }

    DesktopCollectionDialog {
        id: collectionDialog
        onCollectionOpened: collectionId => ShellStore.activeCollectionId = collectionId
    }

    // Collections live here now that there is no sidebar: "All games" plus
    // one tab per collection, GeForce NOW style, with actions on the right.
    Item {
        id: collectionToolbar
        x: DesktopTokens.safeX; y: DesktopTokens.px(28)
        width: parent.width - DesktopTokens.safeX * 2
        height: DesktopTokens.px(52)

        Flickable {
            id: collectionTabsView
            anchors.left: parent.left
            anchors.right: collectionActions.left
            anchors.rightMargin: DesktopTokens.px(24)
            height: parent.height
            contentWidth: collectionTabs.width
            clip: true
            interactive: contentWidth > width
            boundsBehavior: Flickable.StopAtBounds
            Row {
                id: collectionTabs
                height: parent.height
                spacing: DesktopTokens.px(32)
                Repeater {
                    model: [{id: "", name: qsTr("All games")}].concat(ShellStore.gameCollections || [])
                    delegate: ItemDelegate {
                        id: collectionTab
                        required property var modelData
                        objectName: "libraryCollectionTab-" + (modelData.id || "all")
                        readonly property bool selected: root.selectedCollectionId === modelData.id
                        readonly property bool keyboardFocus: activeFocus && AppController.inputMode !== "pointer"
                        height: collectionTabs.height
                        padding: 0
                        focusPolicy: Qt.StrongFocus
                        Accessible.name: modelData.name
                        background: Item {
                            Rectangle {
                                anchors.bottom: parent.bottom
                                width: collectionTab.selected ? parent.width : 0
                                height: DesktopTokens.px(3)
                                color: DesktopTokens.focus
                                Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
                            }
                            Rectangle {
                                anchors.fill: parent
                                anchors.margins: -DesktopTokens.px(6)
                                radius: DesktopTokens.radius
                                color: "transparent"
                                border.width: collectionTab.keyboardFocus ? DesktopTokens.focusOutline : 0
                                border.color: Theme.label
                            }
                        }
                        contentItem: Text {
                            text: collectionTab.modelData.name
                            textFormat: Text.PlainText
                            verticalAlignment: Text.AlignVCenter
                            color: collectionTab.selected || collectionTab.hovered ? DesktopTokens.textHigh : DesktopTokens.textMuted
                            font.family: DesktopTokens.displayFont
                            font.pixelSize: DesktopTokens.headingSize
                            font.weight: collectionTab.selected ? Font.Bold : Font.DemiBold
                            Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
                        }
                        onClicked: ShellStore.activeCollectionId = modelData.id
                    }
                }
            }
        }
        Row {
            id: collectionActions
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(12)
            DesktopButton {
                text: qsTr("Rename")
                visible: root.collection !== null
                enabled: !ShellStore.collectionsBusy
                onClicked: root.editCollection("rename", null)
            }
            DesktopButton {
                text: qsTr("Delete")
                visible: root.collection !== null
                enabled: !ShellStore.collectionsBusy
                onClicked: root.editCollection("delete", null)
            }
            DesktopButton {
                objectName: "libraryNewCollectionButton"
                text: qsTr("New collection")
                glyph: "desktop-plus.svg"
                enabled: !ShellStore.collectionsBusy
                onClicked: root.editCollection("create", null)
            }
        }
    }

    Rectangle {
        id: catalogNotice
        objectName: "libraryCompletenessNotice"
        x: DesktopTokens.safeX
        y: collectionToolbar.y + collectionToolbar.height + DesktopTokens.px(20)
        width: parent.width - DesktopTokens.safeX * 2
        height: visible ? Math.max(DesktopTokens.px(64), noticeText.implicitHeight + DesktopTokens.px(28)) : 0
        visible: root.activeFilter === "cloud-favorites" || (ShellStore.catalogSource === "account-library" && ShellStore.catalogState !== "ready")
        radius: DesktopTokens.radius
        color: DesktopTokens.raised
        Text {
            id: noticeText
            x: DesktopTokens.px(18); anchors.verticalCenter: parent.verticalCenter
            width: parent.width - noticeAction.width - DesktopTokens.px(54)
            text: root.activeFilter === "cloud-favorites" ? (ShellStore.remoteFavoritesError || qsTr("GeForce NOW favorites may show only part of your favorites. Refresh to check for updates. Home pins are separate.")) : ShellStore.catalogError || (ShellStore.catalogComplete
                ? qsTr("Refreshing the library. Your last complete library is still shown.")
                : qsTr("Loading your library. The games shown so far are only part of it."))
            wrapMode: Text.WordWrap
            color: DesktopTokens.textBody
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
        }
        DesktopButton {
            id: noticeAction
            anchors.right: parent.right; anchors.rightMargin: DesktopTokens.px(14); anchors.verticalCenter: parent.verticalCenter
            text: root.activeFilter === "cloud-favorites" ? qsTr("Refresh") : ShellStore.catalogNextCursor ? qsTr("Continue") : qsTr("Retry")
            visible: root.activeFilter === "cloud-favorites" || (ShellStore.catalogRequestId === "" && ShellStore.catalogError !== "")
            enabled: root.activeFilter !== "cloud-favorites" || ShellStore.remoteFavoritesState !== "loading"
            onClicked: root.activeFilter === "cloud-favorites" ? ShellStore.refreshCloudFavorites() : ShellStore.continueCatalog()
        }
    }

    Flow {
        id: filterRow
        x: DesktopTokens.safeX
        y: catalogNotice.y + catalogNotice.height + DesktopTokens.px(catalogNotice.visible ? 16 : 20)
        width: parent.width - DesktopTokens.safeX * 2
        spacing: DesktopTokens.px(10)
        Repeater {
            model: [
                {key:"all", label:qsTr("All"), count:root.countWhere(function(game) { return !root.isHiddenGame(game) })},
                {key:"cloud-favorites", label:qsTr("GeForce NOW favorites"), count:ShellStore.remoteFavorites.length},
                {key:"ready", label:qsTr("Available versions"), count:root.countWhere(root.isReady)},
                {key:"rtx", label:"RTX", count:root.countWhere(root.hasRtx)},
                {key:"controller", label:qsTr("Controller"), count:root.countWhere(root.hasController)},
                {key:"steam", label:"Steam", count:root.countStore("steam")},
                {key:"epic", label:"Epic", count:root.countStore("epic")},
                {key:"gog", label:"GOG", count:root.countStore("gog")},
                {key:"hidden", label:qsTr("Hidden"), count:root.countHidden()}
            ]
            delegate: Button {
                id: filterButton
                required property var modelData
                visible: modelData.key !== "hidden" || root.countHidden() > 0
                readonly property bool chosen: root.activeFilter === filterButton.modelData.key
                height: DesktopTokens.px(40)
                implicitHeight: DesktopTokens.px(40)
                implicitWidth: Math.max(DesktopTokens.px(64), chipRow.implicitWidth + DesktopTokens.px(36))
                padding: 0
                leftPadding: 0
                rightPadding: 0
                topPadding: 0
                bottomPadding: 0
                focusPolicy: Qt.StrongFocus
                hoverEnabled: true
                clip: false
                // Chosen chip is solid light on dark, like GeForce NOW's filters.
                background: Rectangle {
                    radius: height / 2
                    color: filterButton.chosen ? DesktopTokens.textHigh : (filterButton.hovered ? DesktopTokens.hover : DesktopTokens.raised)
                    Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
                    Rectangle {
                        anchors.fill: parent
                        anchors.margins: -DesktopTokens.px(5)
                        radius: height / 2
                        color: "transparent"
                        border.width: filterButton.activeFocus ? DesktopTokens.focusOutline : 0
                        border.color: Theme.label
                    }
                }
                contentItem: Item {
                    implicitWidth: chipRow.implicitWidth
                    implicitHeight: DesktopTokens.px(40)
                    Row {
                        id: chipRow
                        anchors.centerIn: parent
                        spacing: DesktopTokens.px(8)
                        Image {
                            visible: ["steam","epic","gog"].indexOf(filterButton.modelData.key) >= 0
                            width: DesktopTokens.px(16)
                            height: DesktopTokens.px(16)
                            anchors.verticalCenter: parent.verticalCenter
                            source: visible ? DesktopTokens.storeIconUrl(filterButton.modelData.key) : ""
                            sourceSize: Qt.size(32, 32)
                            fillMode: Image.PreserveAspectFit
                        }
                        Text {
                            text: filterButton.modelData.label
                            anchors.verticalCenter: parent.verticalCenter
                            color: filterButton.chosen ? DesktopTokens.shell : DesktopTokens.textBody
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.captionSize
                            font.weight: Font.DemiBold
                            verticalAlignment: Text.AlignVCenter
                        }
                        Text {
                            text: filterButton.modelData.count
                            anchors.verticalCenter: parent.verticalCenter
                            color: filterButton.chosen ? Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.6) : DesktopTokens.textMuted
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.captionSize
                            font.weight: Font.Medium
                            font.features: { "tnum": 1 }
                            verticalAlignment: Text.AlignVCenter
                        }
                    }
                }
                onClicked: root.activeFilter = modelData.key
            }
        }
    }
    GridView {
        id: grid
        // Offset by the poster focus gutter so artwork lines up with the safe margin.
        x: DesktopTokens.safeX - DesktopTokens.px(10)
        y: filterRow.y + filterRow.height + DesktopTokens.px(18)
        width: parent.width - x * 2
        height: parent.height - y
        clip: true
        cellWidth: root.libraryCellW
        cellHeight: root.libraryCellH
        model: root.games
        focus: true
        boundsBehavior: Flickable.StopAtBounds
        ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
        highlightFollowsCurrentItem: true
        preferredHighlightBegin: DesktopTokens.px(40)
        preferredHighlightEnd: height - DesktopTokens.px(40)
        highlightRangeMode: GridView.ApplyRange
        highlightMoveDuration: DesktopTokens.motionDuration
        delegate: DesktopPoster {
            required property var modelData
            game: modelData
            tileWidth: root.libraryCellW
            tileHeight: root.libraryCellH
            onClicked: {
                ShellStore.selectedGame = modelData
                root.detailsRequested(modelData)
            }
            onDoubleClicked: {
                ShellStore.selectedGame = modelData
                root.playRequested(modelData)
            }
            onContextRequested: (sceneX, sceneY) => {
                root.contextGame = modelData
                const local = root.mapFromItem(null, sceneX, sceneY)
                root.contextPoint = Qt.point(Math.min(root.width - DesktopTokens.px(620), Math.max(16, local.x)), Math.min(root.height - DesktopTokens.px(380), Math.max(16, local.y)))
                root.collectionOpen = false
            }
        }
    }

    Column {
        anchors.centerIn: grid
        width: Math.min(grid.width - 48, DesktopTokens.px(560))
        spacing: DesktopTokens.px(14)
        visible: root.games.length === 0
        Text {
            width: parent.width
            text: root.collection ? qsTr("No games in this view") : qsTr("No games found")
            color: DesktopTokens.text
            font.family: DesktopTokens.displayFont
            font.pixelSize: DesktopTokens.titleSize
            font.bold: true
            horizontalAlignment: Text.AlignHCenter
        }
        Text {
            width: parent.width
            text: root.collection
                ? qsTr("Open All games, right-click a game, and choose Add to collection. Games can belong to more than one collection.")
                : qsTr("Try a different search or filter.")
            color: DesktopTokens.textMuted
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            wrapMode: Text.WordWrap
            horizontalAlignment: Text.AlignHCenter
        }
    }

    MouseArea {
        anchors.fill: parent; z: 40
        visible: root.contextGame !== null
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        onClicked: { root.contextGame = null; root.collectionOpen = false }
    }
    property bool collectionOpen: false
    function closeContext() {
        root.contextGame = null
        root.collectionOpen = false
    }
    function activateContext(action) {
        const game = root.contextGame
        if (action === "favorite") {
            ShellStore.toggleFavorite(game)
            return
        }
        if (action === "hide") {
            ShellStore.toggleHidden(game)
            root.closeContext()
            return
        }
        root.closeContext()
        if (action === "play") { ShellStore.selectedGame = game; root.playRequested(game) }
        else if (action === "details") { ShellStore.selectedGame = game; root.detailsRequested(game) }
        else if (action === "settings") AppController.navigate("settings-streaming")
    }
    Rectangle {
        x: root.contextPoint.x; y: root.contextPoint.y
        width: DesktopTokens.px(300); height: contextColumn.implicitHeight + DesktopTokens.px(16); radius: DesktopTokens.radiusLarge
        visible: contextMotion.present
        enabled: root.contextGame !== null
        z: 41
        color: DesktopTokens.raised
        Column {
            id: contextColumn
            x: DesktopTokens.px(8); y: DesktopTokens.px(8); width: parent.width - DesktopTokens.px(16); spacing: 0
            Text { width: parent.width; height: DesktopTokens.px(36); leftPadding: 8; text: root.presentedContextGame ? String(root.presentedContextGame.title || "") : ""; color: DesktopTokens.textMuted; elide: Text.ElideRight; verticalAlignment: Text.AlignVCenter; font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.captionSize; font.weight: Font.DemiBold; font.letterSpacing: 0 }
            Rectangle {
                id: playRow
                width: parent.width; height: DesktopTokens.px(44); radius: DesktopTokens.radius
                color: playHover.hovered ? Qt.lighter(DesktopTokens.focus, 1.1) : DesktopTokens.focus
                Text { x: DesktopTokens.px(14); anchors.verticalCenter: parent.verticalCenter; text: "▶  " + qsTr("Play"); color: Theme.focusText; font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.captionSize; font.weight: Font.Bold }
                KeyboardGlyph { anchors.right: parent.right; anchors.rightMargin: 12; anchors.verticalCenter: parent.verticalCenter; shortcut: "Enter"; keySize: DesktopTokens.px(22); ink: Theme.focusText; Accessible.name: qsTr("Enter") }
                HoverHandler { id: playHover; cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: root.activateContext("play") }
            }
            Item { width: parent.width; height: 6 }
            Repeater {
                model: [
                    {label:qsTr("Details"), key:"Space", action:"details"},
                    {label:root.favoriteLabel(), key:"F", action:"favorite"},
                    {label:qsTr("Add to collection"), key:"›", action:"collection"},
                    {label:qsTr("Stream settings…"), key:"Ctrl ,", action:"settings"},
                    {label:root.hideLabel(), key:"", action:"hide"}
                ]
                delegate: ItemDelegate {
                    required property var modelData
                    width: contextColumn.width; height: DesktopTokens.px(42); padding: DesktopTokens.px(10)
                    highlighted: modelData.action === "collection" && root.collectionOpen
                    background: Rectangle { radius: DesktopTokens.radius; color: parent.hovered || parent.activeFocus || (modelData.action === "collection" && root.collectionOpen) ? DesktopTokens.hover : "transparent" }
                    contentItem: Item {
                        Text { anchors.left: parent.left; anchors.verticalCenter: parent.verticalCenter; text: modelData.label; color: DesktopTokens.textBody; font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.captionSize }
                        KeyboardGlyph { visible: modelData.action !== "collection"; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; shortcut: modelData.key; keySize: DesktopTokens.px(20); ink: DesktopTokens.textMuted }
                        Text { visible: modelData.action === "collection"; anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter; text: modelData.key; color: DesktopTokens.textFaint; font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.captionSize }
                    }
                    onClicked: {
                        if (modelData.action === "collection")
                            root.collectionOpen = !root.collectionOpen
                        else
                            root.activateContext(modelData.action)
                    }
                }
            }
        }
        opacity: contextMotion.progress
        scale: contextMotion.zoom
        transformOrigin: Item.TopLeft
    }
    Rectangle {
        x: Math.max(8, Math.min(root.width - width - 8, root.contextPoint.x + DesktopTokens.px(308)))
        y: Math.max(8, Math.min(root.height - height - 8, root.contextPoint.y + DesktopTokens.px(130)))
        width: DesktopTokens.px(300)
        height: Math.min(root.height - 16, DesktopTokens.px(120) + Math.min(6, ShellStore.gameCollections.length) * DesktopTokens.px(42) + (ShellStore.collectionError ? DesktopTokens.px(60) : 0))
        radius: DesktopTokens.radiusLarge
        visible: collectionMotion.present
        enabled: root.contextGame !== null && root.collectionOpen
        z: 42
        color: DesktopTokens.raised
        Column {
            x: DesktopTokens.px(8); y: DesktopTokens.px(8); width: parent.width - DesktopTokens.px(16); spacing: 0
            Text { width: parent.width; height: DesktopTokens.px(32); leftPadding: 8; text: qsTr("Collections"); color: DesktopTokens.textFaint; verticalAlignment: Text.AlignVCenter; font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.captionSize; font.weight: Font.DemiBold; font.letterSpacing: 0 }
            ListView {
                width: parent.width
                height: Math.min(6, count) * DesktopTokens.px(42)
                clip: true
                model: ShellStore.gameCollections
                boundsBehavior: Flickable.StopAtBounds
                ScrollBar.vertical: ScrollBar { }
                delegate: ItemDelegate {
                    id: membershipButton
                    required property var modelData
                    width: ListView.view.width
                    height: DesktopTokens.px(42)
                    padding: DesktopTokens.px(10)
                    enabled: !ShellStore.collectionsBusy
                    Accessible.name: modelData.name
                    background: Rectangle { radius: DesktopTokens.radius; color: membershipButton.hovered || membershipButton.activeFocus ? DesktopTokens.hover : "transparent" }
                    contentItem: Text {
                        text: (ShellStore.isInCollection(root.contextGame, membershipButton.modelData.id) ? "✓  " : "+  ") + membershipButton.modelData.name
                        textFormat: Text.PlainText
                        elide: Text.ElideRight
                        verticalAlignment: Text.AlignVCenter
                        color: DesktopTokens.textBody
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                    }
                    onClicked: ShellStore.toggleCollectionGame(modelData.id, root.contextGame)
                }
            }
            ItemDelegate {
                id: newCollectionAction
                width: parent.width; height: DesktopTokens.px(44); padding: DesktopTokens.px(10)
                enabled: !ShellStore.collectionsBusy
                background: Rectangle { radius: DesktopTokens.radius; color: newCollectionAction.hovered || newCollectionAction.activeFocus ? DesktopTokens.hover : "transparent" }
                contentItem: Text {
                    text: qsTr("New collection")
                    verticalAlignment: Text.AlignVCenter
                    color: DesktopTokens.textBody
                    font.family: DesktopTokens.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                }
                onClicked: root.editCollection("create", root.contextGame)
            }
            Text {
                width: parent.width
                height: visible ? DesktopTokens.px(60) : 0
                visible: ShellStore.collectionError !== ""
                text: ShellStore.collectionError
                textFormat: Text.PlainText
                wrapMode: Text.WordWrap
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                }
        }
        opacity: collectionMotion.progress
        scale: collectionMotion.zoom
        transformOrigin: Item.TopLeft
    }
}
