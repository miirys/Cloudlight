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
                     || String(item.badge || "").toLocaleLowerCase().indexOf(query) >= 0
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
    // Where the field ends inside this row; measured as the list opens.
    property real fieldBottom: DesktopTokens.px(48)
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
        if (expanded) { root.fieldBottom = selector.mapToItem(root, 0, selector.height).y; raiseAncestors(true); search.clear(); if (root.items.length > 8) search.forceActiveFocus(); else scroll.forceActiveFocus() }
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
        // GeForce NOW-style field: the value sits left-aligned in the control
        // column with a caret at the far edge. No box at rest; a soft fill
        // grows in on hover and stays while the list is open.
        AbstractButton {
            id: selector
            objectName: "settingsChoiceField"
            width: header.controlWidth
            height: DesktopTokens.px(36)
            hoverEnabled: true
            Accessible.role: Accessible.ComboBox
            Accessible.name: root.title + ": " + fieldLabel.text
            onClicked: root.expanded = !root.expanded
            background: Rectangle {
                radius: DesktopTokens.px(8)
                color: DesktopTokens.hover
                opacity: root.expanded ? 1 : selector.down ? 0.9 : selector.hovered || selector.visualFocus ? 0.7 : 0
                Behavior on opacity { NumberAnimation { duration: Theme.focusDuration; easing.type: Easing.OutCubic } }
                border.width: selector.visualFocus && AppController.inputMode !== "pointer" ? 2 : 0
                border.color: Theme.label
            }
            contentItem: Item {
                Text {
                    id: fieldLabel
                    objectName: "settingsButtonLabel"
                    x: DesktopTokens.px(12)
                    anchors.verticalCenter: parent.verticalCenter
                    width: parent.width - x - DesktopTokens.px(36)
                    text: root.valueLabel || (root.current ? root.current.label : String(root.value))
                    elide: Text.ElideRight
                    color: Theme.label
                    font.family: Theme.bodyFont
                    font.pixelSize: DesktopTokens.px(14)
                    font.weight: Font.Medium
                }
                DesktopSettingsIcon {
                    anchors.right: parent.right; anchors.rightMargin: DesktopTokens.px(12)
                    anchors.verticalCenter: parent.verticalCenter
                    width: DesktopTokens.px(10); height: width
                    glyph: "chevron"
                    rotation: root.expanded ? -90 : 90
                    Behavior on rotation { enabled: !AppController.reducedMotion; NumberAnimation { duration: Theme.springDuration * 0.6; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring } }
                    ink: selector.hovered || root.expanded ? Theme.label : Theme.textMuted
                }
            }
        }
    }
    Rectangle {
        id: menuSurface
        // Hidden items take no input, so the list needs no enabled gate; gating it
        // would also report every option as disabled while the list is closed.
        // Visible from the moment it opens (before the reveal binding updates), so the
        // filter field or list can take keyboard focus in the same handler.
        visible: root.expanded || reveal.present
        readonly property real controlX: header.width - header.rightInset - header.controlWidth
        width: Math.max(header.controlWidth, DesktopTokens.px(240))
        x: Math.max(0, Math.min(controlX, header.width - width))
        y: root.fieldBottom + DesktopTokens.px(4)
        height: root.menuHeight
        radius: DesktopTokens.px(10)
        color: Theme.surfaceRaised
        opacity: reveal.progress
        // Unfolds from the field: a short drop plus a slight vertical scale.
        transform: [
            Scale { origin.y: 0; yScale: 0.94 + 0.06 * reveal.progress },
            Translate { y: -DesktopTokens.px(4) * (1 - reveal.progress) }
        ]
        // Soft two-step shadow instead of an outline.
        Rectangle {
            z: -1
            anchors.fill: parent; anchors.margins: -DesktopTokens.px(1); anchors.topMargin: DesktopTokens.px(4)
            radius: parent.radius + DesktopTokens.px(1)
            color: Qt.rgba(0, 0, 0, 0.22)
        }
        Rectangle {
            z: -2
            anchors.fill: parent; anchors.margins: -DesktopTokens.px(6); anchors.topMargin: DesktopTokens.px(8)
            radius: parent.radius + DesktopTokens.px(6)
            color: Qt.rgba(0, 0, 0, 0.16)
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
            ScrollBar.vertical: ScrollBar {
                policy: scroll.contentHeight > scroll.height ? ScrollBar.AlwaysOn : ScrollBar.AlwaysOff
                width: DesktopTokens.px(6)
                contentItem: Rectangle { implicitWidth: DesktopTokens.px(4); radius: width / 2; color: Theme.textMuted; opacity: 0.5 }
                background: Item {}
            }
            Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
            Column {
                id: grid
                width: parent.width - (scroll.contentHeight > scroll.height ? DesktopTokens.px(8) : 0); spacing: DesktopTokens.px(2)
                Repeater {
                    model: root.groups
                    delegate: Column {
                        required property var modelData
                        width: grid.width; spacing: 0
                        Text {
                            visible: modelData.label !== ""
                            leftPadding: DesktopTokens.px(12); topPadding: DesktopTokens.px(10); bottomPadding: DesktopTokens.px(4)
                            text: modelData.label; color: Theme.textMuted
                            font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(11)
                            font.weight: Font.DemiBold; font.letterSpacing: DesktopTokens.px(0.6)
                        }
                        Repeater {
                            model: modelData.items
                            delegate: AbstractButton {
                                id: tile
                                required property var modelData
                                readonly property bool chosen: enabled && String(modelData.value) === String(root.value)
                                objectName: "settingsChoice-" + String(modelData.value)
                                width: grid.width
                                height: Math.max(DesktopTokens.px(38), optionText.implicitHeight + DesktopTokens.px(16))
                                enabled: !modelData.disabled
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
                                    color: DesktopTokens.hover
                                    opacity: tile.enabled && (tile.hovered || tile.activeFocus) ? 1 : 0
                                    Behavior on opacity { NumberAnimation { duration: Theme.focusDuration; easing.type: Easing.OutCubic } }
                                }
                                DesktopSettingsIcon {
                                    id: optionIcon
                                    visible: !!tile.modelData.glyph
                                    anchors.left: parent.left; anchors.leftMargin: DesktopTokens.px(10)
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: 20; height: 20
                                    glyph: tile.modelData.glyph || "controller"
                                    ink: Theme.label
                                    opacity: tile.enabled ? 1 : 0.4
                                }
                                Column {
                                    id: optionText
                                    anchors.verticalCenter: parent.verticalCenter
                                    x: optionIcon.visible ? DesktopTokens.px(40) : DesktopTokens.px(12)
                                    width: parent.width - x - trailing.width - DesktopTokens.px(20); spacing: DesktopTokens.px(2)
                                    Text {
                                        width: parent.width; text: tile.modelData.label
                                        color: tile.chosen ? Theme.focus : tile.enabled ? Theme.label : Theme.textMuted
                                        opacity: tile.enabled ? 1 : 0.7
                                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(14)
                                        font.weight: tile.chosen ? Font.DemiBold : Font.Medium; elide: Text.ElideRight
                                    }
                                    Text {
                                        visible: text !== ""; width: parent.width
                                        text: tile.modelData.detail || ""
                                        color: tile.modelData.detailColor || Theme.textMuted
                                        opacity: tile.enabled ? 1 : 0.8
                                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12)
                                        wrapMode: Text.WordWrap; maximumLineCount: 2; elide: Text.ElideRight
                                        lineHeight: 1.15
                                    }
                                }
                                Row {
                                    id: trailing
                                    anchors.right: parent.right; anchors.rightMargin: DesktopTokens.px(10)
                                    anchors.verticalCenter: parent.verticalCenter
                                    spacing: DesktopTokens.px(8)
                                    Rectangle {
                                        visible: !!tile.modelData.badge
                                        anchors.verticalCenter: parent.verticalCenter
                                        width: badgeText.implicitWidth + DesktopTokens.px(10); height: DesktopTokens.px(18)
                                        radius: DesktopTokens.px(4)
                                        color: DesktopTokens.raisedStrong
                                        opacity: tile.enabled ? 1 : 0.5
                                        Text {
                                            id: badgeText
                                            anchors.centerIn: parent
                                            text: tile.modelData.badge || ""
                                            color: Theme.textMuted
                                            font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(10)
                                            font.weight: Font.DemiBold
                                        }
                                    }
                                    DesktopGlyph {
                                        anchors.verticalCenter: parent.verticalCenter
                                        width: DesktopTokens.px(15); height: DesktopTokens.px(12)
                                        visible: tile.chosen
                                        icon: "desktop-check-focus.svg"
                                    }
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
