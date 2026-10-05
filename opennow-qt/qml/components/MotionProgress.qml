import QtQuick
import OpenNOW

// One interruptible owner for a reveal. Consumers derive opacity/scale from
// progress, retain their content while present, and keep hit-test bounds fixed.
Item {
    id: root
    property bool shown: false
    property int enterDuration: 200
    property int exitDuration: 160
    property real closedScale: 0.96
    property real progress: 0
    readonly property bool present: shown || progress > 0
    readonly property real zoom: AppController.reducedMotion ? 1 : closedScale + (1 - closedScale) * progress
    property bool initialized: false
    signal hidden()

    function synchronize() {
        if (!initialized) return
        // Pick the curve before progress changes: a binding on `shown` could still hold the
        // previous direction's duration when the Behavior starts.
        animation.duration = shown ? enterDuration : exitDuration
        animation.easing.bezierCurve = shown ? Theme.springSoft : [0.4, 0.0, 1.0, 1.0, 1, 1]
        progress = shown ? 1 : 0
    }
    onShownChanged: synchronize()
    Component.onCompleted: { initialized = true; synchronize() }
    onProgressChanged: if (initialized && progress === 0 && !shown)
        Qt.callLater(() => { if (!root.shown && root.progress === 0) root.hidden() })

    Behavior on progress {
        enabled: !AppController.reducedMotion
        NumberAnimation {
            id: animation
            // Progress is a bounded 0..1 contract (consumers derive opacity
            // from it), so the enter curve decelerates without overshoot.
            duration: root.exitDuration
            easing.type: Easing.BezierSpline
            easing.bezierCurve: [0.4, 0.0, 1.0, 1.0, 1, 1]
        }
    }
    Connections {
        target: AppController
        function onReducedMotionChanged() {
            if (AppController.reducedMotion) {
                animation.stop()
                root.synchronize()
            }
        }
    }
}
