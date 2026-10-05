import QtQuick
import QtQuick.Controls
import OpenNOW

Button {
    id: root
    property bool primary: false
    property bool danger: false
    property bool onMediaBackground: false
    property string shortcutText: ""
    property string shortcutSequence: shortcutText
    property string glyph: ""
    property string themedGlyph: ""
    property int glyphSize: DesktopTokens.px(18)
    property int cornerRadius: DesktopTokens.radius
    height: DesktopTokens.px(44)
    implicitWidth: Math.max(DesktopTokens.px(80), contentRow.implicitWidth + leftPadding + rightPadding)
    leftPadding: DesktopTokens.px(20)
    rightPadding: DesktopTokens.px(20)
    focusPolicy: Qt.StrongFocus
    hoverEnabled: true
    // Select motion: the button lifts slightly toward the pointer on hover and
    // settles back on a spring; pressing sinks it. Text stays sharp (curve text).
    scale: !enabled || AppController.reducedMotion ? 1 : down ? 0.97 : hovered ? 1.03 : 1
    Behavior on scale { NumberAnimation { duration: Theme.springDuration * 0.6; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
    font.family: DesktopTokens.bodyFont
    font.pixelSize: DesktopTokens.captionSize
    font.weight: Font.DemiBold
    // Solid fills only: accent for the primary action, neutral grey otherwise.
    readonly property color fill: root.primary ? root.accentFill
        : root.danger ? (root.hovered ? Qt.darker(DesktopTokens.danger, 1.1) : DesktopTokens.danger)
        : root.onMediaBackground ? (root.hovered ? "#454545" : "#333333")
        : (root.hovered ? Qt.lighter(DesktopTokens.raisedStrong, Theme.lightMode ? 0.94 : 1.2) : DesktopTokens.raisedStrong)
    readonly property color accentFill: root.onMediaBackground ? Theme.mediaAccent : DesktopTokens.focus
    readonly property color ink: Theme.contrastText(fill)
    background: Rectangle {
        radius: root.cornerRadius
        color: root.down ? Qt.darker(root.fill, 1.15) : root.fill
        opacity: root.enabled ? 1 : 0.5
        Behavior on color { ColorAnimation { duration: DesktopTokens.quickDuration } }
        // Focus ring sits outside the button so it reads on any fill from a distance.
        Rectangle {
            anchors.fill: parent
            anchors.margins: -DesktopTokens.px(4)
            radius: parent.radius + DesktopTokens.px(3)
            color: "transparent"
            border.width: DesktopTokens.px(3)
            border.color: root.onMediaBackground ? Theme.mediaForeground : Theme.label
            // Keyboard and controller focus only; a clicked or auto-focused button
            // shows no ring under the mouse.
            visible: root.activeFocus && AppController.inputMode !== "pointer"
        }
    }
    contentItem: Item {
        implicitWidth: contentRow.implicitWidth
        implicitHeight: contentRow.implicitHeight
        Row {
            id: contentRow
            anchors.centerIn: parent
            spacing: root.glyph !== "" || root.themedGlyph !== "" ? DesktopTokens.px(10) : DesktopTokens.px(8)
            DesktopGlyph {
                visible: root.glyph !== "" && root.themedGlyph === ""
                anchors.verticalCenter: parent.verticalCenter
                width: root.glyphSize
                height: root.glyphSize
                icon: root.glyph
            }
            Loader {
                active: root.themedGlyph !== ""
                visible: active
                anchors.verticalCenter: parent.verticalCenter
                width: root.glyphSize; height: root.glyphSize
                sourceComponent: DesktopSettingsIcon {
                    glyph: root.themedGlyph
                    ink: root.ink
                }
            }
            Text {
                visible: root.text !== ""
                anchors.verticalCenter: parent.verticalCenter
                text: root.text
                color: root.ink
                font: root.font
            }
            KeyboardGlyph {
                visible: root.shortcutText !== ""
                anchors.verticalCenter: parent.verticalCenter
                shortcut: root.shortcutSequence
                Accessible.name: root.shortcutText
                keySize: DesktopTokens.px(22)
                ink: root.ink
            }
        }
    }
}
