import QtQuick
import QtQuick.Controls
import OpenNOW

// A scrolling overlay page. Content goes in `content`; the page's title is shown in the
// header. `firstFocus()` puts focus on the first focusable entry.
Flickable {
    id: root
    objectName: "overlayPage"
    property string title: ""
    property string pageName: ""
    default property alias content: column.data
    property alias column: column
    // Pinned content below the scrolling column (Statistics and Quit on the main page).
    property alias footer: footerColumn.data
    // Floating lists (drop-downs, the filter "+" list) open in this layer above the content.
    property alias popupLayer: popupLayer
    clip: true
    contentWidth: width
    contentHeight: Math.max(height - footerColumn.height, column.implicitHeight)
    boundsBehavior: Flickable.StopAtBounds
    flickableDirection: Flickable.VerticalFlick
    bottomMargin: footerColumn.height

    function firstFocus() {
        const item = root.nextFocusable(column)
        if (item) item.forceActiveFocus(Qt.OtherFocusReason)
    }
    function nextFocusable(parentItem) {
        for (let i = 0; i < parentItem.children.length; ++i) {
            const child = parentItem.children[i]
            if (!child.visible) continue
            if (child.activeFocusOnTab && child.enabled) return child
            const nested = root.nextFocusable(child)
            if (nested) return nested
        }
        for (let j = 0; j < footerColumn.children.length && parentItem === column; ++j) {
            const pinned = footerColumn.children[j]
            if (pinned.visible && pinned.activeFocusOnTab) return pinned
        }
        return null
    }

    Column {
        id: column
        width: root.width
    }
    Item {
        id: popupLayer
        parent: root
        anchors.fill: parent
        z: 10
    }
    Column {
        id: footerColumn
        parent: root
        width: root.width
        y: root.height - height
        bottomPadding: OverlayStyle.u(12)
    }
    ScrollBar.vertical: ScrollBar {
        policy: root.contentHeight > root.height ? ScrollBar.AsNeeded : ScrollBar.AlwaysOff
        width: OverlayStyle.u(8)
        contentItem: Rectangle { color: "#5A5A5A"; implicitWidth: OverlayStyle.u(8) }
        background: Rectangle { color: OverlayStyle.scrollbar }
    }
}
