import QtQuick

FocusScope {
    property bool desktopSurfaceActive: false
    // Height of the custom window caption above the content, if any.
    property real topInset: 0
    y: topInset

    scale: desktopSurfaceActive ? 1 : Math.min(parent.width / 1920, (parent.height - topInset) / 1080)
    width: scale > 0 ? parent.width / scale : 0
    height: scale > 0 ? (parent.height - topInset) / scale : 0
    transformOrigin: Item.TopLeft
}
