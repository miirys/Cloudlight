pragma ComponentBehavior: Bound

import QtQuick
import OpenNOW

// One home tile. Landscape tiles show the hero art with the title underneath,
// like GeForce NOW's rails; focus is a scale plus a solid outline, readable
// from across a room.
Item {
    id: root

    required property var game
    property int tileWidth: DesktopTokens.posterWidth
    property int tileHeight: DesktopTokens.posterHeight
    property bool landscape: false
    property bool current: false
    readonly property bool pointerHover: hoverHandler.hovered
    readonly property bool navigationFocus: current && AppController.inputMode !== "pointer"
    readonly property bool highlighted: pointerHover || navigationFocus
    readonly property string artwork: DesktopTokens.artworkUrl(game, root.landscape)
    readonly property string title: game ? String(game.title || qsTr("Game")) : qsTr("Game")

    signal activated()
    signal pointed()

    width: tileWidth
    height: tileHeight + (root.landscape ? DesktopTokens.px(44) : 0)
    z: highlighted || visual.scale !== 1 ? 20 : 0
    Accessible.role: Accessible.Button
    Accessible.name: root.title

    Item {
        id: visual
        width: root.tileWidth
        height: root.tileHeight
        scale: !AppController.reducedMotion && root.highlighted ? DesktopTokens.cardHoverScale : 1
        transformOrigin: Item.Center
        Behavior on scale {
            NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: [0.2, 0, 0, 1, 1, 1] }
        }

        RoundedArtwork {
            anchors.fill: parent
            artwork: root.artwork
            fallbackColor: DesktopTokens.raised
            cornerRadius: DesktopTokens.radius
            scrimStart: 1
        }

        Rectangle {
            anchors.fill: parent
            anchors.margins: -DesktopTokens.px(4)
            radius: DesktopTokens.radius + DesktopTokens.px(4)
            color: "transparent"
            border.width: DesktopTokens.focusOutline
            border.color: DesktopTokens.focus
            opacity: root.highlighted ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        }

        DesktopPosterOverlay {
            visible: !root.landscape && root.highlighted
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.leftMargin: 9
            anchors.rightMargin: 9
            anchors.bottomMargin: 12
            game: root.game
        }
    } // visual; hit-test handlers remain outside the transformed item

    Text {
        visible: root.landscape
        y: root.tileHeight + DesktopTokens.px(14)
        width: root.tileWidth
        text: root.title
        elide: Text.ElideRight
        color: root.highlighted ? DesktopTokens.textHigh : DesktopTokens.textBody
        font.family: DesktopTokens.bodyFont
        font.pixelSize: DesktopTokens.bodySize
        font.weight: root.highlighted ? Font.DemiBold : Font.Medium
        Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
    }

    HoverHandler {
        id: hoverHandler
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad | PointerDevice.Stylus
        cursorShape: Qt.PointingHandCursor
        onHoveredChanged: if (hovered) root.pointed()
    }

    TapHandler {
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad | PointerDevice.Stylus | PointerDevice.TouchScreen
        onTapped: root.activated()
    }
}
