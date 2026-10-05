pragma ComponentBehavior: Bound
import QtQuick
import OpenNOW

// Game filters, as on GeForce NOW: three style slots plus None, a name and a shortcut per
// style, and a "+" list of filters. Filters are applied to the video by the stream
// renderer; changes show on the game behind the panel as you make them.
OverlayPage {
    id: page
    property var menu
    pageName: "filters"
    title: qsTr("Game filters")

    readonly property var saved: ShellStore.settings.gameFilters || ({})
    // Local copy so slider moves feel instant; written back after a short pause.
    property var draft: page.normalized(saved)
    property bool dirty: false
    onSavedChanged: if (!dirty) draft = page.normalized(saved)
    property int editing: Math.max(1, Number(draft.active || 0))

    function normalized(value) {
        const styles = []
        for (let i = 0; i < 3; ++i) {
            const style = (value && value.styles && value.styles[i]) || {}
            styles.push({name: String(style.name || ""), filters: (style.filters || []).map(f => Object.assign({}, f))})
        }
        return {active: Math.max(0, Math.min(3, Number(value && value.active || 0))), styles: styles}
    }
    function commit(next) {
        page.draft = next
        page.dirty = true
        writeTimer.restart()
    }
    Timer {
        id: writeTimer
        interval: 180
        onTriggered: {
            ShellStore.setSetting("gameFilters", page.draft)
            page.dirty = false
        }
    }
    function select(slot) {
        const next = page.normalized(page.draft)
        next.active = slot
        if (slot > 0) page.editing = slot
        page.commit(next)
    }
    function editStyle(change) {
        const next = page.normalized(page.draft)
        change(next.styles[page.editing - 1])
        next.active = page.editing
        page.commit(next)
    }

    readonly property var catalog: [
        {value: "black-white", label: qsTr("Black & White"), params: [{key: "intensity", label: qsTr("Intensity"), from: 0, to: 100, fallback: 100}]},
        {value: "brightness-contrast", label: qsTr("Brightness / Contrast"), params: [
            {key: "brightness", label: qsTr("Brightness"), from: -100, to: 100, fallback: 0},
            {key: "contrast", label: qsTr("Contrast"), from: -100, to: 100, fallback: 0}]},
        {value: "color", label: qsTr("Color"), params: [
            {key: "saturation", label: qsTr("Saturation"), from: -100, to: 100, fallback: 0},
            {key: "vibrance", label: qsTr("Vibrance"), from: -100, to: 100, fallback: 0},
            {key: "temperature", label: qsTr("Temperature"), from: -100, to: 100, fallback: 0}]},
        {value: "colorblind", label: qsTr("Colorblind"), params: [{key: "strength", label: qsTr("Strength"), from: 0, to: 100, fallback: 100}]},
        {value: "details", label: qsTr("Details"), params: [{key: "amount", label: qsTr("Amount"), from: 0, to: 100, fallback: 50}]},
        {value: "letterbox", label: qsTr("Letterbox"), params: [{key: "amount", label: qsTr("Amount"), from: 0, to: 100, fallback: 50}]},
        {value: "night-mode", label: qsTr("Night mode"), params: [{key: "intensity", label: qsTr("Intensity"), from: 0, to: 100, fallback: 50}]},
        {value: "old-film", label: qsTr("Old film"), params: [{key: "intensity", label: qsTr("Intensity"), from: 0, to: 100, fallback: 60}]},
        {value: "sharpen", label: qsTr("Sharpen"), params: [{key: "amount", label: qsTr("Amount"), from: 0, to: 100, fallback: 50}]},
        {value: "vignette", label: qsTr("Vignette"), params: [{key: "amount", label: qsTr("Amount"), from: 0, to: 100, fallback: 50}]}
    ]
    readonly property var colorblindModes: [
        {value: "protanopia", label: qsTr("Protanopia")},
        {value: "deuteranopia", label: qsTr("Deuteranopia")},
        {value: "tritanopia", label: qsTr("Tritanopia")}
    ]
    function entry(type) { return page.catalog.find(item => item.value === type) }
    readonly property var style: draft.styles[editing - 1] || ({name: "", filters: []})
    readonly property var available: page.catalog.filter(item => !page.style.filters.some(f => f.type === item.value))
        .map(item => ({value: item.value, label: item.label}))

    OverlaySectionLabel { text: qsTr("Style"); topPadding: OverlayStyle.u(39) }
    Item { width: 1; height: OverlayStyle.u(5) }
    Row {
        id: slots
        x: OverlayStyle.gutter
        spacing: OverlayStyle.u(7)
        readonly property int slot: Math.floor((page.width - OverlayStyle.gutter * 2 - spacing * 3) / 4)
        Repeater {
            model: 4
            delegate: OverlayFocusable {
                id: slotTile
                required property int index
                objectName: "overlayFilterSlot" + index
                width: slots.slot
                height: slots.slot
                showHighlight: false
                readonly property bool selected: page.draft.active === index
                readonly property var slotStyle: index > 0 ? page.draft.styles[index - 1] : null
                Accessible.role: Accessible.RadioButton
                Accessible.name: label.text
                Accessible.checked: selected
                onActivated: page.select(index)
                onStepped: direction => {
                    const next = slotTile.nextItemInFocusChain(direction > 0)
                    if (next && next.parent === slotTile.parent) next.forceActiveFocus()
                }
                Rectangle {
                    anchors.fill: parent
                    color: slotTile.highlighted ? "#141414" : "#000000"
                    border.width: Math.max(1, Math.round(OverlayStyle.uf(slotTile.selected || slotTile.keyFocus ? 2 : 1)))
                    border.color: slotTile.selected ? OverlayStyle.accent : slotTile.keyFocus ? OverlayStyle.text : "#D8D8D8"
                    Behavior on border.color { ColorAnimation { duration: OverlayStyle.fastDuration } }
                }
                Text {
                    id: label
                    anchors.centerIn: parent
                    width: parent.width - OverlayStyle.u(12)
                    horizontalAlignment: Text.AlignHCenter
                    text: slotTile.index === 0 ? qsTr("None")
                        : String(slotTile.slotStyle.name || "") !== "" ? String(slotTile.slotStyle.name)
                        : String(slotTile.index)
                    color: OverlayStyle.text
                    font.family: Theme.bodyFont
                    font.pixelSize: OverlayStyle.bodySize
                    font.variableAxes: OverlayStyle.bodyAxes
                    elide: Text.ElideRight
                }
                Text {
                    visible: slotTile.index > 0 && slotTile.slotStyle.filters.length > 0
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: OverlayStyle.u(10)
                    text: qsTr("%n filter(s)", "", slotTile.index > 0 ? slotTile.slotStyle.filters.length : 0)
                    color: OverlayStyle.subtitle
                    font.family: Theme.bodyFont
                    font.pixelSize: OverlayStyle.u(15)
                }
            }
        }
    }
    OverlayDivider {}

    // Name, with GeForce NOW's 30-character counter.
    OverlaySectionLabel { text: qsTr("Name"); strong: true; topPadding: 0 }
    OverlayFocusable {
        id: nameField
        objectName: "overlayFilterName"
        width: parent.width
        height: OverlayStyle.u(70)
        showHighlight: false
        onActivated: input.forceActiveFocus()
        Rectangle {
            x: OverlayStyle.gutter
            width: parent.width - OverlayStyle.gutter * 2
            height: parent.height
            color: OverlayStyle.field
            TextInput {
                id: input
                x: OverlayStyle.u(21)
                width: parent.width - x * 2
                anchors.verticalCenter: parent.verticalCenter
                text: page.style.name
                maximumLength: 30
                color: OverlayStyle.text
                selectionColor: OverlayStyle.accentTrack
                font.family: Theme.bodyFont
                font.pixelSize: OverlayStyle.bodySize
                font.variableAxes: OverlayStyle.bodyAxes
                clip: true
                onTextEdited: { const value = text; page.editStyle(style => style.name = value) }
                Keys.onPressed: event => {
                    if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Escape
                            || event.key === Qt.Key_Down || event.key === Qt.Key_Up) {
                        nameField.forceActiveFocus()
                        if (event.key === Qt.Key_Down || event.key === Qt.Key_Up) nameField.moveFocus(event.key === Qt.Key_Down)
                        event.accepted = true
                    }
                }
                Text {
                    visible: input.text === "" && !input.activeFocus
                    anchors.verticalCenter: parent.verticalCenter
                    text: qsTr("Style %1").arg(page.editing)
                    color: "#8A8A8A"
                    font: input.font
                }
            }
            Rectangle {
                anchors.bottom: parent.bottom
                width: parent.width
                height: Math.max(1, Math.round(OverlayStyle.uf(input.activeFocus || nameField.keyFocus ? 2 : 1)))
                color: input.activeFocus || nameField.keyFocus ? OverlayStyle.accent : OverlayStyle.fieldRule
            }
        }
    }
    Text {
        x: OverlayStyle.gutter
        width: parent.width - OverlayStyle.gutter * 2
        horizontalAlignment: Text.AlignRight
        topPadding: OverlayStyle.u(8)
        text: input.text.length + "/30"
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
    }
    OverlaySectionLabel { text: qsTr("Shortcut"); strong: true; topPadding: OverlayStyle.u(4) }
    OverlayKeyField {
        objectName: "overlayFilterShortcut"
        fullWidth: true
        title: qsTr("Shortcut")
        clearable: true
        readonly property string setting: "shortcutGameFilter" + page.editing
        shortcut: String(ShellStore.settings[setting] ?? "")
        onCaptured: chord => {
            const change = {}
            change[setting] = chord
            ShellStore.updateShortcuts(change)
        }
    }
    Item { width: 1; height: OverlayStyle.u(12) }
    OverlayDivider {}

    // Filters in this style, then the "+" list of the ones not added yet.
    Item {
        id: filtersHeader
        width: parent.width
        height: OverlayStyle.u(94)
        Text {
            x: OverlayStyle.gutter
            anchors.verticalCenter: parent.verticalCenter
            text: qsTr("Filters")
            color: OverlayStyle.label
            font.family: Theme.bodyFont
            font.pixelSize: OverlayStyle.labelSize
        }
        OverlayFocusable {
            id: addButton
            objectName: "overlayFilterAdd"
            anchors.right: parent.right
            anchors.rightMargin: OverlayStyle.u(4)
            width: OverlayStyle.u(56)
            height: parent.height
            available: page.available.length > 0
            Accessible.role: Accessible.Button
            Accessible.name: qsTr("Add filter")
            onActivated: addMenu.show()
            OverlayIcon {
                anchors.centerIn: parent
                width: OverlayStyle.u(32)
                height: width
                name: "add"
                ink: addButton.available ? OverlayStyle.text : OverlayStyle.disabled
            }
        }
    }
    OverlayChoiceMenu {
        id: addMenu
        parent: page.popupLayer
        anchorItem: addButton
        alignRight: true
        items: page.available
        onPicked: value => page.editStyle(style => {
            const params = {type: value}
            for (const param of page.entry(value).params) params[param.key] = param.fallback
            if (value === "colorblind") params.mode = "deuteranopia"
            style.filters.push(params)
        })
    }
    Text {
        visible: page.style.filters.length === 0
        x: OverlayStyle.gutter
        width: parent.width - OverlayStyle.gutter * 2
        bottomPadding: OverlayStyle.u(16)
        text: qsTr("Add a filter with +. Changes show on the game behind this panel.")
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
        wrapMode: Text.WordWrap
    }
    Repeater {
        model: page.style.filters
        delegate: Column {
            id: filter
            required property var modelData
            required property int index
            readonly property var info: page.entry(modelData.type) || {label: modelData.type, params: []}
            width: page.width
            OverlayRow {
                title: filter.info.label
                bold: true
                trailing: "none"
                height: OverlayStyle.u(60)
                Accessible.name: qsTr("Remove %1").arg(filter.info.label)
                onActivated: page.editStyle(style => style.filters.splice(filter.index, 1))
                OverlayIcon {
                    anchors.right: parent.right
                    anchors.rightMargin: OverlayStyle.gutter
                    anchors.verticalCenter: parent.verticalCenter
                    width: OverlayStyle.u(26)
                    height: width
                    name: "close"
                    ink: OverlayStyle.subtitle
                }
            }
            OverlayRow {
                visible: filter.modelData.type === "colorblind"
                title: qsTr("Type")
                trailing: "stepper"
                height: OverlayStyle.u(64)
                valueText: (page.colorblindModes.find(m => m.value === filter.modelData.mode) || page.colorblindModes[1]).label
                onStepped: direction => page.editStyle(style => {
                    const modes = page.colorblindModes.map(m => m.value)
                    const current = Math.max(0, modes.indexOf(style.filters[filter.index].mode))
                    style.filters[filter.index].mode = modes[(current + direction + modes.length) % modes.length]
                })
                onActivated: stepped(1)
            }
            Repeater {
                model: filter.info.params
                delegate: OverlaySlider {
                    required property var modelData
                    title: modelData.label
                    from: modelData.from
                    to: modelData.to
                    value: Number(filter.modelData[modelData.key] ?? modelData.fallback)
                    onMoved: next => {
                        const key = modelData.key
                        page.editStyle(style => style.filters[filter.index][key] = next)
                    }
                }
            }
        }
    }
}
