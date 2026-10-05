import QtQuick
import OpenNOW

// A settings group. Like GeForce NOW, groups are not boxed cards: rows sit on the
// page background and are separated by spacing. Non-paper panels (used for
// standalone notices) keep a flat, opaque surface.
Rectangle {
    id: panel
    property bool paperStyle: false
    property int padding: paperStyle ? 0 : DesktopTokens.px(16)
    default property alias content: body.data

    implicitHeight: body.implicitHeight + padding * 2
    radius: paperStyle ? 0 : DesktopTokens.radius
    color: paperStyle ? "transparent" : Theme.surface
    border.width: paperStyle ? 0 : 0
    border.color: DesktopTokens.seamSoft

    Column {
        id: body
        x: panel.padding
        y: panel.padding
        width: panel.width - panel.padding * 2
        spacing: panel.paperStyle ? DesktopTokens.px(2) : 0
    }
}
