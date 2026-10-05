pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import QtQuick.Shapes
import QtQuick.Window
import OpenNOW

FocusScope {
    id: root
    objectName: "desktopSignInScreen"
    property bool providerOpen: false
    property bool qrRequested: false
    property bool staySignedIn: true
    property double clockMs: Date.now()
    property double challengeReceivedAt: Date.now()
    readonly property var challenge: ShellStore.authChallenge
    readonly property var providers: ShellStore.providers || []
    readonly property var selectedProvider: ShellStore.selectedProvider || {displayName:qsTr("Select a provider"), idpId:ShellStore.selectedProviderIdpId, region:""}
    readonly property bool waiting: ShellStore.authState === "starting" || ShellStore.authState === "waiting" || ShellStore.authState === "completing"
    readonly property bool failed: ShellStore.authState === "error"
    readonly property bool wideLayout: width >= DesktopTokens.px(1240)
    readonly property color mutedInk: DesktopTokens.textMuted
    readonly property color faintInk: DesktopTokens.textMuted
    readonly property color bodyInk: DesktopTokens.textBody
    readonly property color cardSeam: DesktopTokens.seam
    readonly property bool hasExpiry: challenge !== null && Number.isFinite(Number(challenge.expiresAt))
    readonly property int secondsLeft: hasExpiry ? Math.max(0, Math.ceil((Number(challenge.expiresAt) - clockMs) / 1000)) : 0
    readonly property string timeLeft: Math.floor(secondsLeft / 60) + ":" + String(secondsLeft % 60).padStart(2, "0")
    signal signedIn()
    signal offlineRequested()

    focus: true
    Timer { interval: 1000; repeat: true; running: root.challenge !== null; onTriggered: root.clockMs = Date.now() }

    function revealFocusedControl() {
        const win = root.Window.window
        const focused = win ? win.activeFocusItem : null
        if (!focused)
            return
        let ancestor = focused
        while (ancestor && ancestor !== body)
            ancestor = ancestor.parent
        if (!ancestor)
            return
        const point = focused.mapToItem(viewport.contentItem, 0, 0)
        const margin = DesktopTokens.px(16)
        let offset = viewport.contentY
        if (point.y < offset + margin)
            offset = point.y - margin
        else if (point.y + focused.height > offset + viewport.height - margin)
            offset = point.y + focused.height - viewport.height + margin
        viewport.contentY = Math.max(0, Math.min(Math.max(0, viewport.contentHeight - viewport.height), offset))
    }

    Connections {
        target: root.Window.window
        function onActiveFocusItemChanged() { Qt.callLater(root.revealFocusedControl) }
    }

    component BodyText: Text {
        color: root.bodyInk
        font.family: DesktopTokens.bodyFont
        font.pixelSize: DesktopTokens.px(14)
        font.weight: Font.Normal
        wrapMode: Text.WordWrap
        lineHeight: DesktopTokens.px(20)
        lineHeightMode: Text.FixedHeight
        height: text.length > 0 ? Math.max(1, lineCount) * lineHeight : 0
    }

    // Small secondary label (sentence case, body font).
    component MonoText: Text {
        color: root.faintInk
        font.family: DesktopTokens.bodyFont
        font.pixelSize: DesktopTokens.px(13)
        font.weight: Font.DemiBold
        lineHeight: DesktopTokens.px(16)
        lineHeightMode: Text.FixedHeight
        height: text.length > 0 ? Math.max(1, lineCount) * lineHeight : 0
    }

    component AuthButton: DesktopButton {
        id: action
        property bool quiet: false
        property bool external: false
        height: DesktopTokens.px(44)
        implicitWidth: Math.max(DesktopTokens.px(68), contentItem.implicitWidth + leftPadding + rightPadding)
        font.pixelSize: DesktopTokens.px(14)
        contentItem: Item {
            implicitWidth: actionContents.implicitWidth
            implicitHeight: actionContents.implicitHeight
            Row {
                id: actionContents
                anchors.centerIn: parent
                spacing: DesktopTokens.px(10)
                DesktopGlyph { visible: action.glyph !== ""; anchors.verticalCenter: parent.verticalCenter; width: action.glyphSize; height: action.glyphSize; icon: action.glyph }
                BodyText { anchors.verticalCenter: parent.verticalCenter; text: action.text; color: action.quiet ? root.bodyInk : action.ink; font: action.font }
                BodyText { visible: action.external; anchors.verticalCenter: parent.verticalCenter; text: "↗"; color: action.ink; font: action.font }
            }
        }
    }

    component HeaderLink: AbstractButton {
        id: link
        property string destination: ""
        property string explanation: ""
        implicitWidth: linkText.implicitWidth
        implicitHeight: DesktopTokens.px(24)
        focusPolicy: Qt.StrongFocus
        hoverEnabled: true
        contentItem: BodyText {
            id: linkText
            text: link.text; color: DesktopTokens.focus
            font.weight: Font.Bold; lineHeight: DesktopTokens.px(16)
            verticalAlignment: Text.AlignVCenter
        }
        background: Rectangle {
            radius: DesktopTokens.radius
            color: link.hovered ? DesktopTokens.raised : "transparent"
            border.width: link.activeFocus ? DesktopTokens.px(3) : 0; border.color: Theme.label
        }
        onClicked: {
            if (destination !== "")
                Qt.openUrlExternally(destination)
            else
                explanationPopup.open()
        }
        Popup {
            id: explanationPopup
            parent: Overlay.overlay
            x: Math.max(DesktopTokens.px(24), root.width - width - DesktopTokens.px(24))
            y: DesktopTokens.px(64)
            width: Math.min(DesktopTokens.px(360), root.width - DesktopTokens.px(48))
            padding: DesktopTokens.px(20)
            focus: true
            closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
            background: Rectangle { radius: DesktopTokens.radiusLarge; color: Theme.surfaceRaised; border.color: Theme.seam }
            contentItem: BodyText { text: link.explanation }
        }
    }

    DesktopOnboardingBackdrop { anchors.fill: parent }

    // Wide screens split like a streaming service's sign-in: the mascot (or, until her
    // art ships, the Cloudlight emblem) on a flat panel at the left, the form at the right.
    readonly property real artWidth: wideLayout ? Math.round(width * 0.5) : 0
    component Sparkle: Shape {
        id: sparkle
        property real size: DesktopTokens.px(16)
        property color ink: "#B9A9D9"
        readonly property real c: size / 2
        readonly property real k: size * 0.07
        width: size; height: size
        preferredRendererType: Shape.CurveRenderer
        ShapePath {
            strokeWidth: -1
            fillColor: sparkle.ink
            startX: sparkle.c; startY: 0
            PathCubic { x: sparkle.size; y: sparkle.c; control1X: sparkle.c + sparkle.k; control1Y: sparkle.c - sparkle.k; control2X: sparkle.c + sparkle.k; control2Y: sparkle.c - sparkle.k }
            PathCubic { x: sparkle.c; y: sparkle.size; control1X: sparkle.c + sparkle.k; control1Y: sparkle.c + sparkle.k; control2X: sparkle.c + sparkle.k; control2Y: sparkle.c + sparkle.k }
            PathCubic { x: 0; y: sparkle.c; control1X: sparkle.c - sparkle.k; control1Y: sparkle.c + sparkle.k; control2X: sparkle.c - sparkle.k; control2Y: sparkle.c + sparkle.k }
            PathCubic { x: sparkle.c; y: 0; control1X: sparkle.c - sparkle.k; control1Y: sparkle.c - sparkle.k; control2X: sparkle.c - sparkle.k; control2Y: sparkle.c - sparkle.k }
        }
    }
    Rectangle {
        id: artPanel
        objectName: "signInArtPanel"
        visible: root.wideLayout
        width: root.artWidth
        height: root.height
        color: Theme.surface

        Repeater {
            model: [
                {x: 0.14, y: 0.22, s: 18}, {x: 0.80, y: 0.16, s: 12}, {x: 0.88, y: 0.58, s: 22},
                {x: 0.10, y: 0.70, s: 10}, {x: 0.62, y: 0.86, s: 14}, {x: 0.30, y: 0.42, s: 8}
            ]
            Sparkle {
                required property var modelData
                x: Math.round(artPanel.width * modelData.x)
                y: Math.round(artPanel.height * modelData.y)
                size: DesktopTokens.px(modelData.s)
                ink: Theme.lightMode ? "#7E6BA8" : "#B9A9D9"
                opacity: 0.8
            }
        }

        CloudlightMascot {
            id: signInMascot
            objectName: "signInMascot"
            pose: "login"
            anchors.horizontalCenter: parent.horizontalCenter
            y: hasArt ? parent.height - height : Math.round(parent.height * 0.24)
            width: hasArt ? Math.round(parent.width * 0.9) : Math.round(parent.width * 0.5)
            height: hasArt ? Math.round(parent.height * 0.86) : width
            emblemScale: 0.8
        }

        Column {
            visible: !signInMascot.hasArt
            anchors.horizontalCenter: parent.horizontalCenter
            y: signInMascot.y + signInMascot.height + DesktopTokens.px(12)
            width: Math.min(parent.width - DesktopTokens.px(96), DesktopTokens.px(560))
            spacing: DesktopTokens.px(14)
            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                text: qsTr("Welcome home.")
                color: Theme.label
                font.family: Theme.brandFont
                font.pixelSize: DesktopTokens.px(56)
                font.weight: Font.Bold
            }
            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                text: qsTr("Sign in once, and your GeForce NOW library is ready on the big screen.")
                color: DesktopTokens.textMuted
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.bodySize
                wrapMode: Text.WordWrap
                lineHeight: 1.3
            }
        }
    }

    Item {
        id: topStrip
        width: parent.width
        height: DesktopTokens.px(72)
        Row {
            x: DesktopTokens.px(root.wideLayout ? 40 : 24)
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(10)
            DesktopBrandLockup { anchors.verticalCenter: parent.verticalCenter }
            Row {
                anchors.verticalCenter: parent.verticalCenter
                spacing: DesktopTokens.px(6)
                leftPadding: DesktopTokens.px(4)
                MonoText {
                    id: versionLabel
                    anchors.verticalCenter: parent.verticalCenter
                    text: (Qt.application.version || qsTr("unknown")) + qsTr(" · Beta")
                    font.weight: Font.Normal
                }
            }
        }
        Row {
            visible: root.width >= DesktopTokens.px(700)
            anchors.right: parent.right
            anchors.rightMargin: DesktopTokens.px(root.wideLayout ? 40 : 24)
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(22)
            HeaderLink {
                text: qsTr("Why an account?")
                explanation: qsTr("Cloudlight is a native client for GeForce NOW and its alliance partners. Sign in with your provider to access your library, browse the stores and connect with friends.")
            }
            HeaderLink { text: qsTr("Source"); destination: "https://github.com/OpenCloudGaming/OpenNOW" }
            HeaderLink {
                text: qsTr("Privacy")
                explanation: qsTr("Cloudlight never sees your password. Sign-in happens on your provider's own page and only a session token comes back.")
                    + "\n\n" + qsTr("Cloudlight prefers the OS keychain for saved session tokens. If it is unavailable, tokens are saved unencrypted on disk.")
            }
        }
    }

    Flickable {
        id: viewport
        objectName: "signInScroll"
        anchors.top: topStrip.bottom
        anchors.bottom: footer.top
        width: parent.width
        contentWidth: width
        contentHeight: Math.max(height, body.height + DesktopTokens.px(48))
        flickableDirection: Flickable.VerticalFlick
        boundsBehavior: Flickable.StopAtBounds
        clip: true
        ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }

        Item {
            id: body
            x: root.artWidth + (viewport.width - root.artWidth - width) / 2
            y: height <= viewport.height ? (viewport.height - height) / 2 : DesktopTokens.px(24)
            width: Math.max(0, Math.min(DesktopTokens.px(460), viewport.width - root.artWidth - DesktopTokens.px(48)))
            height: card.height

            Rectangle {
                id: card
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.verticalCenter: parent.verticalCenter
                width: Math.min(DesktopTokens.px(460), parent.width)
                height: cardColumn.implicitHeight + 2
                radius: DesktopTokens.radiusLarge
                // Beside the art panel the form sits straight on the page; alone, it keeps its card.
                color: root.wideLayout ? "transparent" : Theme.surface
                border.width: root.wideLayout ? 0 : 0
                border.color: root.cardSeam

                Column {
                    id: cardColumn
                    x: 1
                    y: 1
                    width: parent.width - 2

                    Column {
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        topPadding: DesktopTokens.px(26)
                        bottomPadding: DesktopTokens.px(18)
                        spacing: DesktopTokens.px(8)
                        MonoText {
                            text: root.failed ? qsTr("Sign-in failed") : root.waiting ? qsTr("Waiting for approval") : qsTr("Not signed in")
                            color: root.failed ? DesktopTokens.danger : root.waiting ? DesktopTokens.amber : root.faintInk
                        }
                        BodyText {
                            width: parent.width
                            text: root.failed ? qsTr("We could not finish sign in")
                                : root.waiting ? (root.qrRequested ? qsTr("Scan to sign in") : qsTr("Finish in your browser")) : qsTr("Sign in to continue")
                            color: DesktopTokens.text
                            font.family: DesktopTokens.displayFont
                            font.pixelSize: DesktopTokens.px(26)
                            font.weight: Font.Bold
                            lineHeight: DesktopTokens.px(30)
                            topPadding: -DesktopTokens.px(3)
                        }
                        BodyText {
                            width: parent.width
                            text: root.failed ? (ShellStore.authMessage || qsTr("Your provider returned without a usable session. Nothing was saved."))
                                : root.waiting ? (root.qrRequested ? qsTr("Scan with your phone and approve the request on your provider. This screen moves on by itself.") : qsTr("A secure provider page is open. Approve it there and return to Cloudlight."))
                                : qsTr("Nothing works until your provider tells us who you are.")
                        }
                    }

                    Column {
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        bottomPadding: DesktopTokens.px(24)
                        visible: ShellStore.savedAccounts.length > 0
                        AuthButton {
                            objectName: "savedAccountsButton"
                            width: parent.width
                            enabled: ShellStore.ready
                            text: qsTr("Saved accounts")
                            onClicked: {
                                ShellStore.cancelDeviceLogin()
                                root.providerOpen = false
                                AppController.navigate("accounts")
                            }
                        }
                    }

                    Column {
                        id: providerColumn
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        spacing: DesktopTokens.px(8)
                        bottomPadding: DesktopTokens.px(18)
                        visible: !root.waiting && !root.failed
                        MonoText { text: qsTr("Provider") }
                        BodyText {
                            objectName: "providerDiscoveryNotice"
                            width: parent.width
                            visible: ShellStore.providerDiscoveryDegraded
                            text: root.providers.length
                                ? qsTr("Provider discovery is unavailable. Known providers are shown. Refresh to try again.")
                                : qsTr("No providers are available. Refresh to try again.")
                            color: DesktopTokens.textMuted
                        }
                        AuthButton {
                            width: parent.width
                            visible: ShellStore.providerDiscoveryDegraded
                            text: qsTr("Refresh providers")
                            enabled: ShellStore.ready && ShellStore.providersRequestId === ""
                            onClicked: ShellStore.refreshProviders(true)
                        }
                        ItemDelegate {
                            id: providerButton
                            focusPolicy: Qt.StrongFocus
                            width: parent.width
                            height: DesktopTokens.px(56)
                            padding: 0
                            Accessible.name: String(root.selectedProvider.displayName || "NVIDIA · GeForce NOW")
                            background: Rectangle {
                                radius: DesktopTokens.radius
                                color: providerButton.hovered || providerButton.activeFocus ? DesktopTokens.raisedStrong : DesktopTokens.raised
                                border.width: providerButton.activeFocus ? DesktopTokens.px(3) : 0
                                border.color: providerButton.activeFocus ? Theme.label : root.cardSeam
                            }
                            contentItem: Item {
                                Rectangle {
                                    x: DesktopTokens.px(14)
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: DesktopTokens.px(32)
                                    height: width
                                    radius: DesktopTokens.radius
                                    color: "#76B900"
                                    BodyText { anchors.centerIn: parent; text: String(root.selectedProvider.displayName || "").slice(0, 1).toUpperCase(); color: "#141414"; font.pixelSize: DesktopTokens.px(14); font.weight: Font.Bold }
                                }
                                Column {
                                    x: DesktopTokens.px(58)
                                    width: parent.width - DesktopTokens.px(100)
                                    anchors.verticalCenter: parent.verticalCenter
                                    spacing: DesktopTokens.px(2)
                                    BodyText { objectName: "signInProviderName"; width: parent.width; text: String(root.selectedProvider.displayName || "NVIDIA · GeForce NOW"); color: DesktopTokens.text; font.pixelSize: DesktopTokens.px(14); font.weight: Font.DemiBold; maximumLineCount: 1; elide: Text.ElideRight }
                                    MonoText { objectName: "signInProviderRegion"; width: parent.width; text: String(root.selectedProvider.region || qsTr("Global")) + qsTr(" · selected provider"); font.weight: Font.Normal; elide: Text.ElideRight }
                                }
                                DesktopGlyph {
                                    anchors.right: parent.right
                                    anchors.rightMargin: DesktopTokens.px(14)
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: DesktopTokens.px(14)
                                    height: DesktopTokens.px(14)
                                    icon: "desktop-chevron-down.svg"
                                    rotation: root.providerOpen ? 180 : 0
                                }
                            }
                            Component.onCompleted: if (root.visible) forceActiveFocus()
                            onClicked: root.providerOpen = !root.providerOpen
                        }
                        Row {
                            width: parent.width
                            spacing: DesktopTokens.px(12)
                            leftPadding: DesktopTokens.px(4)
                            rightPadding: DesktopTokens.px(4)
                            BodyText {
                                width: parent.width - DesktopTokens.px(8) - (moreProviders.visible ? moreProviders.width + parent.spacing : 0)
                                text: qsTr("Alliance partners like LG U+, Taiwan Mobile and bro.game run their own rigs.")
                                color: root.mutedInk
                                font.pixelSize: DesktopTokens.px(13)
                                lineHeight: DesktopTokens.px(18)
                            }
                            MonoText { id: moreProviders; anchors.verticalCenter: parent.verticalCenter; visible: root.providers.length > 1; text: qsTr("%1 more").arg(root.providers.length - 1); color: DesktopTokens.focus }
                        }
                        Rectangle {
                            width: parent.width
                            height: persistWarning.height + DesktopTokens.px(20)
                            visible: ShellStore.sessionPersistenceMessage !== ""
                            radius: DesktopTokens.radius
                            color: DesktopTokens.raised
                            border.width: 0
                            border.color: DesktopTokens.danger
                            BodyText { id: persistWarning; x: DesktopTokens.px(10); y: DesktopTokens.px(10); width: parent.width - DesktopTokens.px(20); text: ShellStore.sessionPersistenceMessage; font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
                        }
                    }

                    ItemDelegate {
                        id: persistenceButton
                        focusPolicy: Qt.StrongFocus
                        visible: !root.waiting && !root.failed
                        width: parent.width
                        height: Math.max(DesktopTokens.px(65), persistenceText.implicitHeight + DesktopTokens.px(30))
                        padding: 0
                        Accessible.name: qsTr("Stay signed in on this PC")
                        Accessible.role: Accessible.CheckBox
                        Accessible.checkable: true
                        Accessible.checked: root.staySignedIn
                        background: Rectangle {
                            color: persistenceButton.hovered || persistenceButton.activeFocus ? DesktopTokens.raised : "transparent"
                            Rectangle { visible: persistenceButton.activeFocus; width: DesktopTokens.px(4); height: parent.height; color: Theme.focus }
                            Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
                            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: DesktopTokens.seamSoft }
                        }
                        contentItem: Item {
                            Column {
                                id: persistenceText
                                x: DesktopTokens.px(26)
                                width: parent.width - DesktopTokens.px(112)
                                anchors.verticalCenter: parent.verticalCenter
                                spacing: DesktopTokens.px(2)
                                BodyText { width: parent.width; text: qsTr("Stay signed in on this PC"); color: DesktopTokens.text; font.weight: Font.DemiBold; lineHeight: DesktopTokens.px(17) }
                                BodyText { width: parent.width; text: qsTr("Cloudlight prefers the OS keychain for saved session tokens. If it is unavailable, tokens are saved unencrypted on disk."); color: root.mutedInk; font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
                            }
                            DesktopSettingsToggle {
                                anchors.right: parent.right
                                anchors.rightMargin: DesktopTokens.px(26)
                                anchors.verticalCenter: parent.verticalCenter
                                checked: root.staySignedIn
                                focusPolicy: Qt.NoFocus
                                onValueChangedByUser: value => root.staySignedIn = value
                            }
                        }
                        onClicked: root.staySignedIn = !root.staySignedIn
                    }

                    Column {
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        spacing: DesktopTokens.px(10)
                        topPadding: DesktopTokens.px(20)
                        visible: !root.waiting && !root.failed
                        AuthButton {
                            width: parent.width
                            height: DesktopTokens.px(48)
                            primary: true
                            external: true
                            font.pixelSize: DesktopTokens.px(14)
                            text: qsTr("Continue with %1").arg(root.selectedProvider.displayName)
                            enabled: ShellStore.ready && ShellStore.selectedProvider !== null
                            onClicked: ShellStore.startDeviceLogin(root.selectedProvider.idpId || "", root.staySignedIn)
                        }
                        AuthButton {
                            width: parent.width
                            glyph: "desktop-qr.svg"
                            glyphSize: DesktopTokens.px(14)
                            text: qsTr("Sign in with a QR code")
                            enabled: ShellStore.ready && ShellStore.selectedProvider !== null
                            onClicked: { root.qrRequested = true; ShellStore.startDeviceLogin(root.selectedProvider.idpId || "", root.staySignedIn) }
                        }
                    }

                    Row {
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        spacing: DesktopTokens.px(8)
                        topPadding: DesktopTokens.px(16)
                        bottomPadding: DesktopTokens.px(24)
                        visible: !root.waiting && !root.failed
                        Item {
                            width: DesktopTokens.px(12)
                            height: DesktopTokens.px(14)
                            Shape {
                                width: 12
                                height: 14
                                transform: Scale { xScale: DesktopTokens.uiScale; yScale: DesktopTokens.uiScale }
                                ShapePath {
                                    strokeColor: root.faintInk
                                    strokeWidth: 1.4
                                    fillColor: "transparent"
                                    joinStyle: ShapePath.RoundJoin
                                    PathSvg { path: "M6 1l4.5 1.8v3.6c0 3-2 5.2-4.5 6.1C3.5 11.6 1.5 9.4 1.5 6.4V2.8L6 1Z" }
                                }
                            }
                        }
                        BodyText {
                            width: parent.width - DesktopTokens.px(20)
                            text: qsTr("Cloudlight never sees your password. Sign-in happens on your provider's own page and only a session token comes back.")
                            color: root.faintInk
                            font.pixelSize: DesktopTokens.px(13)
                            lineHeight: DesktopTokens.px(18)
                        }
                    }

                    Column {
                        width: parent.width
                        visible: root.waiting
                        Item {
                            id: codeBlock
                            x: DesktopTokens.px(26)
                            width: parent.width - DesktopTokens.px(52)
                            readonly property bool stacked: width < DesktopTokens.px(340)
                            height: (stacked && qrBox.visible ? qrBox.height + DesktopTokens.px(22) + codeText.implicitHeight : Math.max(qrBox.visible ? qrBox.height : 0, codeText.implicitHeight)) + DesktopTokens.px(20)
                            Rectangle {
                                id: qrBox
                                visible: root.qrRequested
                                width: DesktopTokens.px(150)
                                height: width
                                x: codeBlock.stacked ? (parent.width - width) / 2 : 0
                                radius: DesktopTokens.radius
                                color: "#FFFFFF"
                                Grid {
                                    id: qrGrid
                                    anchors.centerIn: parent
                                    columns: root.challenge && root.challenge.qrRows ? root.challenge.qrRows.length : 0
                                    visible: columns > 0
                                    property var qrRows: root.challenge && root.challenge.qrRows ? root.challenge.qrRows : []
                                    property real cell: columns > 0 ? Math.floor(DesktopTokens.px(126) / columns) : 0
                                    Repeater {
                                        model: qrGrid.columns * qrGrid.columns
                                        Rectangle {
                                            required property int index
                                            width: qrGrid.cell
                                            height: qrGrid.cell
                                            color: qrGrid.qrRows[Math.floor(index / qrGrid.columns)].charAt(index % qrGrid.columns) === "1" ? "#000000" : "#FFFFFF"
                                        }
                                    }
                                }
                                MonoText { anchors.centerIn: parent; visible: !qrGrid.visible; text: "QR"; color: "#000000"; font.pixelSize: DesktopTokens.px(30); lineHeight: DesktopTokens.px(36) }
                            }
                            Column {
                                id: codeText
                                x: qrBox.visible && !codeBlock.stacked ? qrBox.width + DesktopTokens.px(22) : 0
                                y: codeBlock.stacked && qrBox.visible ? qrBox.height + DesktopTokens.px(22) : Math.max(0, (qrBox.height - implicitHeight) / 2)
                                width: parent.width - x
                                spacing: DesktopTokens.px(10)
                                MonoText { width: parent.width; text: qsTr("Or enter this code"); wrapMode: Text.WordWrap }
                                MonoText {
                                    width: parent.width
                                    text: root.challenge ? String(root.challenge.userCode || "").toUpperCase() : qsTr("Creating secure code…")
                                    color: DesktopTokens.text
                                    font.family: root.challenge ? DesktopTokens.monoFont : DesktopTokens.bodyFont
                                    font.pixelSize: DesktopTokens.px(root.challenge ? 30 : 14)
                                    font.weight: Font.Bold
                                    font.letterSpacing: 0
                                    lineHeight: DesktopTokens.px(32)
                                    wrapMode: Text.WrapAnywhere
                                }
                                MonoText {
                                    width: parent.width
                                    text: root.challenge ? String(root.challenge.verificationUri || "").replace(/^https?:\/\//, "") : qsTr("Contacting provider")
                                    color: DesktopTokens.focus
                                    font.pixelSize: DesktopTokens.px(14)
                                    lineHeight: DesktopTokens.px(18)
                                    wrapMode: Text.WrapAnywhere
                                }
                                Column {
                                    width: parent.width
                                    visible: root.hasExpiry
                                    topPadding: DesktopTokens.px(4)
                                    spacing: DesktopTokens.px(6)
                                    Rectangle {
                                        width: parent.width
                                        height: DesktopTokens.px(3)
                                        radius: DesktopTokens.px(2)
                                        color: DesktopTokens.raised
                                        Rectangle { width: parent.width * Math.max(0, Math.min(1, (Number(root.challenge ? root.challenge.expiresAt : 0) - root.clockMs) / Math.max(1, Number(root.challenge ? root.challenge.expiresAt : 0) - root.challengeReceivedAt))); height: parent.height; radius: parent.radius; color: DesktopTokens.amber }
                                    }
                                    MonoText { width: parent.width; text: qsTr("Code expires in %1").arg(root.timeLeft); color: root.mutedInk; font.weight: Font.Normal; wrapMode: Text.WordWrap }
                                }
                            }
                        }
                        Rectangle {
                            width: parent.width
                            height: Math.max(DesktopTokens.px(66), approvalStatus.height + DesktopTokens.px(30))
                            color: DesktopTokens.raised
                            Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
                            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: DesktopTokens.seamSoft }
                            Item {
                                x: DesktopTokens.px(26)
                                anchors.verticalCenter: parent.verticalCenter
                                width: DesktopTokens.px(16)
                                height: width
                                Shape {
                                    width: 16
                                    height: 16
                                    transform: Scale { xScale: DesktopTokens.uiScale; yScale: DesktopTokens.uiScale }
                                    ShapePath { strokeColor: DesktopTokens.amber; strokeWidth: 1.8; fillColor: "transparent"; capStyle: ShapePath.RoundCap; PathSvg { path: "M8 2a6 6 0 1 1-6 6" } }
                                }
                                RotationAnimator on rotation { from: 0; to: 360; duration: 1200; running: root.waiting && !AppController.reducedMotion; loops: Animation.Infinite }
                            }
                            BodyText { id: approvalStatus; x: DesktopTokens.px(52); anchors.verticalCenter: parent.verticalCenter; width: parent.width - DesktopTokens.px(78); text: ShellStore.authMessage || qsTr("Waiting for approval…") }
                        }
                        Row {
                            x: DesktopTokens.px(26)
                            width: parent.width - DesktopTokens.px(52)
                            spacing: DesktopTokens.px(10)
                            topPadding: DesktopTokens.px(20)
                            bottomPadding: DesktopTokens.px(24)
                            AuthButton {
                                width: parent.width - cancelButton.width - parent.spacing
                                external: true
                                text: root.qrRequested ? qsTr("Open sign-in page") : qsTr("Open provider page")
                                onClicked: if (root.challenge) Qt.openUrlExternally(root.challenge.verificationUriComplete || root.challenge.verificationUri)
                            }
                            AuthButton { id: cancelButton; width: Math.max(DesktopTokens.px(78), implicitWidth); quiet: true; text: qsTr("Cancel"); onClicked: { ShellStore.cancelDeviceLogin(); root.qrRequested = false } }
                        }
                    }

                    Column {
                        x: DesktopTokens.px(26)
                        width: parent.width - DesktopTokens.px(52)
                        spacing: DesktopTokens.px(16)
                        bottomPadding: DesktopTokens.px(24)
                        visible: root.failed
                        Rectangle {
                            width: parent.width
                            height: failureText.height + DesktopTokens.px(32)
                            radius: DesktopTokens.radius
                            color: DesktopTokens.raised
                            Rectangle { width: DesktopTokens.px(4); height: parent.height; color: DesktopTokens.danger }
                            BodyText { id: failureText; x: DesktopTokens.px(20); y: DesktopTokens.px(16); width: parent.width - DesktopTokens.px(36); text: qsTr("The authorization was cancelled or expired. Your previous account state is unchanged.") }
                        }
                        Row {
                            width: parent.width
                            spacing: DesktopTokens.px(10)
                            AuthButton { width: (parent.width - parent.spacing) * 0.55; primary: true; text: qsTr("Try again"); onClicked: { ShellStore.authState = "idle"; ShellStore.startDeviceLogin(root.selectedProvider.idpId || "", root.staySignedIn) } }
                            AuthButton { width: (parent.width - parent.spacing) * 0.45; text: qsTr("Choose provider"); onClicked: { ShellStore.authState = "idle"; root.providerOpen = true } }
                        }
                        Rectangle {
                            width: parent.width
                            height: retryNotes.implicitHeight + DesktopTokens.px(24)
                            radius: DesktopTokens.radius
                            color: DesktopTokens.raised
                            Column {
                                id: retryNotes
                                x: DesktopTokens.px(12)
                                y: DesktopTokens.px(12)
                                width: parent.width - DesktopTokens.px(24)
                                spacing: DesktopTokens.px(8)
                                MonoText { width: parent.width; text: qsTr("Before you try again, check that:"); wrapMode: Text.WordWrap }
                                BodyText { width: parent.width; text: qsTr("Your internet connection is available"); font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
                                BodyText { width: parent.width; text: qsTr("Pop-ups are allowed in your browser"); font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
                                BodyText { width: parent.width; text: qsTr("You selected the correct alliance provider"); font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
                            }
                        }
                    }
                }

                Rectangle {
                    id: providerMenu
                    x: cardColumn.x + providerColumn.x
                    y: cardColumn.y + providerColumn.y + providerButton.y + providerButton.height + DesktopTokens.px(6)
                    width: providerColumn.width
                    height: Math.min(DesktopTokens.px(260), root.providers.length * DesktopTokens.px(52) + DesktopTokens.px(16))
                    MotionProgress { id: providerMotion; shown: root.providerOpen && !root.waiting && !root.failed; enterDuration: 120; exitDuration: 120 }
                    visible: providerMotion.present
                    enabled: root.providerOpen && !root.waiting && !root.failed
                    z: 20
                    radius: DesktopTokens.radius
                    color: Theme.surfaceRaised
                    border.width: 0
                    border.color: root.cardSeam
                    ListView {
                        anchors.fill: parent
                        anchors.margins: DesktopTokens.px(8)
                        clip: true
                        model: root.providers
                        delegate: ItemDelegate {
                            focusPolicy: Qt.StrongFocus
                            id: providerOption
                            required property var modelData
                            width: ListView.view.width
                            height: DesktopTokens.px(52)
                            Accessible.name: modelData.displayName || qsTr("Provider")
                            background: Rectangle { radius: DesktopTokens.radius; color: providerOption.hovered || providerOption.activeFocus ? DesktopTokens.raisedStrong : "transparent"; border.width: providerOption.activeFocus ? DesktopTokens.px(3) : 0; border.color: Theme.label }
                            contentItem: Column {
                                spacing: DesktopTokens.px(2)
                                BodyText { width: parent.width; text: providerOption.modelData.displayName || qsTr("Provider"); color: DesktopTokens.text; font.weight: Font.DemiBold; maximumLineCount: 1; elide: Text.ElideRight }
                                MonoText { text: String(providerOption.modelData.region || qsTr("Global")); font.weight: Font.Normal }
                            }
                            onClicked: { root.providerOpen = false; ShellStore.startDeviceLogin(modelData.idpId || "", root.staySignedIn) }
                        }
                    }
                    opacity: providerMotion.progress
                    scale: providerMotion.zoom
                    transformOrigin: Item.TopLeft
                }
            }
        }
    }

    Item {
        id: footer
        anchors.bottom: parent.bottom
        x: root.artWidth
        width: parent.width - root.artWidth
        height: DesktopTokens.px(72)
        Rectangle { width: parent.width; height: 1; color: DesktopTokens.seamSoft }
        Row {
            x: DesktopTokens.px(root.wideLayout ? 40 : 24)
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(18)
            Row {
                spacing: DesktopTokens.px(8)
                Rectangle {
                    anchors.verticalCenter: parent.verticalCenter
                    width: enterLabel.implicitWidth + DesktopTokens.px(12)
                    height: DesktopTokens.px(22)
                    radius: DesktopTokens.radius
                    color: DesktopTokens.raised
                    MonoText { id: enterLabel; anchors.centerIn: parent; text: qsTr("Enter"); color: root.bodyInk }
                }
                BodyText { anchors.verticalCenter: parent.verticalCenter; text: qsTr("Activate focused action"); color: root.mutedInk; font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
            }
            Row {
                spacing: DesktopTokens.px(8)
                Rectangle {
                    anchors.verticalCenter: parent.verticalCenter
                    width: tabLabel.implicitWidth + DesktopTokens.px(12)
                    height: DesktopTokens.px(22)
                    radius: DesktopTokens.radius
                    color: DesktopTokens.raised
                    MonoText { id: tabLabel; anchors.centerIn: parent; text: qsTr("Tab"); color: root.bodyInk }
                }
                BodyText { anchors.verticalCenter: parent.verticalCenter; text: qsTr("Move focus"); color: root.mutedInk; font.pixelSize: DesktopTokens.px(13); lineHeight: DesktopTokens.px(18) }
            }
        }
        Row {
            visible: root.wideLayout
            anchors.right: parent.right
            anchors.rightMargin: DesktopTokens.px(40)
            anchors.verticalCenter: parent.verticalCenter
            spacing: DesktopTokens.px(8)
            MonoText { text: root.waiting ? qsTr("Approve the sign-in on your phone or in the browser") : qsTr("Not signed in"); font.weight: Font.Normal }
        }
    }

    Connections {
        target: ShellStore
        function onSignedInChanged() { if (ShellStore.signedIn && !ShellStore.addingAccount) root.signedIn() }
        function onAuthChallengeChanged() {
            root.challengeReceivedAt = Date.now()
            root.clockMs = root.challengeReceivedAt
            if (ShellStore.authChallenge && !root.qrRequested)
                Qt.openUrlExternally(ShellStore.authChallenge.verificationUriComplete || ShellStore.authChallenge.verificationUri)
        }
    }
}
