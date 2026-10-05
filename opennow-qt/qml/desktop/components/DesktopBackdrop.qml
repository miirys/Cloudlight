import QtQuick
import QtQuick.Window
import QtQuick.Effects
import OpenNOW

Item {
    id: root
    objectName: root.signIn ? "signInBackdrop" : "desktopBackdrop"
    property string artwork: ShellStore.catalogGames.length
        ? DesktopTokens.artworkUrl(ShellStore.catalogGames[0], true) : ""
    property bool signIn: false
    readonly property bool customBackground: !root.signIn && ShellStore.settings.desktopBackground === "custom"
    readonly property string normalizedArtwork: root.artwork !== ""
        ? DesktopTokens.decodeArtworkUrl(root.artwork)
        : root.signIn ? "qrc:/qt/qml/OpenNOW/res/brand/signin-hero.jpg" : "qrc:/qt/qml/OpenNOW/res/brand/desktop-renew.jpg"

    ArtworkSource {
        id: artworkSource
        sourceUrl: root.normalizedArtwork
        active: root.visible && (root.signIn || String(ShellStore.settings.desktopBackground || "solid") === "art")
    }

    Rectangle { anchors.fill: parent; color: root.signIn ? Theme.shell : DesktopTokens.shell }

    // Behind dense pages such as Settings the picture is blurred and dimmed so the
    // text over it stays readable; the blur only runs while it is needed.
    property bool blurred: false
    property real blurAmount: blurred ? 1 : 0
    Behavior on blurAmount { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
    Item {
        id: picture
        anchors.fill: parent
        // Only blur when there is a picture to blur, and at half resolution: the
        // result is a soft wash either way, and a full-size 4K layer made opening
        // Settings hitch.
        readonly property bool hasPicture: art.opacity > 0 || customPicture.opacity > 0
        layer.enabled: root.blurAmount > 0 && hasPicture
        layer.textureSize: Qt.size(Math.max(1, Math.round(width / 2)), Math.max(1, Math.round(height / 2)))
        layer.smooth: true
        layer.effect: MultiEffect {
            blurEnabled: true
            blur: root.blurAmount
            blurMax: 32
            autoPaddingEnabled: false
        }
        Item {
            anchors.fill: parent
            clip: true
            Image {
                id: art
                width: parent.width
                height: parent.height * (root.signIn ? 1.18 : 1)
                y: root.signIn ? -parent.height * 0.12 : 0
                source: artworkSource.resolvedUrl
                asynchronous: true
                cache: true
                fillMode: Image.PreserveAspectCrop
                sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
                opacity: status === Image.Ready && (root.signIn || String(ShellStore.settings.desktopBackground || "solid") === "art") ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: DesktopTokens.revealDuration } }
            }
        }

        Image {
            id: customPicture
            objectName: "customDesktopBackground"
            anchors.fill: parent
            source: root.visible && root.customBackground ? String(ShellStore.settings.desktopBackgroundImage || "") : ""
            sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
            asynchronous: true
            fillMode: Image.PreserveAspectCrop
            opacity: status === Image.Ready ? Number(ShellStore.settings.desktopBackgroundOpacity ?? 30) / 100 : 0
        }
    }
    Rectangle {
        anchors.fill: parent
        color: DesktopTokens.shell
        opacity: 0.4 * root.blurAmount
        visible: opacity > 0
    }

    Rectangle {
        visible: root.signIn
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.72) }
            GradientStop { position: 0.4; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.6) }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.84) }
        }
    }
    Rectangle {
        visible: root.signIn
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.95) }
            GradientStop { position: 0.5; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0) }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, 0.95) }
        }
    }

    Rectangle {
        anchors.fill: parent
        // Only the explicit "gradient" background gets an accent wash; it sits
        // above the darkening layers used for artwork.
        visible: !root.signIn && !root.customBackground && String(ShellStore.settings.desktopBackground || "solid") === "gradient"
        z: 1
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(Theme.focus.r, Theme.focus.g, Theme.focus.b, 0.22) }
            GradientStop { position: 0.4; color: "transparent" }
        }
    }
    Rectangle {
        visible: !root.signIn
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, root.customBackground ? 0.82 : 0.92) }
            GradientStop { position: 0.34; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, root.customBackground ? 0.12 : 0.78) }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, root.customBackground ? 0.24 : 0.94) }
        }
    }
    Rectangle {
        visible: !root.signIn
        anchors.fill: parent
        gradient: Gradient {
            GradientStop { position: 0; color: "#00000000" }
            GradientStop { position: 1; color: Qt.rgba(Theme.shell.r, Theme.shell.g, Theme.shell.b, root.customBackground ? 0.18 : 0.55) }
        }
    }
}
