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

    // GeForce NOW's "+" list in its order, with NVIDIA's slider labels, ranges, steps and the
    // defaults a filter starts with when added. Values are the integers NVIDIA's sliders show;
    // the stream renderer converts them (StreamVideoFilter.h, settings.rs keep the same table).
    // Depth filters need the game's depth buffer, which a video stream does not carry; the
    // "pending" ones have no verified NVIDIA definition yet, so they stay listed but greyed.
    function param(key, label, from, to, fallback, step, suffix) {
        return {key: key, label: label, from: from, to: to, fallback: fallback, step: step || 1, suffix: suffix || ""}
    }
    readonly property var catalog: [
        {value: "auto-depth-of-field", label: qsTr("Auto Depth of Field"), depth: true, params: []},
        {value: "black-white", label: qsTr("Black & White"), params: [page.param("intensity", qsTr("Intensity"), 0, 100, 100)]},
        {value: "brightness-contrast", label: qsTr("Brightness / Contrast"), params: [
            page.param("exposure", qsTr("Exposure"), -100, 100, 0, 2),
            page.param("contrast", qsTr("Contrast"), -100, 100, 30, 2),
            page.param("highlights", qsTr("Highlights"), -100, 100, 20, 2),
            page.param("shadows", qsTr("Shadows"), -100, 100, -30, 2),
            page.param("gamma", qsTr("Gamma"), -100, 100, 0, 2)]},
        {value: "color", label: qsTr("Color"), params: [
            page.param("tintColor", qsTr("Tint Color"), 0, 100, 20),
            page.param("tintIntensity", qsTr("Tint Intensity"), 0, 100, 30),
            page.param("temperature", qsTr("Temperature"), -100, 100, 0, 2),
            page.param("vibrance", qsTr("Vibrance"), -100, 100, 0, 2)]},
        {value: "colorblind", label: qsTr("Colorblind"), params: [
            page.param("protanopia", qsTr("Protanopia"), 0, 100, 0),
            page.param("deuteranopia", qsTr("Deuteranopia"), 0, 100, 100),
            page.param("tritanopia", qsTr("Tritanopia"), 0, 100, 0)]},
        {value: "depth-of-field", label: qsTr("Depth of field"), depth: true, params: []},
        {value: "details", label: qsTr("Details"), params: [
            page.param("sharpen", qsTr("Sharpen"), 0, 100, 50),
            page.param("clarity", qsTr("Clarity"), -100, 100, 70, 2),
            page.param("hdrToning", qsTr("HDR Toning"), -100, 100, 60, 2),
            page.param("bloom", qsTr("Bloom"), 0, 100, 15)]},
        {value: "letterbox", label: qsTr("Letterbox"), params: [
            page.param("horizontal", qsTr("Horizontal Scale"), 1, 30, 21),
            page.param("vertical", qsTr("Vertical Scale"), 1, 30, 9)]},
        {value: "night-mode", label: qsTr("Night mode"), params: [page.param("intensity", qsTr("Intensity"), 0, 100, 30)]},
        {value: "old-film", label: qsTr("Old film"), params: [
            page.param("gamma", qsTr("Gamma"), 0, 100, 50),
            page.param("exposure", qsTr("Exposure"), 0, 100, 50),
            page.param("contrast", qsTr("Contrast"), 0, 100, 50),
            page.param("vignette", qsTr("Vignette Amount"), 0, 100, 50),
            page.param("strength", qsTr("Filter Strength"), 0, 100, 100),
            page.param("dirt", qsTr("Film Dirt Strength"), 0, 100, 100)]},
        {value: "painterly", label: qsTr("Painterly"), pending: true, params: []},
        {value: "sharpen", label: qsTr("Sharpen"), params: [
            page.param("sharpen", qsTr("Sharpen"), 0, 100, 50),
            page.param("ignoreGrain", qsTr("Ignore Film Grain"), 0, 100, 15)]},
        {value: "sharpen-plus", label: qsTr("Sharpen+"), params: [
            page.param("sharpen", qsTr("Sharpen"), 0, 100, 50)]},
        {value: "special-fx", label: qsTr("SpecialFX"), pending: true, params: []},
        {value: "splitscreen", label: qsTr("Splitscreen"), pending: true, params: []},
        {value: "tilt-shift", label: qsTr("Tilt-shift"), pending: true, params: []},
        {value: "vignette", label: qsTr("Vignette"), params: [page.param("intensity", qsTr("Intensity"), 0, 100, 70)]},
        {value: "watercolor", label: qsTr("Watercolor"), pending: true, params: []}
    ]
    function entry(type) { return page.catalog.find(item => item.value === type) }
    readonly property var style: draft.styles[editing - 1] || ({name: "", filters: []})
    // Depth filters stay in the list, as on GeForce NOW, but can't be added: a video stream
    // carries no depth buffer, so the client has nothing to focus with.
    readonly property var available: page.catalog.filter(item => !page.style.filters.some(f => f.type === item.value))
        .map(item => ({value: item.value, label: item.label, disabled: item.depth === true || item.pending === true}))
    readonly property bool canAdd: page.style.filters.length < 8 && page.available.some(item => !item.disabled)

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
                    border.width: Math.max(1, Math.round(OverlayStyle.uf(slotTile.selected || slotTile.keyFocus ? 2 : 0)))
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
                    font.weight: OverlayStyle.bodyWeight
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
    OverlaySectionLabel { text: qsTr("Name"); strong: true; topPadding: OverlayStyle.u(5) }
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
                font.weight: OverlayStyle.bodyWeight
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
        topPadding: OverlayStyle.u(11)
        text: input.text.length + "/30"
        color: OverlayStyle.subtitle
        font.family: Theme.bodyFont
        font.pixelSize: OverlayStyle.subtitleSize
    }
    OverlaySectionLabel { text: qsTr("Shortcut"); strong: true; topPadding: OverlayStyle.u(15); bottomPadding: OverlayStyle.u(25) }
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
        height: OverlayStyle.u(78)
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
            anchors.rightMargin: OverlayStyle.u(11)
            anchors.verticalCenter: parent.verticalCenter
            width: OverlayStyle.u(56)
            height: OverlayStyle.u(48)
            available: page.canAdd
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
            style.filters.push(params)
        })
    }
    Text {
        visible: page.style.filters.length === 0
        x: OverlayStyle.gutter
        width: parent.width - OverlayStyle.gutter * 2
        bottomPadding: OverlayStyle.u(16)
        text: qsTr("Add a filter with +. Changes show on the game behind this panel. Greyed filters need the game's depth information or aren't available in Cloudlight yet.")
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
            Repeater {
                model: filter.info.params
                delegate: OverlaySlider {
                    required property var modelData
                    title: modelData.label
                    from: modelData.from
                    to: modelData.to
                    step: modelData.step
                    suffix: modelData.suffix
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
