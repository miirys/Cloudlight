import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    default property alias contentData: contentHost.data
    property string route: "home"
    readonly property bool settingsPage: route.indexOf("settings") === 0
    readonly property int headerHeight: DesktopTokens.topBarHeight
    property string title: qsTr("Home")
    property string subtitle: qsTr("Your library")
    property bool searchVisible: route !== "settings" && route.indexOf("settings-") !== 0 && route !== "friends" && route !== "updates"
    property string searchText: ""
    property bool headerOverlay: false
    // Narrow or heavily scaled windows drop the clock and wordmark and pull
    // the bar in from the 10-foot safe margin rather than letting items collide.
    readonly property bool headerTight: width < DesktopTokens.px(1280)
    readonly property bool headerCompact: width < DesktopTokens.px(1100)
    readonly property int headerEdge: headerCompact ? DesktopTokens.px(24) : DesktopTokens.safeX
    property bool headerSolid: true
    property date now: new Date()
    readonly property bool friendsAvailable: Boolean(ShellStore.socialCapabilities && ShellStore.socialCapabilities.friendsAvailable)
    readonly property var navItems: {
        const items = [
            { route: "home", name: qsTr("Home") },
            { route: "library", name: qsTr("Library") },
            { route: "store", name: qsTr("Store") }
        ]
        if (root.friendsAvailable)
            items.push({ route: "friends", name: qsTr("Friends") })
        return items
    }
    signal routeRequested(string route)
    signal consoleModeRequested()
    signal commandPaletteRequested()

    anchors.fill: parent
    focus: true

    function routeSelected(value) {
        if (value === "library")
            return root.route === "library" || root.route === "game-detail"
        return root.route === value
    }

    function displayName() {
        return ShellStore.signedIn && ShellStore.authSession && ShellStore.authSession.user
            ? String(ShellStore.authSession.user.displayName || qsTr("Player"))
            : qsTr("Guest")
    }

    Timer {
        interval: 15000
        repeat: true
        running: root.visible
        triggeredOnStart: true
        onTriggered: root.now = new Date()
    }

    function activeSessionPrompt() {
        const session = ShellStore.resumableSession
        if (!session)
            return ""
        const title = ShellStore.sessionGameTitle(session)
        return title
            ? qsTr("Active session: %1 · Resume?").arg(title)
            : qsTr("Active session running · Resume?")
    }

    DesktopBackdrop { anchors.fill: parent }

    // GeForce NOW layout: one opaque top bar (brand, section tabs, search,
    // account) over full-width content. No sidebar, no footer.
    Item {
        id: main
        anchors.fill: parent

        Rectangle {
            id: header
            objectName: "desktopTopBar"
            width: parent.width; height: root.headerHeight
            z: 2
            color: "transparent"

            // Solid bar on every page; over the home hero it starts as a scrim
            // and fills in once the hero scrolls away.
            Rectangle {
                anchors.fill: parent
                color: DesktopTokens.topBar
                opacity: !root.headerOverlay || root.headerSolid ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: DesktopTokens.motionDuration } }
            }
            Rectangle {
                anchors.left: parent.left; anchors.right: parent.right; anchors.top: parent.top
                height: parent.height * 1.6
                visible: opacity > 0
                opacity: root.headerOverlay && !root.headerSolid ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: DesktopTokens.motionDuration } }
                gradient: Gradient {
                    GradientStop { position: 0; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.8) }
                    GradientStop { position: 1; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0) }
                }
            }

            DesktopBrandLockup {
                id: brand
                x: root.headerEdge
                textReveal: root.headerCompact ? 0 : 1
                anchors.verticalCenter: parent.verticalCenter
                markHeight: DesktopTokens.px(22)
                fontPixelSize: DesktopTokens.px(21)
            }

            Row {
                id: tabs
                objectName: "desktopTopTabs"
                x: brand.x + brand.width + DesktopTokens.px(root.headerCompact ? 20 : 44)
                height: parent.height
                spacing: DesktopTokens.px(root.headerCompact ? 0 : 8)
                Repeater {
                    model: root.navItems
                    delegate: ItemDelegate {
                        id: tab
                        required property var modelData
                        required property int index
                        objectName: "desktopTab-" + modelData.route
                        readonly property bool selected: root.routeSelected(modelData.route)
                        readonly property bool keyboardFocus: activeFocus && AppController.inputMode !== "pointer"
                        height: tabs.height
                        leftPadding: DesktopTokens.px(root.headerCompact ? 12 : 16); rightPadding: leftPadding
                        topPadding: 0; bottomPadding: 0
                        focusPolicy: Qt.StrongFocus
                        Accessible.name: modelData.name
                        background: Item {
                            Rectangle {
                                anchors.fill: parent
                                anchors.topMargin: DesktopTokens.px(14); anchors.bottomMargin: DesktopTokens.px(14)
                                radius: DesktopTokens.radius
                                color: tab.hovered || tab.keyboardFocus ? DesktopTokens.hover : "transparent"
                                border.width: tab.keyboardFocus ? DesktopTokens.focusOutline : 0
                                border.color: Theme.label
                                Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
                            }
                            Rectangle {
                                anchors.bottom: parent.bottom
                                anchors.horizontalCenter: parent.horizontalCenter
                                height: DesktopTokens.px(3)
                                width: tab.selected ? parent.width - DesktopTokens.px(24) : 0
                                color: DesktopTokens.focus
                                Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
                            }
                        }
                        contentItem: Text {
                            text: tab.modelData.name
                            verticalAlignment: Text.AlignVCenter
                            color: tab.selected || tab.hovered || tab.keyboardFocus ? DesktopTokens.textHigh : DesktopTokens.textMuted
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.navSize
                            font.weight: tab.selected ? Font.Bold : Font.DemiBold
                            Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
                        }
                        onClicked: {
                            if (modelData.route === "library")
                                ShellStore.activeCollectionId = ""
                            root.routeRequested(modelData.route)
                        }
                    }
                }
            }

            TextField {
                id: search
                objectName: "desktopHeaderSearch"
                readonly property real slotLeft: tabs.x + tabs.width + DesktopTokens.px(32)
                readonly property real slotRight: account.x - DesktopTokens.px(24)
                visible: root.searchVisible && slotRight - slotLeft >= DesktopTokens.px(220)
                x: slotLeft + Math.max(0, (slotRight - slotLeft - width) / 2)
                anchors.verticalCenter: parent.verticalCenter
                width: Math.min(DesktopTokens.px(560), slotRight - slotLeft)
                height: DesktopTokens.px(44)
                leftPadding: DesktopTokens.px(46); rightPadding: DesktopTokens.px(16); topPadding: 0; bottomPadding: 0
                color: DesktopTokens.textHigh
                placeholderText: root.route === "friends" ? qsTr("Search friends") : root.route === "store" ? qsTr("Search the store") : qsTr("Find your games")
                placeholderTextColor: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.Medium
                text: root.searchText
                selectByMouse: true
                background: Rectangle {
                    radius: DesktopTokens.radius
                    color: search.activeFocus ? DesktopTokens.raisedStrong : DesktopTokens.raised
                    border.width: search.activeFocus ? DesktopTokens.focusOutline : 0
                    border.color: Theme.label
                }
                onTextChanged: root.searchText = text
                DesktopGlyph { x: DesktopTokens.px(16); anchors.verticalCenter: parent.verticalCenter; width: DesktopTokens.px(18); height: width; icon: "desktop-search.svg" }
            }

            Row {
                id: account
                anchors.right: parent.right
                anchors.rightMargin: root.headerEdge
                anchors.verticalCenter: parent.verticalCenter
                spacing: DesktopTokens.px(12)

                DesktopButton {
                    id: activeSessionButton
                    objectName: "desktopHeaderResume"
                    visible: ShellStore.resumableSession !== null && root.route !== "stream"
                    anchors.verticalCenter: parent.verticalCenter
                    // Narrow windows keep the action as a play icon so the tabs never collide with it.
                    width: root.headerCompact ? DesktopTokens.px(44)
                        : Math.min(DesktopTokens.px(root.headerTight ? 200 : 300), Math.max(DesktopTokens.px(140), implicitWidth))
                    height: DesktopTokens.px(44)
                    leftPadding: root.headerCompact ? 0 : DesktopTokens.px(20)
                    rightPadding: leftPadding
                    primary: true
                    glyph: root.headerCompact ? "desktop-play.svg" : ""
                    text: root.headerCompact ? "" : root.headerTight ? qsTr("Resume game") : root.activeSessionPrompt()
                    Accessible.name: root.activeSessionPrompt()
                    ToolTip.visible: hovered
                    ToolTip.text: root.activeSessionPrompt()
                    ToolTip.delay: 700
                    onClicked: ShellStore.resumeActiveSession()
                }

                Text {
                    id: clock
                    visible: !root.headerTight
                    anchors.verticalCenter: parent.verticalCenter
                    text: Qt.formatTime(root.now, Qt.locale().timeFormat(Locale.ShortFormat))
                    color: DesktopTokens.textMuted
                    font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.DemiBold
                    font.features: { "tnum": 1 }
                }

                ItemDelegate {
                    id: profileButton
                    objectName: "desktopHeaderProfile"
                    anchors.verticalCenter: parent.verticalCenter
                    height: DesktopTokens.px(48)
                    leftPadding: DesktopTokens.px(6); rightPadding: DesktopTokens.px(14)
                    topPadding: 0; bottomPadding: 0
                    focusPolicy: Qt.StrongFocus
                    Accessible.name: qsTr("Account")
                    readonly property bool keyboardFocus: activeFocus && AppController.inputMode !== "pointer"
                    background: Rectangle {
                        radius: DesktopTokens.radius
                        color: profileButton.hovered || profileButton.keyboardFocus ? DesktopTokens.hover : "transparent"
                        border.width: profileButton.keyboardFocus ? DesktopTokens.focusOutline : 0
                        border.color: Theme.label
                    }
                    contentItem: Row {
                        spacing: DesktopTokens.px(12)
                        Rectangle {
                            anchors.verticalCenter: parent.verticalCenter
                            width: DesktopTokens.px(36); height: width; radius: width / 2
                            color: DesktopTokens.focus
                            Text {
                                anchors.centerIn: parent
                                text: root.displayName().charAt(0).toUpperCase()
                                color: Theme.focusText
                                font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.Bold
                            }
                        }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            visible: header.width >= DesktopTokens.px(1280)
                            text: root.displayName()
                            color: DesktopTokens.textHigh
                            font.family: DesktopTokens.bodyFont; font.pixelSize: DesktopTokens.bodySize; font.weight: Font.DemiBold
                        }
                    }
                    onClicked: root.routeRequested("settings-account")
                }

                ItemDelegate {
                    id: settingsButton
                    objectName: "desktopHeaderSettings"
                    anchors.verticalCenter: parent.verticalCenter
                    width: DesktopTokens.px(48); height: width
                    padding: 0
                    focusPolicy: Qt.StrongFocus
                    Accessible.name: qsTr("Settings")
                    readonly property bool selected: root.settingsPage
                    readonly property bool keyboardFocus: activeFocus && AppController.inputMode !== "pointer"
                    background: Rectangle {
                        radius: DesktopTokens.radius
                        color: settingsButton.selected ? DesktopTokens.raised
                            : settingsButton.hovered || settingsButton.keyboardFocus ? DesktopTokens.hover : "transparent"
                        border.width: settingsButton.keyboardFocus ? DesktopTokens.focusOutline : 0
                        border.color: Theme.label
                    }
                    contentItem: Item {
                        DesktopGlyph {
                            anchors.centerIn: parent
                            width: DesktopTokens.px(22); height: width
                            icon: "desktop-nav-settings.svg"
                            active: false
                        }
                    }
                    onClicked: root.routeRequested("settings")
                }
            }
        }

        Item {
            id: contentHost
            x: 0; y: root.headerOverlay ? 0 : root.headerHeight
            width: parent.width
            height: parent.height - y
            clip: true
        }
    }

    DesktopCollectionDialog {
        id: collectionDialog
        onCollectionOpened: collectionId => {
            ShellStore.activeCollectionId = collectionId
            root.routeRequested("library")
        }
    }

    Keys.onPressed: event => {
        if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_K) {
            root.commandPaletteRequested()
            event.accepted = true
        } else if ((event.modifiers & Qt.ControlModifier) && event.key === Qt.Key_Comma) {
            root.routeRequested("settings")
            event.accepted = true
        } else if (event.key === Qt.Key_Slash && root.searchVisible) {
            search.forceActiveFocus()
            event.accepted = true
        }
    }
}
