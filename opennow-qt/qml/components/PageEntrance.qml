import QtQuick
import OpenNOW

// Animate only the incoming page, never the shell or native video surface.
// Restarting replaces the previous transition; no delayed swaps or stale pages.
Item {
    id: root
    property real progress: 1
    // Fade the whole page in while it settles upward, so a route change reads
    // as one deliberate reveal rather than a flicker.
    readonly property real pageOpacity: Math.min(1, progress * 1.6)
    readonly property real offset: 18 * (1 - progress)

    function restart() {
        entrance.stop()
        progress = AppController.reducedMotion ? 1 : 0
        if (!AppController.reducedMotion) entrance.start()
    }

    NumberAnimation {
        id: entrance
        target: root; property: "progress"; to: 1
        duration: 280
        // Material 3 "emphasized decelerate".
        easing.type: Easing.BezierSpline
        easing.bezierCurve: [0.05, 0.7, 0.1, 1, 1, 1]
    }
    Connections {
        target: AppController
        function onReducedMotionChanged() {
            if (AppController.reducedMotion) { entrance.stop(); root.progress = 1 }
        }
    }
}
