import QtQuick
import QtQuick.Window
import OpenNOW

// Full-bleed key art at the screen's native resolution. The catalog image shows
// straight away; on screens wider than it, a sharper copy from the image CDN fades in
// over it once cached. If the sharper copy never arrives, the catalog image stays.
Item {
    id: root
    property string artwork: ""
    property bool active: true
    property int fadeDuration: 400
    readonly property int pixelWidth: Math.ceil(width * Screen.devicePixelRatio)
    readonly property int pixelHeight: Math.ceil(height * Screen.devicePixelRatio)
    readonly property string baseUrl: DesktopTokens.decodeArtworkUrl(root.artwork)
    readonly property string sharpUrl: DesktopTokens.artworkForWidth(root.baseUrl, root.pixelWidth)
    readonly property bool upgrading: root.sharpUrl !== root.baseUrl
    readonly property bool ready: base.status === Image.Ready || sharp.status === Image.Ready

    ArtworkSource {
        id: baseSource
        sourceUrl: root.baseUrl
        active: root.active
    }
    ArtworkSource {
        id: sharpSource
        sourceUrl: root.upgrading ? root.sharpUrl : ""
        active: root.active && root.upgrading
    }
    Image {
        id: base
        anchors.fill: parent
        source: baseSource.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        sourceSize: Qt.size(root.pixelWidth, root.pixelHeight)
        asynchronous: true
        cache: true
        visible: sharp.opacity < 1
    }
    Image {
        id: sharp
        anchors.fill: parent
        source: sharpSource.resolvedUrl
        fillMode: Image.PreserveAspectCrop
        sourceSize: Qt.size(root.pixelWidth, root.pixelHeight)
        asynchronous: true
        cache: true
        opacity: status === Image.Ready ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: AppController.reducedMotion ? 0 : root.fadeDuration } }
    }
}
