import QtQuick
import OpenNOW

// Flat slider row for filter strengths: label and value above a thin track. Values sit on
// a `step` grid counted from `from` (NVIDIA's bipolar sliders move in twos); Left and Right
// move one step, and dragging or clicking the track snaps to the nearest step.
OverlayFocusable {
    id: root
    property string title: ""
    property int from: 0
    property int to: 100
    property int step: 1
    property int value: 0
    // Shown after the number, e.g. "°" for angles. Bipolar values carry an explicit sign.
    property string suffix: ""
    signal moved(int value)
    width: parent ? parent.width : 0
    height: OverlayStyle.u(84)
    Accessible.role: Accessible.Slider
    Accessible.name: title
    function clamp(next) {
        const snapped = from + Math.round((next - from) / Math.max(1, step)) * Math.max(1, step)
        return Math.max(from, Math.min(to, snapped))
    }
    onStepped: direction => { const next = clamp(value + direction * step); if (next !== value) moved(next) }

    Text {
        x: OverlayStyle.gutter
        y: OverlayStyle.u(14)
        text: root.title
        color: root.available ? OverlayStyle.text : OverlayStyle.disabled
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.bodySize
        font.variableAxes: OverlayStyle.bodyAxes
    }
    Text {
        anchors.right: parent.right
        anchors.rightMargin: OverlayStyle.gutter
        y: OverlayStyle.u(14)
        text: (root.from < 0 && root.value > 0 ? "+" + root.value : String(root.value)) + root.suffix
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        font.features: { "tnum": 1 }
    }
    Item {
        id: track
        x: OverlayStyle.gutter
        y: OverlayStyle.u(56)
        width: parent.width - OverlayStyle.gutter * 2
        height: OverlayStyle.u(20)
        readonly property real ratio: (root.value - root.from) / Math.max(1, root.to - root.from)
        readonly property real origin: root.from < 0 ? (0 - root.from) / (root.to - root.from) : 0
        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            width: parent.width
            height: Math.max(2, Math.round(OverlayStyle.uf(4)))
            color: OverlayStyle.trackOff
        }
        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            x: parent.width * Math.min(track.origin, track.ratio)
            width: parent.width * Math.abs(track.ratio - track.origin)
            height: Math.max(2, Math.round(OverlayStyle.uf(4)))
            color: root.available ? OverlayStyle.accent : OverlayStyle.disabled
        }
        Rectangle {
            width: OverlayStyle.u(20)
            height: width
            radius: width / 2
            anchors.verticalCenter: parent.verticalCenter
            x: parent.width * track.ratio - width / 2
            color: root.available ? (root.activeFocus ? OverlayStyle.accent : "#E0E0E0") : OverlayStyle.disabled
            Behavior on x { enabled: !drag.active; NumberAnimation { duration: OverlayStyle.fastDuration } }
        }
        DragHandler {
            id: drag
            target: null
            xAxis.enabled: true
            yAxis.enabled: false
            grabPermissions: PointerHandler.CanTakeOverFromAnything
            onCentroidChanged: if (active) {
                const ratio = Math.max(0, Math.min(1, centroid.position.x / track.width))
                const next = root.clamp(root.from + ratio * (root.to - root.from))
                if (next !== root.value) root.moved(next)
            }
        }
        TapHandler {
            gesturePolicy: TapHandler.ReleaseWithinBounds
            grabPermissions: PointerHandler.CanTakeOverFromAnything
            onTapped: eventPoint => {
                root.forceActiveFocus(Qt.MouseFocusReason)
                const ratio = Math.max(0, Math.min(1, eventPoint.position.x / track.width))
                root.moved(root.clamp(root.from + ratio * (root.to - root.from)))
            }
        }
    }
}
