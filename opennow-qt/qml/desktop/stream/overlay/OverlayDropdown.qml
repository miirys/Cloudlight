import QtQuick
import OpenNOW

// A setting with a grey label above its current value and a chevron, opening a flat list.
Column {
    id: root
    property string label: ""
    property string subtitle: ""
    property var items: []
    property var value: undefined
    property bool available: true
    property Item menuHost: null
    signal picked(var value)
    width: parent ? parent.width : 0

    // The list floats above the page, so it lives in the page's popup layer rather
    // than in this Column (which would lay it out inline).
    function hostItem() {
        for (let item = root.parent; item; item = item.parent)
            if (item instanceof Flickable) return item.popupLayer || item.contentItem
        return root.parent
    }
    readonly property string valueLabel: {
        const item = root.items.find(entry => entry.value === root.value)
        return item ? String(item.label) : String(root.value ?? "")
    }

    // Inline: the label is the row title and the value sits in a column on the right.
    property bool inline: false
    OverlaySectionLabel { text: root.label; visible: root.label !== "" && !root.inline }
    OverlayRow {
        id: row
        title: root.inline ? root.label : root.valueLabel
        subtitle: root.subtitle
        trailing: root.inline ? "select" : "dropdown"
        valueText: root.valueLabel
        available: root.available
        height: root.subtitle !== "" ? OverlayStyle.rowHeight : OverlayStyle.u(root.inline ? 73 : 58)
        onActivated: menu.show()
    }
    OverlayChoiceMenu {
        id: menu
        // Bound rather than reparented on open: an item moved out of a null parent at
        // runtime is never drawn.
        parent: root.menuHost || root.hostItem()
        anchorItem: row
        items: root.items
        value: root.value
        onPicked: value => root.picked(value)
    }
}
