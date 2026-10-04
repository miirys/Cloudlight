import QtQuick
import QtQuick.Window
import OpenNOW

// The Cloudlight mascot slot. Mascot art lives in res/mascot/<pose>.png (half-body
// "login", chibi "loading", "empty", "error", "offline", "success"). Until a pose is
// listed in `available`, the slot shows the brand emblem instead, so screens never
// show a broken image or stand-in character art.
Item {
    id: root

    property string pose: "login"
    // Poses that have shipped art. Add the pose here and the file to QmlModule.cmake.
    readonly property var available: []
    readonly property bool hasArt: available.indexOf(pose) >= 0
    // Emblem sizing when there is no art: a fraction of the slot's shorter side.
    property real emblemScale: 0.42

    implicitWidth: DesktopTokens.px(360)
    implicitHeight: DesktopTokens.px(360)

    Image {
        id: art
        anchors.fill: parent
        visible: root.hasArt
        source: root.hasArt ? "qrc:/qt/qml/OpenNOW/res/mascot/" + root.pose + ".png" : ""
        fillMode: Image.PreserveAspectFit
        verticalAlignment: Image.AlignBottom
        asynchronous: true
        mipmap: true
        sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
    }

    Image {
        anchors.centerIn: parent
        visible: !root.hasArt
        width: Math.round(Math.min(parent.width, parent.height) * root.emblemScale)
        height: width
        source: Theme.lightMode ? "qrc:/qt/qml/OpenNOW/res/brand/cloudlight-mark-light.png" : "qrc:/qt/qml/OpenNOW/res/brand/opennow-mark.png"
        fillMode: Image.PreserveAspectFit
        mipmap: true
        sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
    }
}
