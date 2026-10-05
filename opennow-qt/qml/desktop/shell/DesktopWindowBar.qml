import QtQuick
import OpenNOW

// Cloudlight's own caption strip on Windows (WindowChrome owns the native
// side): app mark on the left, minimise / maximise / close on the right, and
// everything else drags the window, snaps to edges and double-click maximises
// exactly like a system title bar.
Item {
    id: root
    property bool shown: false
    readonly property bool maximized: typeof WindowChrome !== "undefined" && WindowChrome.maximized
    implicitHeight: 32
    height: shown ? implicitHeight : 0
    visible: shown
    clip: true

    function report() {
        if (typeof WindowChrome === "undefined")
            return
        if (!shown) {
            WindowChrome.setCaption(0, [])
            return
        }
        const rects = []
        for (const button of [minimizeButton, maximizeButton, closeButton]) {
            const p = button.mapToItem(null, 0, 0)
            rects.push(Qt.rect(p.x, p.y, button.width, button.height))
        }
        WindowChrome.setCaption(height, rects)
    }
    onShownChanged: Qt.callLater(report)
    onWidthChanged: Qt.callLater(report)
    onHeightChanged: Qt.callLater(report)
    Component.onCompleted: Qt.callLater(report)

    Rectangle {
        anchors.fill: parent
        color: DesktopTokens.topBar
    }

    Image {
        x: 12
        anchors.verticalCenter: parent.verticalCenter
        width: 16; height: 16
        sourceSize: Qt.size(64, 64)
        source: "qrc:/icons/opennow-64.png"
        smooth: true
        mipmap: true
    }

    Row {
        anchors.right: parent.right
        height: parent.height

        WindowBarButton {
            id: minimizeButton
            Accessible.name: qsTr("Minimize")
            onClicked: WindowChrome.minimize()
            Rectangle { anchors.centerIn: parent; width: 10; height: 1; color: minimizeButton.glyph }
        }
        WindowBarButton {
            id: maximizeButton
            Accessible.name: root.maximized ? qsTr("Restore") : qsTr("Maximize")
            onClicked: WindowChrome.toggleMaximized()
            Item {
                anchors.centerIn: parent
                width: 10; height: 10
                Rectangle {
                    visible: root.maximized
                    x: 2; y: 0; width: 8; height: 8
                    color: "transparent"; border.width: 1; border.color: maximizeButton.glyph
                    radius: 1
                }
                Rectangle {
                    x: 0; y: root.maximized ? 2 : 0
                    width: root.maximized ? 8 : 10; height: width
                    color: root.maximized ? DesktopTokens.topBar : "transparent"
                    border.width: 1; border.color: maximizeButton.glyph
                    radius: 1.5
                }
            }
        }
        WindowBarButton {
            id: closeButton
            danger: true
            Accessible.name: qsTr("Close")
            onClicked: WindowChrome.close()
            Item {
                anchors.centerIn: parent
                width: 10; height: 10
                Rectangle { anchors.centerIn: parent; width: 13; height: 1; rotation: 45; antialiasing: true; color: closeButton.glyph }
                Rectangle { anchors.centerIn: parent; width: 13; height: 1; rotation: -45; antialiasing: true; color: closeButton.glyph }
            }
        }
    }

    component WindowBarButton: Item {
        id: button
        property bool danger: false
        readonly property color glyph: hover.hovered && danger ? "#FFFFFF" : DesktopTokens.textHigh
        signal clicked()
        width: 46
        height: root.implicitHeight
        Accessible.role: Accessible.Button
        Rectangle {
            anchors.fill: parent
            color: button.danger ? "#C42B1C" : Qt.rgba(1, 1, 1, Theme.lightMode ? 0 : 0.08)
            opacity: tap.pressed ? 0.8 : hover.hovered ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: 90 } }
        }
        Rectangle {
            anchors.fill: parent
            visible: Theme.lightMode && !button.danger
            color: Qt.rgba(0, 0, 0, 0.06)
            opacity: hover.hovered ? 1 : 0
        }
        HoverHandler { id: hover }
        TapHandler { id: tap; onTapped: button.clicked() }
    }
}
