pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// GeForce NOW's flat drop-down list: a grey sheet of plain entries that opens below the
// row that owns it. Up and Down move, Return picks, Escape closes and returns focus.
FocusScope {
    id: root
    property var items: []          // [{value, label, disabled?}]
    property var value: undefined
    property Item anchorItem: null
    property int maximumRows: 10
    property bool alignRight: false
    property int menuWidth: OverlayStyle.u(373)
    property int current: 0
    readonly property bool open: visible
    signal picked(var value)
    // Leaving the list (a click elsewhere, focus moving away) closes it without a pick.
    onActiveFocusChanged: if (!activeFocus && visible) visible = false
    visible: false
    z: 20
    width: menuWidth
    height: Math.min(items.length, maximumRows) * OverlayStyle.u(64) + OverlayStyle.u(16)

    function show() {
        if (!anchorItem || items.length === 0) return
        const host = parent
        const point = anchorItem.mapToItem(host, 0, anchorItem.height)
        // Right-aligned lists end just inside the panel edge, as in the reference.
        x = alignRight ? host.width - width - OverlayStyle.u(16) : point.x + OverlayStyle.gutter
        y = point.y
        const selected = items.findIndex(item => item.value === value)
        current = Math.max(0, selected)
        visible = true
        list.positionViewAtIndex(current, ListView.Contain)
        list.forceActiveFocus()
        reveal.restart()
    }
    function close() {
        visible = false
        if (anchorItem) anchorItem.forceActiveFocus()
    }

    NumberAnimation {
        id: reveal
        target: sheet
        property: "opacity"
        from: 0
        to: 1
        duration: OverlayStyle.fastDuration
    }
    Rectangle {
        id: sheet
        anchors.fill: parent
        color: OverlayStyle.menu
        ListView {
            id: list
            anchors.fill: parent
            anchors.topMargin: OverlayStyle.u(8)
            anchors.bottomMargin: OverlayStyle.u(8)
            clip: true
            model: root.items
            currentIndex: root.current
            boundsBehavior: Flickable.StopAtBounds
            keyNavigationEnabled: false
            delegate: Item {
                id: entry
                required property var modelData
                required property int index
                width: list.width
                height: OverlayStyle.u(64)
                readonly property bool available: modelData.disabled !== true
                Rectangle {
                    anchors.fill: parent
                    color: entry.index === root.current && list.activeFocus ? "#525252"
                        : entryHover.hovered ? "#4A4A4A" : "transparent"
                }
                Text {
                    x: OverlayStyle.u(21)
                    width: parent.width - x * 2
                    anchors.verticalCenter: parent.verticalCenter
                    text: String(entry.modelData.label)
                    color: !entry.available ? OverlayStyle.disabled
                        : entry.modelData.value === root.value ? OverlayStyle.accent : "#DADADA"
                    font.family: Theme.bodyFont
                    font.pixelSize: OverlayStyle.bodySize
                    font.weight: OverlayStyle.bodyWeight
                    elide: Text.ElideRight
                }
                HoverHandler { id: entryHover }
                TapHandler {
                    enabled: entry.available
                    onTapped: {
                        root.picked(entry.modelData.value)
                        root.close()
                    }
                }
            }
            Keys.onPressed: event => {
                if (event.key === Qt.Key_Down || event.key === Qt.Key_Up) {
                    const step = event.key === Qt.Key_Down ? 1 : -1
                    let next = root.current
                    for (let i = 0; i < root.items.length; ++i) {
                        next = Math.max(0, Math.min(root.items.length - 1, next + step))
                        if (root.items[next].disabled !== true) break
                    }
                    root.current = next
                    list.positionViewAtIndex(next, ListView.Contain)
                    event.accepted = true
                } else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                    const item = root.items[root.current]
                    if (item && item.disabled !== true) {
                        root.picked(item.value)
                        root.close()
                    }
                    event.accepted = true
                } else if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back || event.key === Qt.Key_Backspace
                        || event.key === Qt.Key_Left) {
                    root.close()
                    event.accepted = true
                } else if (event.key === Qt.Key_Right || event.key === Qt.Key_Tab) {
                    event.accepted = true
                }
            }
        }
        Rectangle {
            visible: list.contentHeight > list.height
            anchors.right: parent.right
            width: OverlayStyle.u(8)
            y: list.y + list.visibleArea.yPosition * list.height
            height: list.visibleArea.heightRatio * list.height
            color: "#5A5A5A"
        }
    }
}
