pragma Singleton
import QtQuick
import OpenNOW

// Measurements and colours for the in-game overlay, taken from GeForce NOW's side panel
// at a 1440 px tall window and scaled with the rest of the desktop UI. `u(n)` converts a
// reference pixel to a scaled pixel; lavender stands in where GeForce NOW uses green.
QtObject {
    // A 1440 px window is 810 desktop units, so one reference pixel is 0.5625 units.
    function u(value) {
        return DesktopTokens.px(value * 0.5625)
    }
    function uf(value) {
        return DesktopTokens.uiScale * value * 0.5625
    }

    readonly property int panelWidth: u(682)
    readonly property int headerHeight: u(75)
    readonly property int gutter: u(21)
    readonly property int textInset: u(75)
    readonly property int iconCenter: u(37)
    readonly property int iconSize: u(31)
    readonly property int rowHeight: u(96)
    readonly property int compactRowHeight: u(75)

    readonly property int titleSize: u(27)
    readonly property int bodySize: u(21)
    readonly property int subtitleSize: u(19)
    readonly property int labelSize: u(18)
    // Inter ships as one variable face, so weights are set on its axis: titles in the
    // reference are a touch heavier than regular, headers and group labels semibold.
    // Static Inter faces: Medium for row text, SemiBold for headings. No
    // variable axes, so Windows renders the same weights as Linux.
    readonly property int bodyWeight: Font.Medium
    readonly property int strongWeight: Font.DemiBold

    readonly property color body: "#191919"
    readonly property color header: "#393939"
    readonly property color field: "#393939"
    readonly property color fieldRule: "#757575"
    readonly property color menu: "#424242"
    readonly property color divider: "#858585"
    readonly property color text: "#E8E8E8"
    readonly property color headerText: "#ECECEC"
    readonly property color subtitle: "#BABABA"
    readonly property color label: "#A8A8A8"
    readonly property color disabled: "#676767"
    readonly property color icon: "#BABABA"
    readonly property color headerIcon: "#C4C4C4"
    readonly property color hover: "#262626"
    readonly property color pressed: "#2E2E2E"
    readonly property color trackOff: "#5E5E5E"
    readonly property color knobOff: "#A0A0A0"
    readonly property color accent: "#B8A6EE"
    readonly property color accentTrack: "#5B5277"
    readonly property color inkOnAccent: "#000000"
    readonly property color scrollbar: "#343434"

    readonly property int fastDuration: AppController.reducedMotion ? 0 : 120
    readonly property int panelDuration: AppController.reducedMotion ? 0 : 220
    readonly property int pageDuration: AppController.reducedMotion ? 0 : 380
}
