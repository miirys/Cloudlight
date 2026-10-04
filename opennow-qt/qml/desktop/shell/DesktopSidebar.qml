import QtQuick
import QtQuick.Controls
import QtQuick.Window
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopSidebar"
    property string currentRoute: "home"
    property bool collapsed: true
    property bool hoverExpanded: false
    readonly property bool overlayOpen: !collapsed || hoverExpanded
    readonly property bool compact: !overlayOpen
    readonly property real reveal: Math.max(0, Math.min(1, (width - DesktopTokens.railCollapsedWidth) / (DesktopTokens.railWidth - DesktopTokens.railCollapsedWidth)))
    readonly property bool consoleModeOn: DesktopTokens.consoleModeOn(Window.window)
    readonly property bool consoleModePending: DesktopTokens.consoleModePending(Window.window)
    readonly property bool friendsAvailable: Boolean(ShellStore.socialCapabilities && ShellStore.socialCapabilities.friendsAvailable)
    signal routeRequested(string route)
    signal consoleModeRequested()
    signal collapseRequested(bool collapsed)
    signal createCollectionRequested()

    x: 0
    width: overlayOpen ? DesktopTokens.railWidth : DesktopTokens.railCollapsedWidth
    height: parent ? parent.height : 900
    z: overlayOpen ? 40 : 3

    function closeOverlay() {
        hoverExpanded = false
        if (!collapsed)
            collapseRequested(true)
    }

    Behavior on width {
        NumberAnimation { duration: AppController.reducedMotion ? 0 : DesktopTokens.motionDuration; easing.type: Easing.OutCubic }
    }

    readonly property var navItems: [
        { route: "home", icon: "desktop-nav-home.svg", name: qsTr("Home") },
        { route: "library", icon: "desktop-nav-library.svg", name: qsTr("Library") },
        { route: "store", icon: "desktop-nav-store.svg", name: qsTr("Store") },
        { route: "friends", icon: "desktop-nav-friends.svg", name: qsTr("Friends") },
        { route: "settings", icon: "desktop-nav-settings.svg", name: qsTr("Settings") }
    ]

    function tierLabel(raw) {
        const tier = String(raw)
        return tier.charAt(0).toUpperCase() + tier.slice(1).toLowerCase()
    }
    function liveMembershipTier() {
        // The login claim goes stale (e.g. upgrade after sign-in); the live
        // subscription is authoritative, the cached claim is the fallback.
        if (ShellStore.subscription && ShellStore.subscription.membershipTier)
            return root.tierLabel(ShellStore.subscription.membershipTier)
        if (ShellStore.signedIn && ShellStore.authSession && ShellStore.authSession.user
                && ShellStore.authSession.user.membershipTier)
            return root.tierLabel(ShellStore.authSession.user.membershipTier)
        return ShellStore.signedIn ? qsTr("Member") : qsTr("Not signed in")
    }

    function routeSelected(route) {
        if (route === "settings")
            return root.currentRoute.indexOf("settings") === 0
        if (route === "library")
            return root.currentRoute === "library" || root.currentRoute === "game-detail"
        return root.currentRoute === route
    }

    Rectangle {
        anchors.fill: parent
        color: DesktopTokens.shell
        Rectangle {
            anchors.right: parent.right
            width: 1
            height: parent.height
            color: root.overlayOpen ? DesktopTokens.seam : DesktopTokens.seamSoft
        }
    }

    HoverHandler {
        acceptedDevices: PointerDevice.Mouse
        enabled: !SmokeTestMode && ShellStore.settings.desktopSidebarHover !== false
        onHoveredChanged: root.hoverExpanded = root.collapsed && hovered && ShellStore.settings.desktopSidebarHover !== false
    }

    Item {
        anchors.fill: parent
        clip: true
        anchors.topMargin: 16
        anchors.leftMargin: 14
        anchors.rightMargin: 14
        anchors.bottomMargin: 12

        Flickable {
            id: railFlick
            anchors.top: parent.top
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: dock.top
            anchors.bottomMargin: 8
            clip: true
            contentWidth: width
            contentHeight: topColumn.implicitHeight
            boundsBehavior: Flickable.StopAtBounds
            flickableDirection: Flickable.VerticalFlick

            Column {
                id: topColumn
                width: railFlick.width
                spacing: 10

        Item {
            width: parent.width
            height: 28

            DesktopBrandLockup {
                anchors.verticalCenter: parent.verticalCenter
                x: 2
                markHeight: 22
                fontPixelSize: DesktopTokens.headingSize
                spacing: 10
                textReveal: root.reveal
            }

            Rectangle {
                id: collapseButton
                visible: false
                width: 28
                height: 28
                anchors.verticalCenter: parent.verticalCenter
                anchors.right: parent.right
                radius: DesktopTokens.radius
                color: collapseHover.hovered || collapseButton.activeFocus ? DesktopTokens.raisedStrong : DesktopTokens.raised
                Accessible.role: Accessible.Button
                Accessible.name: qsTr("Collapse sidebar")
                Accessible.onPressAction: {
                    root.hoverExpanded = false
                    root.collapseRequested(true)
                }

                DesktopGlyph {
                    anchors.centerIn: parent
                    width: 13
                    height: 13
                    icon: "desktop-collapse.svg"
                }
                HoverHandler { id: collapseHover; cursorShape: Qt.PointingHandCursor }
                TapHandler {
                    onTapped: {
                        root.hoverExpanded = false
                        root.collapseRequested(true)
                    }
                }
            }
        }

        Item {
            width: parent.width
            height: 28
            Rectangle {
                id: expandButton
                width: 40
                height: 28
                x: 2
                radius: DesktopTokens.radius
                color: expandHover.hovered || expandButton.activeFocus ? DesktopTokens.raisedStrong : DesktopTokens.raised
                Accessible.role: Accessible.Button
                Accessible.name: root.overlayOpen ? qsTr("Collapse sidebar") : qsTr("Expand sidebar")
                Accessible.onPressAction: {
                    const closing = root.overlayOpen
                    root.hoverExpanded = false
                    root.collapseRequested(closing)
                }

                DesktopGlyph {
                    anchors.centerIn: parent
                    width: 13
                    height: 13
                    icon: root.overlayOpen ? "desktop-collapse.svg" : "desktop-expand.svg"
                }
                HoverHandler { id: expandHover; cursorShape: Qt.PointingHandCursor }
                TapHandler {
                    onTapped: {
                        const closing = root.overlayOpen
                        root.hoverExpanded = false
                        root.collapseRequested(closing)
                    }
                }
            }
        }

        Item {
            width: parent.width
            height: 1
            Rectangle {
                width: 28
                height: 1
                anchors.horizontalCenter: parent.horizontalCenter
                color: DesktopTokens.seamSoft
            }
        }

        Column {
            width: parent.width
            spacing: 3

            Repeater {
                model: root.navItems
                delegate: ItemDelegate {
                    id: navButton
                    required property var modelData
                    width: parent.width
                    height: 44
                    padding: 0
                    readonly property bool selected: root.routeSelected(modelData.route)
                    Accessible.name: modelData.name
                    // Selected: raised fill with an accent bar on the left. Keyboard focus adds
                    // a thick outline so it reads from across the room.
                    background: Rectangle {
                        radius: DesktopTokens.radius
                        color: navButton.selected ? DesktopTokens.raised
                            : (navButton.hovered || navButton.activeFocus ? DesktopTokens.hover : "transparent")
                        border.width: navButton.activeFocus ? DesktopTokens.px(3) : 0
                        border.color: Theme.label
                        Rectangle {
                            visible: navButton.selected
                            width: DesktopTokens.px(3)
                            height: parent.height - DesktopTokens.px(16)
                            anchors.verticalCenter: parent.verticalCenter
                            color: DesktopTokens.focus
                        }
                    }
                    contentItem: Item {
                        DesktopGlyph {
                            objectName: "sidebarIcon-" + navButton.modelData.route
                            x: 13
                            anchors.verticalCenter: parent.verticalCenter
                            width: 18
                            height: 18
                            icon: navButton.modelData.icon
                            active: navButton.selected
                        }
                        Text {
                            x: 48
                            anchors.verticalCenter: parent.verticalCenter
                            visible: root.reveal > 0
                        opacity: root.reveal
                            text: navButton.modelData.name
                            color: navButton.selected ? DesktopTokens.text : DesktopTokens.textBody
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.px(15)
                            font.weight: navButton.selected ? Font.DemiBold : Font.Medium
                        }
                    }
                    onClicked: {
                        if (modelData.route === "library")
                            ShellStore.activeCollectionId = ""
                        root.routeRequested(modelData.route)
                    }
                }
            }
        }

        Item {
            width: parent.width
            height: 44 + collectionRows.height
            Item {
                width: DesktopTokens.railWidth - 28
                height: 36
                Rectangle {
                    x: 8; width: 28; height: 1
                    anchors.verticalCenter: parent.verticalCenter
                    color: DesktopTokens.seamSoft
                }
                Text {
                    x: 48
                    anchors.verticalCenter: parent.verticalCenter
                    visible: root.reveal > 0
                    opacity: root.reveal
                    text: qsTr("Collections")
                    color: DesktopTokens.textMuted
                    font.family: DesktopTokens.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                    font.weight: Font.DemiBold
                }
                Button {
                    id: createCollectionButton
                    objectName: "createCollectionButton"
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    width: 32
                    height: 32
                    visible: root.reveal > 0
                    opacity: root.reveal
                    enabled: root.overlayOpen && !ShellStore.collectionsBusy
                    Accessible.name: qsTr("New collection")
                    ToolTip.visible: hovered
                    ToolTip.text: qsTr("New collection")
                    background: Rectangle { radius: DesktopTokens.radius; color: createCollectionButton.hovered || createCollectionButton.activeFocus ? DesktopTokens.raised : "transparent"; border.width: createCollectionButton.activeFocus ? DesktopTokens.px(3) : 0; border.color: Theme.label }
                    contentItem: DesktopGlyph {
                        width: createCollectionButton.availableWidth
                        height: createCollectionButton.availableHeight
                        icon: "desktop-plus.svg"
                    }
                    onClicked: root.createCollectionRequested()
                }
            }

            Column {
                id: collectionRows
                y: 40
                width: parent.width
                height: implicitHeight * root.reveal
                clip: true
                spacing: 1
            Repeater {
                model: ShellStore.gameCollections
                delegate: Button {
                    id: collectionRow
                    required property var modelData
                    width: parent.width
                    height: 36
                    opacity: root.reveal
                    enabled: root.overlayOpen
                    clip: true
                    Accessible.name: modelData.name
                    background: Rectangle {
                        radius: DesktopTokens.radius
                        color: ShellStore.activeCollectionId === collectionRow.modelData.id && root.currentRoute === "library"
                            ? DesktopTokens.raised : collectionRow.hovered || collectionRow.activeFocus ? DesktopTokens.hover : "transparent"
                        border.width: collectionRow.activeFocus ? DesktopTokens.px(3) : 0
                        border.color: Theme.label
                    }
                    contentItem: Item {
                    width: DesktopTokens.railWidth - 28
                    height: 36
                    DesktopGlyph {
                        objectName: "sidebarCollectionIcon-" + collectionRow.modelData.id
                        x: 9
                        anchors.verticalCenter: parent.verticalCenter
                        width: 16
                        height: width
                        icon: "desktop-nav-library.svg"
                    }
                    Text {
                        x: 42
                        width: parent.width - x - 44
                        elide: Text.ElideRight
                        textFormat: Text.PlainText
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        text: collectionRow.modelData.name
                        color: collectionRow.hovered ? DesktopTokens.text : DesktopTokens.textBody
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.px(14)
                        font.weight: Font.DemiBold
                    }
                    Text {
                        anchors.right: parent.right
                        anchors.rightMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        text: collectionRow.modelData.gameIds.length
                        color: DesktopTokens.textMuted
                        font.family: DesktopTokens.bodyFont
                        font.pixelSize: DesktopTokens.captionSize
                    }
                    }
                    onClicked: {
                        ShellStore.activeCollectionId = modelData.id
                        root.routeRequested("library")
                        root.closeOverlay()
                    }
                }
            }
                Text {
                    x: 8
                    width: parent.width - 16
                    visible: ShellStore.gameCollections.length === 0
                    text: qsTr("Create your first collection with +")
                    wrapMode: Text.WordWrap
                    color: DesktopTokens.textMuted
                    font.family: DesktopTokens.bodyFont
                    font.pixelSize: DesktopTokens.captionSize
                }
            }
        }
            }
        }

        Column {
            id: dock
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            spacing: 4

            Rectangle {
                width: parent.width
                height: 1
                color: DesktopTokens.seamSoft
            }

            ItemDelegate {
                id: consoleModeButton
                width: parent.width
                height: 44
                padding: 0
                enabled: !root.consoleModePending
                opacity: root.consoleModePending ? 0.7 : 1
                Accessible.name: qsTr("Console mode")
                Accessible.description: root.consoleModePending
                    ? qsTr("Switching surfaces")
                    : (root.consoleModeOn ? qsTr("Console mode is on") : qsTr("Console mode is off"))
                Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
                background: Rectangle {
                    radius: DesktopTokens.radius
                    color: consoleModeButton.hovered || consoleModeButton.activeFocus ? DesktopTokens.hover : "transparent"
                    border.width: consoleModeButton.activeFocus ? DesktopTokens.px(3) : 0
                    border.color: Theme.label
                }
                contentItem: Item {
                    DesktopGlyph {
                        x: 13.5
                        anchors.verticalCenter: parent.verticalCenter
                        width: 17
                        height: 17
                        icon: "desktop-gamepad.svg"
                    }
                    Column {
                        x: 48
                        width: Math.max(0, parent.width - x - 50)
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        spacing: 2
                        Text {
                            width: parent.width
                            elide: Text.ElideRight
                            text: root.consoleModePending ? qsTr("Switching…") : qsTr("Console mode")
                            color: DesktopTokens.textBody
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.px(14)
                            font.weight: Font.Medium
                        }
                    }
                    Rectangle {
                        anchors.right: parent.right
                        anchors.rightMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        width: 34
                        height: 20
                        radius: height / 2
                        color: root.consoleModeOn ? DesktopTokens.focus : DesktopTokens.raisedStrong
                        Rectangle {
                            x: root.consoleModeOn ? 17 : 3
                            y: 3
                            width: 14
                            height: 14
                            radius: 7
                            color: root.consoleModeOn ? Theme.focusText : DesktopTokens.text
                            Behavior on x { NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.OutCubic } }
                        }
                    }
                }
                onClicked: root.consoleModeRequested()
            }

            ItemDelegate {
                id: profileButton
                width: parent.width
                height: 44
                padding: 0
                Accessible.name: qsTr("Profile")
                background: Rectangle {
                    radius: DesktopTokens.radius
                    color: profileButton.hovered || profileButton.activeFocus ? DesktopTokens.hover : "transparent"
                    border.width: profileButton.activeFocus ? DesktopTokens.px(3) : 0
                    border.color: Theme.label
                }
                contentItem: Item {
                    Rectangle {
                        x: 4
                        anchors.verticalCenter: parent.verticalCenter
                        width: 36
                        height: 36
                        radius: width / 2
                        color: DesktopTokens.raisedStrong
                        Text {
                            anchors.centerIn: parent
                            text: ShellStore.signedIn && ShellStore.authSession.user
                                ? String(ShellStore.authSession.user.displayName || "?").charAt(0).toUpperCase()
                                : "Z"
                            color: DesktopTokens.textHigh
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.monoSize
                            font.weight: Font.Bold
                        }
                    }
                    Column {
                        x: 48
                        width: Math.max(0, parent.width - x - 28)
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        spacing: 2
                        Text {
                            width: parent.width
                            elide: Text.ElideRight
                            text: ShellStore.signedIn && ShellStore.authSession.user
                                ? ShellStore.authSession.user.displayName
                                : qsTr("Guest")
                            color: DesktopTokens.textHigh
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.captionSize
                            font.weight: Font.Bold
                        }
                        Text {
                            text: root.liveMembershipTier()
                            width: parent.width
                            elide: Text.ElideRight
                            color: DesktopTokens.textMuted
                            font.family: DesktopTokens.bodyFont
                            font.pixelSize: DesktopTokens.captionSize
                        }
                    }
                    DesktopGlyph {
                        anchors.right: parent.right
                        anchors.rightMargin: 10
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.reveal > 0
                        opacity: root.reveal
                        width: 10
                        height: 10
                        icon: "desktop-chevron-up.svg"
                    }
                }
                onClicked: root.routeRequested("settings-account")
            }
        }
    }
}
