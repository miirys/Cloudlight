pragma ComponentBehavior: Bound

import QtQuick
import OpenNOW

// GeForce NOW home: a full-bleed rotating hero for recently played games,
// then landscape rails. Everything is opaque; art fades into the shell colour
// instead of sitting in translucent cards.
FocusScope {
    id: root
    objectName: "desktopHomeScreen"

    readonly property string pageTitle: qsTr("Home")
    readonly property string pageSubtitle: ShellStore.catalogTotalCount
        ? qsTr("%1 games").arg(ShellStore.catalogTotalCount)
        : qsTr("Your library")
    property int focusZone: 0
    property int focusIndex: 0
    property bool active: true
    property double lastPlayedNowMs: Date.now()
    property int heroIndex: 0
    property real reveal: AppController.reducedMotion ? 1 : 0
    // Read by the shell: the top bar stays a scrim over the hero, solid below it.
    readonly property bool headerSolid: contentFlick.contentY > root.heroHeight - DesktopTokens.topBarHeight * 2

    Timer {
        interval: 1000
        repeat: true
        running: root.active && root.visible && Qt.application.state === Qt.ApplicationActive
        triggeredOnStart: true
        onTriggered: root.lastPlayedNowMs = Date.now()
    }

    signal routeRequested(string route)
    signal gameRequested(var game)

    anchors.fill: parent
    focus: active
    clip: true
    Accessible.role: Accessible.Pane
    Accessible.name: pageTitle

    readonly property var games: ShellStore.catalogGames || []
    readonly property var sortedRecent: {
        const list = (root.games || []).slice()
        list.sort((left, right) => String(right.lastPlayed || "").localeCompare(String(left.lastPlayed || "")))
        return list
    }
    readonly property var heroGames: root.takeGames(root.sortedRecent, 0, 5)
    readonly property var heroGame: root.heroGames.length
        ? root.heroGames[Math.min(root.heroIndex, root.heroGames.length - 1)] : null
    readonly property var jumpGames: root.takeGames(root.sortedRecent, 1, 20)
    readonly property var favoriteGames: {
        const list = []
        for (let i = 0; i < root.games.length; ++i) {
            if (ShellStore.isFavorite(root.games[i]))
                list.push(root.games[i])
        }
        return list.length ? list : root.takeGames(root.games, 0, 20)
    }
    readonly property var newGames: root.takeGames(root.games, Math.max(0, root.games.length - 20), 20).reverse()
    readonly property var rails: [
        { title: qsTr("Jump back in"), games: root.jumpGames },
        { title: qsTr("Favourites"), games: root.favoriteGames },
        { title: qsTr("New in your library"), games: root.newGames }
    ]

    readonly property int safeX: DesktopTokens.safeX
    readonly property int railGap: DesktopTokens.px(16)
    // Five landscape tiles across, with the sixth peeking in to show the rail scrolls.
    readonly property int tileWidth: Math.floor((root.width - root.safeX * 2 - root.railGap * 4) / 5.3)
    readonly property int tileHeight: Math.round(root.tileWidth * 9 / 16)
    readonly property int heroHeight: Math.max(DesktopTokens.px(380), Math.round(root.height * 0.7))

    // reveal runs linearly over revealSpan ms; each element eases its own slice.
    readonly property int revealSpan: 1100
    function revealAt(delay, duration) {
        const t = Math.max(0, Math.min(1, (root.reveal * root.revealSpan - delay) / duration))
        return 1 - Math.pow(1 - t, 4)
    }

    function takeGames(source, start, limit) {
        const list = source || []
        const result = []
        for (let index = start; index < list.length && result.length < limit; ++index)
            result.push(list[index])
        return result
    }

    function heroMeta() {
        const game = root.heroGame
        if (!game)
            return qsTr("Sign in and sync your library to continue a game.")
        const last = DesktopTokens.relativeLastPlayed(game.lastPlayed, root.lastPlayedNowMs)
        const hours = game.hoursPlayed ? qsTr("%1 h played").arg(game.hoursPlayed) : ""
        if (last !== "" && hours !== "")
            return last + "  ·  " + hours
        if (last !== "")
            return last
        if (hours !== "")
            return hours
        return qsTr("Ready to stream from your library")
    }

    function zoneGames(zone) {
        return zone >= 1 && zone <= root.rails.length ? root.rails[zone - 1].games : []
    }

    function zoneCount(zone) {
        return zone === 0 ? 2 : root.zoneGames(zone).length
    }

    function setSelection(zone, index) {
        root.focusZone = Math.max(0, Math.min(root.rails.length, zone))
        const count = root.zoneCount(root.focusZone)
        root.focusIndex = Math.max(0, Math.min(Math.max(0, count - 1), index))
        const rail = root.focusZone > 0 ? railRepeater.itemAt(root.focusZone - 1) : null
        if (rail)
            rail.currentIndex = root.focusIndex
        root.ensureSelectionVisible()
    }

    function ensureSelectionVisible() {
        let target = 0
        if (root.focusZone > 0) {
            const rail = railRepeater.itemAt(root.focusZone - 1)
            if (!rail)
                return
            // Keep the focused rail high on screen with the hero edge peeking above.
            target = Math.max(0, Math.min(contentFlick.contentHeight - contentFlick.height,
                                          rail.y - DesktopTokens.topBarHeight - DesktopTokens.px(40)))
        }
        if (Math.abs(target - contentFlick.contentY) < 1)
            return
        scrollAnimation.to = target
        scrollAnimation.restart()
    }

    function moveHorizontal(delta) {
        const count = root.zoneCount(root.focusZone)
        if (count <= 0)
            return
        root.setSelection(root.focusZone, Math.max(0, Math.min(count - 1, root.focusIndex + delta)))
    }

    function moveVertical(delta) {
        let nextZone = root.focusZone + delta
        while (nextZone > 0 && nextZone <= root.rails.length && root.zoneCount(nextZone) === 0)
            nextZone += delta
        if (nextZone < 0 || nextZone > root.rails.length)
            return
        let nextIndex = 0
        if (nextZone > 0) {
            const rail = railRepeater.itemAt(nextZone - 1)
            nextIndex = rail ? Math.max(0, rail.currentIndex) : 0
        }
        root.setSelection(nextZone, nextIndex)
    }

    function openGame(game) {
        if (!game)
            return
        root.gameRequested(game)
    }

    function startHero() {
        if (!root.heroGame)
            return
        ShellStore.selectedGame = root.heroGame
        if (ShellStore.signedIn)
            ShellStore.launchSelectedGame(false)
        else
            AppController.navigate("sign-in")
    }

    function activateSelection() {
        if (root.focusZone === 0) {
            if (root.focusIndex === 0)
                root.startHero()
            else
                root.openGame(root.heroGame)
            return
        }
        const selectedGames = root.zoneGames(root.focusZone)
        if (selectedGames.length > root.focusIndex)
            root.openGame(selectedGames[root.focusIndex])
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Left) {
            root.moveHorizontal(-1)
        } else if (event.key === Qt.Key_Right) {
            root.moveHorizontal(1)
        } else if (event.key === Qt.Key_Up) {
            root.moveVertical(-1)
        } else if (event.key === Qt.Key_Down) {
            root.moveVertical(1)
        } else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
            root.activateSelection()
        } else {
            return
        }
        event.accepted = true
    }

    Timer {
        id: heroRotation
        interval: 9000
        repeat: true
        running: root.active && root.visible && root.heroGames.length > 1
            && !heroHover.hovered && !AppController.reducedMotion
        onTriggered: root.heroIndex = (root.heroIndex + 1) % root.heroGames.length
    }

    NumberAnimation {
        id: scrollAnimation
        target: contentFlick
        property: "contentY"
        duration: DesktopTokens.motionDuration
        easing.type: Easing.BezierSpline
        easing.bezierCurve: [0.2, 0, 0, 1, 1, 1]
    }

    Flickable {
        id: contentFlick
        anchors.fill: parent
        contentWidth: width
        contentHeight: Math.max(height, homeColumn.implicitHeight + DesktopTokens.px(64))
        clip: true
        boundsBehavior: Flickable.StopAtBounds
        flickDeceleration: 5200
        maximumFlickVelocity: 2200
        Accessible.role: Accessible.Pane

        Column {
            id: homeColumn
            width: contentFlick.width
            spacing: DesktopTokens.px(28)

            Item {
                id: heroRow
                objectName: "desktopHomeHero"
                width: parent.width
                height: root.heroHeight
                clip: true

                HoverHandler { id: heroHover }

                Rectangle { anchors.fill: parent; color: DesktopTokens.shell }

                Repeater {
                    model: root.heroGames
                    delegate: Item {
                        id: heroLayer
                        required property var modelData
                        required property int index
                        readonly property bool shown: index === Math.min(root.heroIndex, root.heroGames.length - 1)
                        anchors.fill: parent
                        opacity: shown ? 1 : 0
                        visible: opacity > 0
                        Behavior on opacity {
                            NumberAnimation { duration: AppController.reducedMotion ? 0 : 700; easing.type: Easing.InOutQuad }
                        }
                        ArtworkSource {
                            id: heroArt
                            sourceUrl: DesktopTokens.decodeArtworkUrl(DesktopTokens.artworkUrl(heroLayer.modelData, true))
                            active: root.active
                        }
                        Image {
                            anchors.fill: parent
                            source: heroArt.resolvedUrl
                            fillMode: Image.PreserveAspectCrop
                            sourceSize: Qt.size(1920, 1080)
                            asynchronous: true
                            cache: true
                            // Gentle settle on reveal and on each new slide.
                            scale: heroLayer.shown ? 1 + 0.06 * (1 - root.revealAt(0, 1100)) : 1.04
                            Behavior on scale {
                                NumberAnimation { duration: AppController.reducedMotion ? 0 : 1200; easing.type: Easing.OutCubic }
                            }
                        }
                    }
                }

                // Legibility: darken the left side for text, then melt the
                // bottom edge into the page so the rails sit on solid colour.
                Rectangle {
                    anchors.fill: parent
                    gradient: Gradient {
                        orientation: Gradient.Horizontal
                        GradientStop { position: 0; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.9) }
                        GradientStop { position: 0.4; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.5) }
                        GradientStop { position: 0.72; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0) }
                    }
                }
                Rectangle {
                    anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                    height: parent.height * 0.5
                    gradient: Gradient {
                        GradientStop { position: 0; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0) }
                        GradientStop { position: 1; color: DesktopTokens.shell }
                    }
                }

                Column {
                    id: heroText
                    x: root.safeX
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: DesktopTokens.px(44)
                    width: Math.min(parent.width * 0.5, DesktopTokens.px(760))
                    spacing: DesktopTokens.px(12)
                    readonly property real shown: root.revealAt(120, 520)
                    opacity: shown
                    transform: Translate { y: DesktopTokens.px(28) * (1 - heroText.shown) }

                    Text {
                        text: root.heroGame ? qsTr("CONTINUE PLAYING") : qsTr("WELCOME")
                        color: DesktopTokens.focus
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                        font.weight: Font.Bold
                        font.letterSpacing: DesktopTokens.px(2)
                    }
                    Text {
                        objectName: "desktopHomeHeroTitle"
                        width: parent.width
                        text: root.heroGame ? String(root.heroGame.title || qsTr("Game")) : qsTr("No games yet")
                        color: "#FFFFFF"
                        font.family: DesktopTokens.displayFont
                        font.pixelSize: DesktopTokens.displaySize
                        font.weight: Font.Bold
                        font.letterSpacing: -DesktopTokens.px(0.5)
                        wrapMode: Text.WordWrap
                        maximumLineCount: 2
                        elide: Text.ElideRight
                        lineHeight: 1.05
                    }
                    Text {
                        width: parent.width
                        text: root.heroMeta()
                        color: "#D9D9D9"
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.bodySize
                        font.weight: Font.Medium
                        elide: Text.ElideRight
                    }
                    Item { width: 1; height: DesktopTokens.px(8) }
                    Row {
                        spacing: DesktopTokens.px(16)
                        DesktopHeroButton {
                            objectName: "desktopHomePlay"
                            primary: true
                            glyph: "play"
                            text: qsTr("Play")
                            selected: root.focusZone === 0 && root.focusIndex === 0
                            onPointed: root.setSelection(0, 0)
                            onActivated: root.startHero()
                        }
                        DesktopHeroButton {
                            objectName: "desktopHomeDetails"
                            glyph: "info"
                            text: qsTr("Details")
                            selected: root.focusZone === 0 && root.focusIndex === 1
                            onPointed: root.setSelection(0, 1)
                            onActivated: root.openGame(root.heroGame)
                        }
                    }
                }

                Row {
                    id: heroPager
                    visible: root.heroGames.length > 1
                    anchors.right: parent.right
                    anchors.rightMargin: root.safeX
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: DesktopTokens.px(66)
                    spacing: DesktopTokens.px(8)
                    opacity: root.revealAt(400, 400)
                    Repeater {
                        model: root.heroGames.length
                        delegate: Rectangle {
                            id: dash
                            required property int index
                            readonly property bool current: index === root.heroIndex
                            width: current ? DesktopTokens.px(40) : DesktopTokens.px(20)
                            height: DesktopTokens.px(4)
                            radius: height / 2
                            color: current ? DesktopTokens.focus : "#80FFFFFF"
                            Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
                            TapHandler { onTapped: root.heroIndex = dash.index }
                        }
                    }
                }
            }

            Repeater {
                id: railRepeater
                model: root.rails
                delegate: HomeRail {}
            }
        }
    }

    // Staggered page reveal: hero first, then each rail a beat later.
    NumberAnimation {
        id: revealAnimation
        target: root
        property: "reveal"
        from: 0; to: 1
        duration: root.revealSpan
    }

    component HomeRail: Item {
        id: rail
        required property var modelData
        required property int index
        readonly property int zone: index + 1
        property alias currentIndex: list.currentIndex
        onCurrentIndexChanged: rail.reveal(currentIndex)

        // Scroll only as far as needed to keep the focused tile inside the safe area.
        function reveal(i) {
            if (i < 0)
                return
            const x = i * (root.tileWidth + root.railGap)
            const minX = -list.leftMargin
            const maxX = Math.max(minX, list.contentWidth + list.rightMargin - list.width)
            let target = list.contentX
            if (x < list.contentX + root.safeX)
                target = x - root.safeX
            else if (x + root.tileWidth > list.contentX + list.width - root.safeX)
                target = x + root.tileWidth - list.width + root.safeX
            target = Math.max(minX, Math.min(maxX, target))
            if (Math.abs(target - list.contentX) < 1)
                return
            railScroll.to = target
            railScroll.restart()
        }
        NumberAnimation {
            id: railScroll
            target: list
            property: "contentX"
            duration: DesktopTokens.motionDuration
            easing.type: Easing.BezierSpline
            easing.bezierCurve: [0.2, 0, 0, 1, 1, 1]
        }
        readonly property real railReveal: root.revealAt(260 + 110 * index, 520)
        width: homeColumn.width
        height: visible ? header.height + DesktopTokens.px(14) + list.height : 0
        visible: (modelData.games || []).length > 0
        opacity: railReveal
        transform: Translate { y: DesktopTokens.px(32) * (1 - rail.railReveal) }

        Item {
            id: header
            x: root.safeX
            width: parent.width - root.safeX * 2
            height: DesktopTokens.px(34)
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: rail.modelData.title
                color: DesktopTokens.textHigh
                font.family: DesktopTokens.displayFont
                font.pixelSize: DesktopTokens.headingSize
                font.weight: Font.Bold
            }
            Text {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                text: qsTr("SEE ALL")
                color: seeAll.hovered ? DesktopTokens.textHigh : DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                font.weight: Font.Bold
                font.letterSpacing: DesktopTokens.px(1.2)
                HoverHandler { id: seeAll; cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: root.routeRequested("library") }
            }
        }

        ListView {
            id: list
            y: header.height + DesktopTokens.px(14)
            width: parent.width
            // Room below the art for the title line.
            height: root.tileHeight + DesktopTokens.px(52)
            orientation: ListView.Horizontal
            spacing: root.railGap
            leftMargin: root.safeX
            rightMargin: root.safeX
            clip: false
            boundsBehavior: Flickable.StopAtBounds
            model: rail.modelData.games
            currentIndex: 0
            highlightFollowsCurrentItem: false
            delegate: DesktopHomePoster {
                required property var modelData
                required property int index
                game: modelData
                landscape: true
                tileWidth: root.tileWidth
                tileHeight: root.tileHeight
                current: root.focusZone === rail.zone && root.focusIndex === index
                onPointed: root.setSelection(rail.zone, index)
                onActivated: root.openGame(modelData)
            }
        }
    }

    Component.onCompleted: {
        root.focusZone = 0
        root.focusIndex = Math.max(0, Math.min(1, ShellStore.focusIndex("desktop-home")))
        if (!AppController.reducedMotion)
            revealAnimation.start()
        if (root.active)
            Qt.callLater(root.forceActiveFocus)
    }
    onFocusIndexChanged: ShellStore.rememberFocus("desktop-home", focusIndex)
}
