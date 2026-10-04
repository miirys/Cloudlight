import QtQuick
import OpenNOW

Rectangle {
    id: root
    color: Theme.shell
    clip: true

    Image {
        readonly property real coverScale: Math.max(root.width / 1920, root.height / 620)
        width: 1920 * coverScale
        height: 620 * coverScale
        x: (root.width - width) / 2
        y: (root.height - height) * 0.3
        source: "qrc:/qt/qml/OpenNOW/res/brand/signin-hero.jpg"
    }

    Rectangle {
        anchors.fill: parent
        gradient: Gradient {
            // Darkens the hero art so the card in front stays legible.
            GradientStop { position: 0; color: Theme.shell }
            GradientStop { position: 0.5; color: Theme.shell }
            GradientStop { position: 1; color: Theme.shell }
        }
    }
}
