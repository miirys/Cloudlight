import QtQuick
import OpenNOW

// A shortcut row: label on the left, a flat key box with an underline on the right.
// Activating the box listens for the next key chord; Escape cancels, Backspace or
// Delete clears the binding when `clearable` is set.
OverlayFocusable {
    id: root
    property string title: ""
    property string shortcut: ""
    property bool clearable: false
    // Shown like any other binding but cannot be changed (Ctrl+G opens this panel).
    property bool fixed: false
    // A full-width box with no label, as on the Game filters page.
    property bool fullWidth: false
    property bool listening: false
    property string error: ""
    signal captured(string shortcut)
    width: parent ? parent.width : 0
    height: fullWidth ? OverlayStyle.u(70) : OverlayStyle.u(75)
    Accessible.role: Accessible.Button
    Accessible.name: title
    Accessible.description: shortcut === "" ? qsTr("None") : shortcut

    onActivated: if (available && !fixed) { listening = true; error = "" }
    onActiveFocusChanged: if (!activeFocus) listening = false

    Keys.priority: Keys.BeforeItem
    Keys.onPressed: event => {
        if (!root.listening) return
        event.accepted = true
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            root.listening = false
        } else if ((event.key === Qt.Key_Backspace || event.key === Qt.Key_Delete) && root.clearable
                && event.modifiers === Qt.NoModifier) {
            root.listening = false
            root.captured("")
        } else {
            const chord = AppController.shortcutFromKey(event.key, event.modifiers)
            if (chord !== "") {
                root.listening = false
                root.captured(chord)
            }
        }
    }

    Text {
        visible: !root.fullWidth
        x: OverlayStyle.gutter
        width: field.x - x - OverlayStyle.u(16)
        anchors.verticalCenter: parent.verticalCenter
        text: root.title
        color: root.available ? OverlayStyle.text : OverlayStyle.disabled
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.bodySize
        font.variableAxes: OverlayStyle.bodyAxes
        elide: Text.ElideRight
    }
    Rectangle {
        id: field
        anchors.right: parent.right
        anchors.rightMargin: root.fullWidth ? OverlayStyle.gutter : OverlayStyle.u(52)
        anchors.verticalCenter: parent.verticalCenter
        width: root.fullWidth ? root.width - OverlayStyle.gutter * 2 : OverlayStyle.u(160)
        height: root.fullWidth ? OverlayStyle.u(69) : OverlayStyle.u(52)
        color: root.listening ? "#454545" : OverlayStyle.field
        Text {
            x: OverlayStyle.u(root.fullWidth ? 21 : 16)
            width: parent.width - x * 2
            anchors.verticalCenter: parent.verticalCenter
            text: root.listening ? qsTr("Press keys…") : root.shortcut === "" ? qsTr("None") : root.shortcut
            color: root.available ? (root.listening ? OverlayStyle.accent : "#CDCDCD") : OverlayStyle.disabled
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.subtitleSize
            elide: Text.ElideRight
        }
        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width
            height: Math.max(1, Math.round(OverlayStyle.uf(root.listening ? 2 : 1)))
            color: root.listening ? OverlayStyle.accent : OverlayStyle.fieldRule
        }
    }
    Text {
        visible: root.error !== ""
        anchors.right: field.right
        anchors.top: field.bottom
        text: root.error
        color: "#E88A8A"
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.u(15)
    }
}
