import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import OpenNOW

Item {
    id: root

    // Marquee slides from the CMS panels document:
    // {kind:"game"|"marketing", title, body, image, game?, actionLabel?}
    property var slides: []
    property int currentSlide: 0
    property int selectedAction: -1

    signal playRequested(var game)
    signal detailsRequested(var game)
    signal actionPointed(int index)

    width: 1160
    height: 260

    readonly property var slide: slides.length ? slides[Math.max(0, Math.min(currentSlide, slides.length - 1))] : null
    readonly property var slideGame: slide && slide.game ? slide.game : null

    function nextSlide() {
        if (slides.length > 1)
            currentSlide = (currentSlide + 1) % slides.length
    }

    onSlidesChanged: currentSlide = 0

    Timer {
        id: advanceTimer
        interval: 6000
        repeat: true
        running: root.visible && slides.length > 1 && !AppController.reducedMotion && !heroHover.hovered
        onTriggered: root.nextSlide()
    }

    // Keep the text's contrast backing outside the effect layer. Qt's software
    // renderer cannot draw MultiEffect masks, but must still show a readable hero.
    Rectangle {
        anchors.fill: parent
        radius: DesktopTokens.radiusLarge
        color: "#0A0A0A"
    }

    Rectangle {
        id: heroMask
        anchors.fill: parent
        radius: DesktopTokens.radiusLarge
        color: "white"
        visible: false
        layer.enabled: true
    }

    Item {
        anchors.fill: parent
        layer.enabled: true
        layer.smooth: true
        layer.effect: MultiEffect {
            maskEnabled: true
            maskSource: heroMask
            maskThresholdMin: 0.25
            maskSpreadAtMin: 0.2
        }

        Repeater {
            model: root.slides
            Item {
                required property var modelData
                required property int index
                anchors.fill: parent
                visible: opacity > 0
                opacity: index === root.currentSlide ? 1 : 0
                Behavior on opacity { NumberAnimation { duration: AppController.reducedMotion ? 0 : 450; easing.type: Easing.OutCubic } }

                Image {
                    x: Math.round(parent.width * 0.28)
                    width: parent.width - x
                    height: parent.height
                    source: DesktopTokens.decodeArtworkUrl(String(modelData.image || ""))
                    fillMode: Image.PreserveAspectCrop
                    asynchronous: true
                    cache: true
                    sourceSize: Qt.size(Math.ceil(width), Math.ceil(height))
                }
            }
        }

        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                orientation: Gradient.Horizontal
                // Legibility scrim: solid behind the text, clearing over the artwork.
                GradientStop { position: 0; color: "#0A0A0A" }
                GradientStop { position: 0.3; color: "#0A0A0A" }
                GradientStop { position: 0.6; color: "#000A0A0A" }
            }
        }
    }

    HoverHandler { id: heroHover }

    Column {
        x: 24
        y: 24
        width: Math.min(520, Math.max(300, root.width * 0.42))
        spacing: 10

        Text {
            text: root.slide && root.slide.kind === "marketing" ? qsTr("GeForce NOW") : qsTr("Featured")
            color: Theme.mediaMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            font.weight: Font.DemiBold
        }

        Text {
            width: parent.width
            text: root.slide ? String(root.slide.title || "") : ""
            color: "#FFFFFF"
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.px(30)
            font.weight: Font.Bold
            font.letterSpacing: 0
            elide: Text.ElideRight
            maximumLineCount: 2
            wrapMode: Text.WordWrap
        }

        Text {
            width: parent.width
            visible: text !== ""
            text: root.slide ? String(root.slide.body || "") : ""
            color: Theme.mediaMuted
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            font.weight: Font.Normal
            maximumLineCount: 2
            elide: Text.ElideRight
            wrapMode: Text.WordWrap
        }
    }

    Row {
        x: 24
        y: parent.height - 58
        height: 36
        spacing: 9
        visible: root.slideGame !== null

        Button {
            id: playButton
            width: 121
            height: 36
            padding: 0
            focusPolicy: Qt.NoFocus
            hoverEnabled: true
            Accessible.name: text
            text: qsTr("Play")
            onHoveredChanged: if (hovered) root.actionPointed(0)
            onClicked: if (root.slideGame) root.playRequested(root.slideGame)
            background: Rectangle {
                radius: DesktopTokens.radius
                color: playButton.down ? Qt.darker(Theme.mediaAccent, 1.15) : Theme.mediaAccent
                border.width: root.selectedAction === 0 ? 3 : 0
                border.color: "#FFFFFF"
            }
            contentItem: Text {
                text: playButton.text
                color: Theme.contrastText(Theme.mediaAccent)
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                font.weight: Font.DemiBold
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }

        Button {
            id: detailsButton
            width: 153
            height: 36
            padding: 0
            focusPolicy: Qt.NoFocus
            hoverEnabled: true
            Accessible.name: text
            text: qsTr("View details")
            onHoveredChanged: if (hovered) root.actionPointed(1)
            onClicked: if (root.slideGame) root.detailsRequested(root.slideGame)
            background: Rectangle {
                radius: DesktopTokens.radius
                color: detailsButton.down ? "#454545" : "#333333"
                border.width: root.selectedAction === 1 ? 3 : 0
                border.color: "#FFFFFF"
            }
            contentItem: Text {
                text: detailsButton.text
                color: Theme.mediaForeground
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.captionSize
                font.weight: Font.DemiBold
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }
    }

    Row {
        x: 24
        y: parent.height - 20
        spacing: 6
        visible: root.slides.length > 1
        Repeater {
            model: root.slides.length
            Rectangle {
                required property int index
                width: 24
                height: 3
                color: index === root.currentSlide ? Theme.mediaForeground : "#5C5C5C"
                HoverHandler { cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: root.currentSlide = index }
            }
        }
    }
}
