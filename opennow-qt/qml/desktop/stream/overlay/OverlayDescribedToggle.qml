import QtQuick
import OpenNOW

// A setting title with a wrapped grey description and a switch on the right, as on
// GeForce NOW's System and Files pages.
OverlayFocusable {
    id: root
    property string title: ""
    property string description: ""
    property bool checked: false
    property bool caps: false
    width: parent ? parent.width : 0
    height: description === "" ? OverlayStyle.u(73)
        : Math.max(OverlayStyle.u(96), textColumn.y + textColumn.implicitHeight + OverlayStyle.u(30))
    Accessible.role: Accessible.CheckBox
    Accessible.name: title
    Accessible.description: description
    Accessible.checkable: true
    Accessible.checked: checked

    Column {
        id: textColumn
        x: OverlayStyle.gutter
        y: OverlayStyle.u(43)
        width: toggle.x - x - OverlayStyle.u(24)
        spacing: OverlayStyle.u(4)
        Text {
            id: titleLine
            width: parent.width
            text: root.caps ? root.title.toUpperCase() : root.title
            color: root.available ? OverlayStyle.text : OverlayStyle.disabled
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.bodySize
            font.variableAxes: OverlayStyle.bodyAxes
            elide: Text.ElideRight
        }
        Text {
            visible: text !== ""
            width: parent.width
            text: root.description
            textFormat: Text.StyledText
            color: root.available ? OverlayStyle.subtitle : OverlayStyle.disabled
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.subtitleSize
            wrapMode: Text.WordWrap
            lineHeight: 1.1
        }
    }
    OverlayToggle {
        id: toggle
        anchors.right: parent.right
        anchors.rightMargin: OverlayStyle.u(36)
        // Level with the title when there is no description.
        y: (root.description === "" ? textColumn.y + titleLine.height / 2 : OverlayStyle.u(49)) - height / 2
        checked: root.checked
        available: root.available
    }
}
