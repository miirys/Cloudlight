import QtQuick
import OpenNOW

// Full-bleed store marquee, matching the home hero: art across the whole
// width, a left and bottom scrim into the page colour, and the title block
// on the safe margin.
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
    clip: true

    readonly property var slide: slides.length ? slides[Math.max(0, Math.min(currentSlide, slides.length - 1))] : null
    readonly property var slideGame: slide && slide.game ? slide.game : null

    function nextSlide() {
        if (slides.length > 1)
            currentSlide = (currentSlide + 1) % slides.length
    }

    onSlidesChanged: currentSlide = 0

    Timer {
        id: advanceTimer
        interval: 8000
        repeat: true
        running: root.visible && slides.length > 1 && !AppController.reducedMotion && !heroHover.hovered
        onTriggered: root.nextSlide()
    }

    Rectangle { anchors.fill: parent; color: DesktopTokens.shell }

    Repeater {
        model: root.slides
        Item {
            id: slideLayer
            required property var modelData
            required property int index
            readonly property bool shown: index === root.currentSlide
            anchors.fill: parent
            visible: opacity > 0
            opacity: shown ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: AppController.reducedMotion ? 0 : 700; easing.type: Easing.InOutQuad } }

            Image {
                anchors.fill: parent
                source: DesktopTokens.decodeArtworkUrl(String(slideLayer.modelData.image || ""))
                fillMode: Image.PreserveAspectCrop
                asynchronous: true
                cache: true
                sourceSize: Qt.size(1920, 1080)
                scale: slideLayer.shown ? 1 : 1.04
                Behavior on scale { NumberAnimation { duration: AppController.reducedMotion ? 0 : 1200; easing.type: Easing.OutCubic } }
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.9) }
            GradientStop { position: 0.4; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0.5) }
            GradientStop { position: 0.72; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0) }
        }
    }
    Rectangle {
        anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
        height: parent.height * 0.5
        gradient: Gradient {
            GradientStop { position: 0; color: Qt.rgba(DesktopTokens.shell.r, DesktopTokens.shell.g, DesktopTokens.shell.b, 0) }
            GradientStop { position: 1; color: DesktopTokens.shell }
        }
    }

    HoverHandler { id: heroHover }

    Column {
        x: DesktopTokens.safeX
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(44)
        width: Math.min(root.width * 0.5, DesktopTokens.px(760))
        spacing: DesktopTokens.px(12)

        Text {
            text: root.slide && root.slide.kind === "marketing" ? qsTr("GEFORCE NOW") : qsTr("FEATURED")
            color: DesktopTokens.focus
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.captionSize
            font.weight: Font.Bold
            font.letterSpacing: DesktopTokens.px(2)
        }
        Text {
            width: parent.width
            text: root.slide ? String(root.slide.title || "") : ""
            color: "#FFFFFF"
            font.family: DesktopTokens.displayFont
            font.pixelSize: DesktopTokens.displaySize
            font.weight: Font.Bold
            font.letterSpacing: -DesktopTokens.px(0.5)
            elide: Text.ElideRight
            maximumLineCount: 2
            wrapMode: Text.WordWrap
            lineHeight: 1.05
        }
        Text {
            width: parent.width
            visible: text !== ""
            text: root.slide ? String(root.slide.body || "") : ""
            color: "#D9D9D9"
            font.family: DesktopTokens.bodyFont
            font.pixelSize: DesktopTokens.bodySize
            font.weight: Font.Medium
            maximumLineCount: 2
            elide: Text.ElideRight
            wrapMode: Text.WordWrap
        }
        Item { width: 1; height: DesktopTokens.px(8); visible: actions.visible }
        Row {
            id: actions
            spacing: DesktopTokens.px(16)
            visible: root.slideGame !== null
            DesktopHeroButton {
                objectName: "storeHeroPlay"
                primary: true
                glyph: "play"
                text: qsTr("Play")
                selected: root.selectedAction === 0
                onPointed: root.actionPointed(0)
                onActivated: if (root.slideGame) root.playRequested(root.slideGame)
            }
            DesktopHeroButton {
                objectName: "storeHeroDetails"
                glyph: "info"
                text: qsTr("View details")
                selected: root.selectedAction === 1
                onPointed: root.actionPointed(1)
                onActivated: if (root.slideGame) root.detailsRequested(root.slideGame)
            }
        }
    }

    Row {
        anchors.right: parent.right
        anchors.rightMargin: DesktopTokens.safeX
        anchors.bottom: parent.bottom
        anchors.bottomMargin: DesktopTokens.px(66)
        spacing: DesktopTokens.px(8)
        visible: root.slides.length > 1
        Repeater {
            model: root.slides.length
            Rectangle {
                id: dash
                required property int index
                readonly property bool current: index === root.currentSlide
                width: current ? DesktopTokens.px(40) : DesktopTokens.px(20)
                height: DesktopTokens.px(4)
                radius: height / 2
                color: current ? DesktopTokens.focus : "#80FFFFFF"
                Behavior on width { NumberAnimation { duration: DesktopTokens.motionDuration; easing.type: Easing.OutCubic } }
                HoverHandler { cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: root.currentSlide = dash.index }
            }
        }
    }
}
