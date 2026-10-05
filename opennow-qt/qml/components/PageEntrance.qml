import QtQuick
import OpenNOW

// Animate only the incoming page, never the shell or native video surface.
// Restarting replaces the previous transition; no delayed swaps or stale pages.
// GeForce NOW swaps page content in place; the new page only cross-fades in
// over a few frames so the swap is not a hard cut. Nothing scales or slides.
Item {
    id: root
    property real progress: 1
    readonly property real pageOpacity: progress
    // Kept for older consumers; pages no longer scale or travel.
    readonly property real pageScale: 1
    readonly property real offset: 0

    function restart() {
        entrance.stop()
        progress = AppController.reducedMotion ? 1 : 0
        if (!AppController.reducedMotion) entrance.start()
    }

    NumberAnimation {
        id: entrance
        target: root; property: "progress"; to: 1
        duration: 140
        easing.type: Easing.OutCubic
    }
    Connections {
        target: AppController
        function onReducedMotionChanged() {
            if (AppController.reducedMotion) { entrance.stop(); root.progress = 1 }
        }
    }
}
