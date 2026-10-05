import QtQuick
import QtQuick.Window
import OpenNOW

Item {
    id: root
    property string artwork: ""
    property color fallbackColor: Theme.cartSteam
    property real cornerRadius: Theme.radiusLarge
    property real scrimStart: 0.28

    property bool loadStarted: false
    readonly property string normalizedArtwork: DesktopTokens.decodeArtworkUrl(root.artwork)
    onNormalizedArtworkChanged: if (!visible) loadStarted = false
    // Retained popups keep their resolved artwork between opens, avoiding a
    // fallback flash and another image fade every time visibility changes.
    onVisibleChanged: if (visible) loadStarted = true
    Component.onCompleted: if (visible)
        loadStarted = true

    ArtworkSource {
        id: artworkSource
        sourceUrl: root.normalizedArtwork
        active: root.loadStarted
        requestActive: root.visible
    }

    // One shader node per tile: the image's own texture, cover-cropped and
    // rounded in the fragment shader. The previous mask/MultiEffect version
    // allocated two offscreen layers per tile, which made rails stutter as
    // they created delegates.
    Image {
        id: picture
        // Kept visible at zero opacity: an Image only refreshes its texture
        // provider while it is part of the scene; the renderer skips drawing it.
        width: 1; height: 1
        opacity: 0
        source: artworkSource.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        sourceSize: Qt.size(Math.ceil(root.width * Screen.devicePixelRatio), Math.ceil(root.height * Screen.devicePixelRatio))
        asynchronous: true
        cache: true
        smooth: true
    }
    property real imageAmount: picture.status === Image.Ready ? 1 : 0
    Behavior on imageAmount { NumberAnimation { duration: Theme.heroDuration } }

    ShaderEffect {
        anchors.fill: parent
        readonly property var source: picture
        readonly property size itemSize: Qt.size(Math.max(1, width), Math.max(1, height))
        readonly property real imageAspect: picture.implicitHeight > 0 ? picture.implicitWidth / picture.implicitHeight : 1
        readonly property real itemAspect: itemSize.width / itemSize.height
        // Cover crop, centred, matching Image.PreserveAspectCrop.
        readonly property rect cover: imageAspect > itemAspect
            ? Qt.rect((1 - itemAspect / imageAspect) / 2, 0, itemAspect / imageAspect, 1)
            : Qt.rect(0, (1 - imageAspect / itemAspect) / 2, 1, imageAspect / itemAspect)
        // Stay a texel inside the decoded edges: scaled JPEG decodes can leave a
        // light last row or column.
        readonly property real insetU: cover.width * 1.5 / Math.max(1, itemSize.width * pixelScale)
        readonly property real insetV: cover.height * 1.5 / Math.max(1, itemSize.height * pixelScale)
        readonly property rect uvRect: Qt.rect(cover.x + insetU, cover.y + insetV,
                                               cover.width - 2 * insetU, cover.height - 2 * insetV)
        readonly property color fallback: root.fallbackColor
        readonly property real radius: root.cornerRadius
        readonly property real pixelScale: Screen.devicePixelRatio
        readonly property real imageAmount: picture.status === Image.Ready ? root.imageAmount : 0
        readonly property real scrimStart: root.scrimStart
        fragmentShader: "qrc:/opennow/shaders/roundedimage.frag.qsb"
    }
}
