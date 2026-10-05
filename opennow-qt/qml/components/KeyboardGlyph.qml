pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// Keyboard shortcut drawn as native keycaps: a soft rounded fill with the
// key's name in the UI font. Text and shapes render at the window's real
// pixel density, so keycaps never look blurry or pixelated.
Row {
    id: root
    property string shortcut: ""
    property real keySize: 24
    property color ink: Theme.label
    spacing: Math.max(2, Math.round(keySize * 0.14))
    Accessible.role: Accessible.StaticText
    Accessible.name: shortcut

    Repeater {
        model: InputPromptIcons.keysFor(root.shortcut)
        delegate: Rectangle {
            id: keycap
            required property string modelData
            readonly property string label: InputPromptIcons.keyLabel(modelData)
            width: Math.max(height, Math.ceil(keyText.implicitWidth + root.keySize * 0.6))
            height: Math.round(root.keySize)
            radius: Math.round(root.keySize * 0.26)
            color: Qt.rgba(root.ink.r, root.ink.g, root.ink.b, root.ink.a * 0.14)
            Rectangle {
                // A slightly darker lower lip gives the cap depth without an outline.
                anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                height: Math.max(1, Math.round(root.keySize * 0.08))
                radius: parent.radius
                color: Qt.rgba(root.ink.r, root.ink.g, root.ink.b, root.ink.a * 0.08)
            }
            Text {
                id: keyText
                anchors.centerIn: parent
                anchors.verticalCenterOffset: -Math.round(root.keySize * 0.02)
                text: keycap.label
                color: root.ink
                font.family: Theme.bodyFont
                font.pixelSize: Math.max(9, Math.round(root.keySize * (keycap.label.length > 2 ? 0.5 : 0.56)))
                font.weight: Font.DemiBold
            }
        }
    }
}
