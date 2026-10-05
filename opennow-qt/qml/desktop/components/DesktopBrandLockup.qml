import QtQuick
import QtQuick.Window
import OpenNOW

Item {
    id: root
    property real markHeight: DesktopTokens.px(16)
    property real fontPixelSize: DesktopTokens.px(18)
    property real spacing: DesktopTokens.px(10)
    property color ink: DesktopTokens.text
    property real textReveal: 1
    // Over game art or the launch screen the background is always dark.
    property bool onMedia: false
    readonly property real markAspect: 1440 / 1184
    implicitWidth: mark.width + (root.textReveal > 0 ? root.spacing + label.implicitWidth * root.textReveal : 0)
    implicitHeight: Math.max(mark.height, label.implicitHeight)
    width: implicitWidth
    height: implicitHeight

    Image {
        id: mark
        anchors.verticalCenter: parent.verticalCenter
        height: root.markHeight
        width: Math.max(1, Math.round(height * root.markAspect))
        source: Theme.lightMode && !root.onMedia ? "qrc:/qt/qml/OpenNOW/res/brand/cloudlight-mark-light.png" : "qrc:/qt/qml/OpenNOW/res/brand/opennow-mark.png"
        fillMode: Image.PreserveAspectFit
        mipmap: true
        sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
    }
    Text {
        id: label
        anchors.verticalCenter: parent.verticalCenter
        anchors.left: mark.right
        anchors.leftMargin: root.spacing
        visible: root.textReveal > 0
        opacity: root.textReveal
        text: "Cloudlight"
        color: root.ink
        // The wordmark is set in the brand serif; it runs small, so it gets more size.
        font.family: Theme.brandFont
        font.pixelSize: Math.round(root.fontPixelSize * 1.3)
        font.weight: Font.Bold
        font.letterSpacing: 0
    }
}
