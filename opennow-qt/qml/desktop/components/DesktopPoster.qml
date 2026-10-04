import QtQuick
import QtQuick.Controls
import OpenNOW

// Library tile: box art with the title and stores underneath. Focus is a
// scale plus a solid accent outline; there is no idle border.
ItemDelegate {
    id: root
    property var game: null
    property bool showTitle: true
    property bool selected: activeFocus
    property int tileWidth: DesktopTokens.libraryCellWidth
    readonly property int artGutter: DesktopTokens.px(10)
    readonly property int artWidth: Math.max(1, tileWidth - artGutter * 2)
    readonly property int artHeight: Math.round(artWidth * 3 / 2)
    readonly property int titleBlockHeight: showTitle ? DesktopTokens.px(62) : 0
    property int tileHeight: artHeight + artGutter * 2 + titleBlockHeight
    readonly property bool cardLifted: hovered || (activeFocus && AppController.inputMode !== "pointer")
    readonly property string stores: root.game
        ? (root.game.availableStores || []).map(value => DesktopTokens.storeLabel(value)).join(" · ") : ""
    signal contextRequested(real sceneX, real sceneY)
    // Keep focus geometry inside the delegate so GridView clipping never cuts
    // off the outline while the tile scales up.
    width: tileWidth
    height: tileHeight
    padding: 0
    hoverEnabled: true
    focusPolicy: Qt.StrongFocus
    z: cardLifted || artFrame.scale !== 1 ? 20 : 1
    Accessible.name: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game")
    background: Item {}
    contentItem: Item {
        Item {
            id: artFrame
            x: root.artGutter; y: root.artGutter; width: root.artWidth; height: root.artHeight
            scale: AppController.reducedMotion ? 1 : root.down ? 0.985 : root.cardLifted ? DesktopTokens.cardHoverScale : 1
            Behavior on scale {
                NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: [0.2, 0, 0, 1, 1, 1] }
            }
            RoundedArtwork {
                anchors.fill: parent
                artwork: DesktopTokens.artworkUrl(root.game, false)
                cornerRadius: DesktopTokens.radius
                scrimStart: 1
                fallbackColor: DesktopTokens.raised
            }
            Rectangle {
                anchors.fill: parent
                anchors.margins: -DesktopTokens.px(4)
                radius: DesktopTokens.radius + DesktopTokens.px(4)
                color: "transparent"
                border.width: DesktopTokens.focusOutline
                border.color: DesktopTokens.focus
                opacity: root.cardLifted ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
            }
        }
        Column {
            x: root.artGutter; y: root.artGutter + root.artHeight + DesktopTokens.px(12)
            width: root.artWidth
            spacing: DesktopTokens.px(2)
            visible: root.showTitle
            Text {
                width: parent.width
                text: root.game ? String(root.game.title || qsTr("Game")) : qsTr("Game")
                color: root.cardLifted ? DesktopTokens.textHigh : DesktopTokens.textBody
                elide: Text.ElideRight
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.bodySize
                font.weight: root.cardLifted ? Font.DemiBold : Font.Medium
            }
            Text {
                width: parent.width
                text: root.stores
                color: DesktopTokens.textMuted
                elide: Text.ElideRight
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.captionSize
            }
        }
    }
    TapHandler {
        acceptedButtons: Qt.RightButton
        onTapped: point => {
            const scene = root.mapToItem(null, point.position.x, point.position.y)
            root.contextRequested(scene.x, scene.y)
        }
    }
}
