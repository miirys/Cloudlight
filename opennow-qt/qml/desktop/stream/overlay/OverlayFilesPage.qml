import QtQuick
import OpenNOW

// Where captures are kept and how much space they use.
OverlayPage {
    id: page
    property var menu
    pageName: "files"
    title: qsTr("Files and disk space")
    readonly property real totalBytes: ShellStore.mediaItems.reduce((sum, item) => sum + Number(item.sizeBytes || 0), 0)
    function size(bytes) {
        if (bytes >= 1024 * 1024 * 1024) return qsTr("%1 GB").arg((bytes / (1024 * 1024 * 1024)).toFixed(1))
        if (bytes >= 1024 * 1024) return qsTr("%1 MB").arg(Math.round(bytes / (1024 * 1024)))
        return qsTr("%1 KB").arg(Math.round(bytes / 1024))
    }
    Component.onCompleted: ShellStore.refreshMedia()

    Item { width: 1; height: OverlayStyle.u(20) }
    OverlayRow {
        objectName: "overlayGalleryLocation"
        height: OverlayStyle.u(101)
        title: qsTr("Gallery location")
        subtitle: ShellStore.mediaRootPath !== "" ? ShellStore.mediaRootPath : qsTr("Not available yet")
        trailing: "folder"
        available: ShellStore.mediaRootPath !== ""
        onActivated: AppController.openLocalPath(ShellStore.mediaRootPath, false)
    }
    OverlayRow {
        title: qsTr("Gallery size")
        height: OverlayStyle.u(101)
        subtitle: qsTr("%n capture(s)", "", ShellStore.mediaItems.length)
        trailing: "text"
        valueText: page.size(page.totalBytes)
        activeFocusOnTab: false
        showHighlight: false
    }
}
