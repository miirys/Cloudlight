import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: root
    property string glyph: "A"
    property string shortcutText: ""
    property bool primary: false
    property bool danger: false
    property bool currentItem: false
    highlighted: activeFocus || currentItem

    implicitHeight: 56
    leftPadding: 14
    rightPadding: 24
    focusPolicy: Qt.StrongFocus
    Accessible.name: I18n.source(text, I18n.revision) + (shortcutText !== "" ? " · " + shortcutText : "")
    Accessible.role: Accessible.Button

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter) {
            if (!event.isAutoRepeat)
                root.click()
            event.accepted = true
        }
    }

    background: Rectangle {
        radius: Theme.radius
        color: root.primary ? Theme.focus
                            : root.danger ? Theme.coral
                                          : root.highlighted ? Theme.surfaceHover : Theme.surfaceStrong
        border.color: Theme.label
        border.width: root.highlighted ? 4 : 0
        Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
        Behavior on border.color { ColorAnimation { duration: Theme.focusDuration } }
    }

    contentItem: Row {
        spacing: 12
        ControllerGlyph {
            anchors.verticalCenter: parent.verticalCenter
            visible: root.glyph !== ""
            glyph: root.glyph
            label: ""
            glyphSize: 28
            glyphColor: root.primary ? Theme.focusText : root.danger ? Theme.contrastText(Theme.coral) : Theme.label
        }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            text: I18n.source(root.text, I18n.revision)
            color: root.primary ? Theme.focusText : root.danger ? Theme.contrastText(Theme.coral) : Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: 17
            font.weight: Font.Bold
        }
        KeyboardGlyph {
            visible: root.shortcutText !== ""
            anchors.verticalCenter: parent.verticalCenter
            shortcut: root.shortcutText
            keySize: 26
            ink: root.primary ? Theme.focusText : root.danger ? Theme.contrastText(Theme.coral) : Theme.label
        }
    }
}
