import QtQuick
import QtQuick.Controls
import OpenNOW

// A settings row whose value opens a floating dropdown list, like GeForce NOW's
// settings menus. Lists longer than eight entries get a filter field.
Item {
    id: root
    property string title: ""
    property string description: ""
    property string glyph: "globe"
    property var items: []
    property var value: ""
    property string valueLabel: ""
    property bool expanded: false
    property bool showDivider: true
    property int maximumColumns: 0
    property real maximumOptionsHeight: DesktopTokens.px(260)
    property string filterPlaceholder: qsTr("Filter options…")
    default property alias footer: footerContent.data
    signal selected(var value)
    readonly property var current: items.find(item => item.kind !== "heading" && String(item.value) === String(root.value))
    readonly property var groups: {
        const groups = [{label: "", items: []}]
        const query = search.text.trim().toLocaleLowerCase()
        for (const item of items) {
            if (item.kind === "heading") groups.push({label: item.label, items: []})
            else if (!query || String(item.label).toLocaleLowerCase().indexOf(query) >= 0
                     || groups[groups.length-1].label.toLocaleLowerCase().indexOf(query) >= 0)
                groups[groups.length-1].items.push(item)
        }
        return groups.filter(group => group.items.length)
    }
    readonly property real revealProgress: reveal.progress
    readonly property real optionsHeight: Math.min(maximumOptionsHeight, grid.implicitHeight + 12)
    readonly property real menuHeight: search.height + optionsHeight + DesktopTokens.px(search.visible ? 20 : 12)
    // GeForce NOW-style dropdown: the list floats over the rows below instead of
    // pushing the page down. The row itself keeps its height.
    implicitHeight: header.height
        + (footerContent.implicitHeight > 0 ? footerContent.implicitHeight + DesktopTokens.px(12) : 0)
    z: reveal.present ? 1000 : 0
    property var raisedAncestors: []
    // Lift the row's containers above later panels while the list is open so it
    // is not painted under them; restored when it closes.
    function raiseAncestors(raise) {
        for (const entry of raisedAncestors) if (entry.item) entry.item.z = entry.z
        raisedAncestors = []
        if (!raise) return
        const lifted = []
        let item = root.parent
        for (let depth = 0; item && depth < 6 && item.objectName !== "settingsPageLoader"; ++depth, item = item.parent) {
            lifted.push({item: item, z: item.z})
            item.z = 1000
        }
        raisedAncestors = lifted
    }
    MotionProgress { id: reveal; shown: root.expanded; enterDuration: 170; exitDuration: 130 }
    onExpandedChanged: {
        if (expanded) { raiseAncestors(true); search.clear(); if (search.visible) search.forceActiveFocus(); else scroll.forceActiveFocus() }
        else selector.forceActiveFocus()
    }
    Connections {
        target: reveal
        function onPresentChanged() { if (!reveal.present) root.raiseAncestors(false) }
    }
    Component.onDestruction: raiseAncestors(false)
    // Clicking anywhere outside the open list closes it.
    MouseArea {
        visible: root.expanded
        readonly property point origin: visible ? root.mapToItem(null, 0, 0) : Qt.point(0, 0)
        x: -origin.x; y: -origin.y
        width: root.Window.width; height: root.Window.height
        onClicked: root.expanded = false
        onWheel: wheel => { root.expanded = false; wheel.accepted = false }
    }
    DesktopSettingsRow {
        id: header
        width: parent.width; paperStyle: true; glyph: root.glyph
        title: root.title; description: root.description
        expanded: false
        showDivider: root.showDivider
        DesktopSettingsButton {
            id: selector
            width: header.controlWidth
            menu: true
            text: root.valueLabel || (root.current ? root.current.label : String(root.value))
            Accessible.name: root.title + ": " + text
            onClicked: root.expanded = !root.expanded
        }
    }
    Rectangle {
        id: menuSurface
        // Hidden items take no input, so the list needs no enabled gate; gating it
        // would also report every option as disabled while the list is closed.
        visible: reveal.present
        readonly property real controlX: header.width - header.rightInset - header.controlWidth
        width: Math.max(header.controlWidth, DesktopTokens.px(260))
        x: Math.max(0, Math.min(controlX, header.width - width))
        y: header.height - DesktopTokens.px(10)
        height: root.menuHeight
        radius: DesktopTokens.px(10)
        color: Theme.surfaceRaised
        opacity: reveal.progress
        transform: Translate { y: -DesktopTokens.px(6) * (1 - reveal.progress) }
        // Soft shadow instead of an outline.
        Rectangle {
            z: -1
            anchors.fill: parent; anchors.topMargin: DesktopTokens.px(6); anchors.margins: -DesktopTokens.px(2)
            radius: parent.radius + DesktopTokens.px(2)
            color: Qt.rgba(0, 0, 0, 0.35)
        }
        MouseArea { anchors.fill: parent; acceptedButtons: Qt.AllButtons; onWheel: wheel => wheel.accepted = false }
        DesktopSettingsField {
            id: search
            objectName: "settingsChoiceFilter"
            enabled: root.expanded
            x: DesktopTokens.px(8); y: DesktopTokens.px(8); width: parent.width - DesktopTokens.px(16)
            visible: root.items.length > 8
            height: visible ? implicitHeight : 0
            placeholderText: root.filterPlaceholder
            onTextChanged: scroll.contentY = 0
            Accessible.name: root.title + ": " + placeholderText
            Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
        }
        Flickable {
            id: scroll
            x: DesktopTokens.px(4); y: search.y + search.height + DesktopTokens.px(search.visible ? 4 : -2); width: parent.width - DesktopTokens.px(8)
            height: root.optionsHeight
            contentWidth: width; contentHeight: grid.implicitHeight
            clip: true; boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar { policy: scroll.contentHeight > scroll.height ? ScrollBar.AlwaysOn : ScrollBar.AlwaysOff }
            Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
            Column {
                id: grid
                width: parent.width - (scroll.contentHeight > scroll.height ? 12 : 0); spacing: DesktopTokens.px(4)
                Repeater {
                    model: root.groups
                    delegate: Column {
                        required property var modelData
                        width: grid.width; spacing: 0
                        Text {
                            visible: modelData.label !== ""
                            leftPadding: DesktopTokens.px(12); topPadding: DesktopTokens.px(6); bottomPadding: DesktopTokens.px(4)
                            text: modelData.label; color: Theme.textMuted
                            font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12)
                            font.weight: Font.DemiBold; font.capitalization: Font.Capitalize
                        }
                        Repeater {
                            model: modelData.items
                            delegate: AbstractButton {
                                id: tile
                                required property var modelData
                                readonly property bool chosen: enabled && String(modelData.value) === String(root.value)
                                objectName: "settingsChoice-" + String(modelData.value)
                                width: grid.width
                                height: DesktopTokens.px(tile.modelData.detail ? 46 : 36)
                                enabled: !modelData.disabled; opacity: enabled ? 1 : 0.45
                                hoverEnabled: true
                                Accessible.name: String(modelData.label) + " " + String(modelData.detail || "")
                                onClicked: { root.expanded = false; root.selected(modelData.value) }
                                Keys.onReturnPressed: event => { tile.clicked(); event.accepted = true }
                                Keys.onEnterPressed: event => { tile.clicked(); event.accepted = true }
                                Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
                                Keys.onUpPressed: event => { tile.nextItemInFocusChain(false).forceActiveFocus(); event.accepted = true }
                                Keys.onDownPressed: event => { tile.nextItemInFocusChain(true).forceActiveFocus(); event.accepted = true }
                                background: Rectangle {
                                    radius: DesktopTokens.px(7)
                                    color: tile.hovered || tile.activeFocus ? DesktopTokens.hover : "transparent"
                                    Behavior on color { ColorAnimation { duration: Theme.focusDuration } }
                                }
                                DesktopGlyph {
                                    anchors.left: parent.left; anchors.leftMargin: DesktopTokens.px(10)
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: DesktopTokens.px(12); height: DesktopTokens.px(10)
                                    visible: tile.chosen
                                    icon: "desktop-check-focus.svg"
                                }
                                DesktopSettingsIcon {
                                    id: optionIcon
                                    visible: !!tile.modelData.glyph
                                    anchors.left: parent.left; anchors.leftMargin: DesktopTokens.px(30)
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: 20; height: 20
                                    glyph: tile.modelData.glyph || "controller"
                                    ink: Theme.label
                                }
                                Column {
                                    anchors.verticalCenter: parent.verticalCenter
                                    x: optionIcon.visible ? DesktopTokens.px(58) : DesktopTokens.px(30)
                                    width: parent.width - x - DesktopTokens.px(12); spacing: 2
                                    Text { width: parent.width; text: tile.modelData.label; color: Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(14); font.weight: tile.chosen ? Font.DemiBold : Font.Medium; elide: Text.ElideRight }
                                    Text { visible: text !== ""; width: parent.width; text: tile.modelData.detail || ""; color: tile.modelData.detailColor || Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12); elide: Text.ElideRight }
                                }
                            }
                        }
                    }
                }
                Text { visible: root.groups.length === 0; leftPadding: DesktopTokens.px(12); text: qsTr("No matching options"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(13) }
            }
        }
    }
    Column {
        id: footerContent
        x: header.labelInset
        y: header.height
        width: parent.width - x - DesktopTokens.settingsInset
        spacing: DesktopTokens.px(8)
    }
}
