pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Window
import OpenNOW

// Every capture, newest first. Return opens one in the system viewer.
OverlayPage {
    id: page
    property var menu
    pageName: "gallery"
    title: qsTr("Gallery")
    readonly property var captures: ShellStore.mediaItems.slice()
        .sort((a, b) => Number(b.createdAtMs || 0) - Number(a.createdAtMs || 0))
    readonly property int columns: 3
    readonly property int gap: OverlayStyle.u(11)
    readonly property int tile: Math.floor((width - OverlayStyle.gutter * 2 - gap * (columns - 1)) / columns)

    OverlayRow {
        objectName: "overlayOpenCaptures"
        icon: "folder"
        title: qsTr("Open captures folder")
        subtitle: ShellStore.mediaRootPath
        trailing: "external"
        available: ShellStore.mediaRootPath !== ""
        onActivated: AppController.openLocalPath(ShellStore.mediaRootPath, false)
    }
    Text {
        visible: page.captures.length === 0
        x: OverlayStyle.gutter
        width: parent.width - x * 2
        topPadding: OverlayStyle.u(16)
        text: ShellStore.mediaState === "loading" ? qsTr("Loading captures…")
            : qsTr("No captures yet. Screenshots, recordings and saved replays appear here.")
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        wrapMode: Text.WordWrap
    }
    Grid {
        x: OverlayStyle.gutter
        columns: page.columns
        spacing: page.gap
        topPadding: OverlayStyle.u(12)
        Repeater {
            model: page.captures
            delegate: OverlayFocusable {
                id: tile
                required property var modelData
                width: page.tile
                height: page.tile
                showHighlight: false
                Accessible.role: Accessible.Button
                Accessible.name: String(modelData.fileName || "")
                onActivated: if (modelData.filePath) AppController.openLocalPath(String(modelData.filePath), false)
                onStepped: direction => {
                    const next = tile.nextItemInFocusChain(direction > 0)
                    if (next && next.parent === tile.parent) next.forceActiveFocus()
                }
                Rectangle { anchors.fill: parent; color: "#000000" }
                Image {
                    anchors.fill: parent
                    source: String(tile.modelData.kind === "recording"
                        ? tile.modelData.thumbnailUrl || "" : tile.modelData.mediaUrl || "")
                    fillMode: Image.PreserveAspectCrop
                    asynchronous: true
                    sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
                }
                OverlayIcon {
                    visible: tile.modelData.kind === "recording"
                    x: OverlayStyle.u(8); y: OverlayStyle.u(8)
                    width: OverlayStyle.u(24); height: width
                    name: "video"
                    ink: "#FFFFFF"
                }
                Rectangle {
                    anchors.fill: parent
                    color: "transparent"
                    border.width: Math.max(1, Math.round(OverlayStyle.uf(3)))
                    border.color: OverlayStyle.accent
                    opacity: tile.activeFocus ? 1 : tile.highlighted ? 0.5 : 0
                    Behavior on opacity { NumberAnimation { duration: OverlayStyle.fastDuration } }
                }
            }
        }
    }
}
