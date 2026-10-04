import QtQuick
import QtQuick.Controls
import OpenNOW

// Store tile: box art, then title and price/ownership underneath. Only the
// art scales on focus; the text stays put so rows read as a clean grid.
Item {
    id: root

    property var game: ({})
    property bool selected: false
    property string price: ""
    property string discount: ""
    property bool owned: false
    property bool freeToPlay: false
    property color fallbackColor: DesktopTokens.raised
    property int tileWidth: DesktopTokens.libraryArtWidth
    property int tileHeight: artHeight + DesktopTokens.storeCardInfoHeight
    readonly property int artHeight: Math.round(tileWidth * 3 / 2)
    readonly property bool lifted: root.selected || pointer.containsMouse

    signal activated(var game)
    signal pointed()

    width: tileWidth
    height: tileHeight
    z: lifted || artFrame.scale !== 1 ? 20 : 0
    Accessible.role: Accessible.Button
    Accessible.name: String(game && game.title || qsTr("Game"))
    ToolTip.visible: pointer.containsMouse && cardTitle.truncated
    ToolTip.delay: 700
    ToolTip.text: root.Accessible.name

    Item {
        id: artFrame
        width: root.tileWidth
        height: root.artHeight
        scale: !AppController.reducedMotion && root.lifted ? DesktopTokens.cardHoverScale : 1
        Behavior on scale {
            NumberAnimation { duration: DesktopTokens.quickDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: [0.2, 0, 0, 1, 1, 1] }
        }

        RoundedArtwork {
            id: cover
            anchors.fill: parent
            cornerRadius: DesktopTokens.radius
            scrimStart: 1
            artwork: DesktopTokens.artworkUrl(root.game, false)
            fallbackColor: root.fallbackColor
        }

        Rectangle {
            anchors.fill: parent
            anchors.margins: -DesktopTokens.px(4)
            radius: DesktopTokens.radius + DesktopTokens.px(4)
            color: "transparent"
            border.width: DesktopTokens.focusOutline
            border.color: DesktopTokens.focus
            opacity: root.lifted ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: DesktopTokens.quickDuration } }
        }
    }

    Text {
        id: cardTitle
        objectName: "storeCardTitle"
        x: 0
        y: root.artHeight + DesktopTokens.px(12)
        width: root.tileWidth
        height: implicitHeight
        text: root.game ? String(root.game.title || qsTr("Untitled game")) : qsTr("Untitled game")
        color: DesktopTokens.textHigh
        font.family: DesktopTokens.bodyFont
        font.pixelSize: DesktopTokens.bodySize
        font.weight: root.lifted ? Font.DemiBold : Font.Medium
        elide: Text.ElideRight
        wrapMode: Text.Wrap
        maximumLineCount: 2
        verticalAlignment: Text.AlignTop
    }

    Row {
        objectName: "storeCardMetadata"
        x: 0
        y: cardTitle.y + cardTitle.height + DesktopTokens.px(4)
        visible: root.owned || root.freeToPlay || root.price !== "" || root.discount !== ""
        width: root.tileWidth
        height: DesktopTokens.px(24)
        spacing: DesktopTokens.px(8)

        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.discount.length > 0 && !root.owned
            width: discountLabel.implicitWidth + DesktopTokens.px(12)
            height: DesktopTokens.px(24)
            radius: DesktopTokens.radius
            color: DesktopTokens.green

            Text {
                id: discountLabel
                anchors.centerIn: parent
                text: root.discount
                color: Theme.contrastText(DesktopTokens.green)
                font.family: DesktopTokens.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                font.weight: Font.Bold
            }
        }

        DesktopSettingsIcon {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.owned
            width: DesktopTokens.px(16)
            height: DesktopTokens.px(16)
            glyph: "check"
            ink: DesktopTokens.textMuted
        }

        Text {
            anchors.verticalCenter: parent.verticalCenter
            width: Math.max(0, parent.width - x)
            text: root.owned ? qsTr("In library")
                             : root.freeToPlay ? qsTr("Free to play")
                                               : root.price
            color: root.freeToPlay ? DesktopTokens.green
                                   : root.owned ? DesktopTokens.textMuted
                                                : DesktopTokens.textBody
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            font.weight: root.owned ? Font.Medium : Font.DemiBold
            elide: Text.ElideRight
        }
    }

    MouseArea {
        id: pointer
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onEntered: root.pointed()
        onClicked: root.activated(root.game)
    }
}
