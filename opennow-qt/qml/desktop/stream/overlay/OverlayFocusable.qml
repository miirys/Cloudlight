import QtQuick
import OpenNOW

// Base for every row, field and tile the overlay can focus. Up and Down walk the focus
// chain (so pages need no hand-written navigation maps), Return, Enter and Space activate,
// and Left and Right step values. A focused item scrolls itself into view.
Item {
    id: root
    property bool available: true
    readonly property bool highlighted: keyFocus || hover.hovered
    readonly property bool keyFocus: activeFocus && AppController.inputMode !== "pointer"
    signal activated()
    signal stepped(int direction)

    activeFocusOnTab: available
    Accessible.focusable: available
    Accessible.onPressAction: if (root.available) root.activated()

    function ensureVisible() {
        for (let item = root.parent; item; item = item.parent) {
            if (item instanceof Flickable) {
                const top = root.mapToItem(item.contentItem, 0, 0).y
                const margin = OverlayStyle.u(24)
                const maximumY = Math.max(0, item.contentHeight - item.height)
                if (top < item.contentY + margin)
                    item.contentY = Math.max(0, top - margin)
                else if (top + root.height > item.contentY + item.height - margin)
                    item.contentY = Math.min(maximumY, top + root.height + margin - item.height)
                return
            }
        }
    }
    onActiveFocusChanged: if (activeFocus) Qt.callLater(root.ensureVisible)

    function moveFocus(forward) {
        let next = root.nextItemInFocusChain(forward)
        // Stay inside the page that owns this item.
        const page = root.page()
        for (let guard = 0; next && guard < 64; ++guard) {
            if (next === root) return
            if (next.visible && next.enabled && (!page || root.isInside(next, page))) {
                next.forceActiveFocus(forward ? Qt.TabFocusReason : Qt.BacktabFocusReason)
                return
            }
            next = next.nextItemInFocusChain(forward)
        }
    }
    function page() {
        for (let item = root.parent; item; item = item.parent)
            if (item.objectName === "overlayPage") return item
        return null
    }
    function isInside(item, ancestor) {
        for (let node = item; node; node = node.parent)
            if (node === ancestor) return true
        return false
    }

    Keys.onPressed: event => {
        if (event.key === Qt.Key_Down) {
            root.moveFocus(true)
            event.accepted = true
        } else if (event.key === Qt.Key_Up) {
            root.moveFocus(false)
            event.accepted = true
        } else if (event.key === Qt.Key_Left || event.key === Qt.Key_Right) {
            root.stepped(event.key === Qt.Key_Left ? -1 : 1)
            event.accepted = true
        } else if (!event.isAutoRepeat && (event.key === Qt.Key_Return || event.key === Qt.Key_Enter
                || event.key === Qt.Key_Space)) {
            if (root.available) root.activated()
            event.accepted = true
        }
    }

    // Flat highlight: a lighter band for hover, plus a lavender edge for D-pad focus.
    property bool showHighlight: true
    Rectangle {
        anchors.fill: parent
        visible: root.showHighlight
        color: root.activeFocus && root.keyFocus ? OverlayStyle.pressed
            : root.highlighted && root.available ? OverlayStyle.hover : "transparent"
        Behavior on color { ColorAnimation { duration: OverlayStyle.fastDuration } }
        Rectangle {
            width: OverlayStyle.u(4)
            height: parent.height
            color: OverlayStyle.accent
            opacity: root.keyFocus && root.showHighlight ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: OverlayStyle.fastDuration } }
        }
    }

    HoverHandler { id: hover; enabled: root.available }
    TapHandler {
        enabled: root.available
        onTapped: {
            root.forceActiveFocus(Qt.MouseFocusReason)
            root.activated()
        }
    }
}
