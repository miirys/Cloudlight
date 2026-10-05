import QtQuick
import OpenNOW

// Animate only the incoming page, never the shell or native video surface.
// Restarting replaces the previous transition; no delayed swaps or stale pages.
// The page fades in quickly and settles from 98.5% scale on a spring curve,
// the way macOS and iOS swap content. Nothing slides.
Item {
    id: root
    property real progress: 1
    readonly property real pageOpacity: Math.min(1, progress * 2.4)
    readonly property real pageScale: 0.985 + 0.015 * progress
    // Kept for older consumers; pages no longer travel.
    readonly property real offset: 0

    function restart() {
        entrance.stop()
        progress = AppController.reducedMotion ? 1 : 0
        if (!AppController.reducedMotion) entrance.start()
    }

    NumberAnimation {
        id: entrance
        target: root; property: "progress"; to: 1
        duration: Theme.springDuration
        easing.type: Easing.BezierSpline
        easing.bezierCurve: Theme.spring
    }
    Connections {
        target: AppController
        function onReducedMotionChanged() {
            if (AppController.reducedMotion) { entrance.stop(); root.progress = 1 }
        }
    }
}
