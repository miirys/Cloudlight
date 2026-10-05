import QtQuick
import QtQuick.Controls
import OpenNOW

// The same inline disclosure/tile pattern as the resolution picker, for
// account-provided regions and longer lists such as interface languages.
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
    implicitHeight: header.height + (search.height + optionsHeight + DesktopTokens.px(28)) * reveal.progress
        + (footerContent.implicitHeight > 0 ? footerContent.implicitHeight + DesktopTokens.px(12) : 0)
    clip: true
    MotionProgress { id: reveal; shown: root.expanded; enterDuration: 200; exitDuration: 160 }
    onExpandedChanged: {
        if (expanded) { search.clear(); search.forceActiveFocus() }
        else selector.forceActiveFocus()
    }
    Rectangle {
        visible: reveal.present
        opacity: reveal.progress
        x: 0; y: header.height; width: parent.width; height: parent.height - header.height
        radius: DesktopTokens.radius; color: Theme.surface
        border.width: 0; border.color: Theme.seam
    }
    DesktopSettingsRow {
        id: header
        width: parent.width; paperStyle: true; glyph: root.glyph
        title: root.title; description: root.description
        expanded: root.expanded
        showDivider: root.showDivider && !root.expanded
        DesktopSettingsButton {
            id: selector
            width: header.controlWidth
            menu: true
            text: root.valueLabel || (root.current ? root.current.label : String(root.value))
            Accessible.name: root.title + ": " + text
            onClicked: root.expanded = !root.expanded
        }
    }
    DesktopSettingsField {
        id: search
        enabled: root.expanded
        opacity: reveal.progress
        x: DesktopTokens.px(12); y: header.height + DesktopTokens.px(12); width: parent.width - DesktopTokens.px(24)
        visible: reveal.present && root.items.length > 8
        height: visible ? implicitHeight : 0
        placeholderText: root.filterPlaceholder
        onTextChanged: scroll.contentY = 0
        Accessible.name: root.title + ": " + placeholderText
        Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
    }
    Flickable {
        id: scroll
        visible: reveal.present
        enabled: root.expanded
        opacity: reveal.progress
        x: DesktopTokens.px(4); y: search.y + search.height + DesktopTokens.px(4); width: parent.width - DesktopTokens.px(8)
        height: root.optionsHeight
        contentWidth: width; contentHeight: grid.implicitHeight
        clip: true; boundsBehavior: Flickable.StopAtBounds
        ScrollBar.vertical: ScrollBar { policy: scroll.contentHeight > scroll.height ? ScrollBar.AlwaysOn : ScrollBar.AlwaysOff }
        Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
        Column {
            id: grid
            width: parent.width - 12; spacing: DesktopTokens.px(6)
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
                    Flow {
                        width: parent.width; spacing: 0
                        readonly property int columns: 1
                        Repeater {
                            model: modelData.items
                            delegate: AbstractButton {
                                id: tile
                                required property var modelData
                                readonly property bool chosen: enabled && String(modelData.value) === String(root.value)
                                objectName: "settingsChoice-" + String(modelData.value)
                                width: parent.width
                                height: DesktopTokens.px(tile.modelData.detail ? 48 : 38)
                                enabled: !modelData.disabled; opacity: enabled ? 1 : 0.45
                                hoverEnabled: true
                                Accessible.name: String(modelData.label) + " " + String(modelData.detail || "")
                                onClicked: { root.expanded = false; root.selected(modelData.value) }
                                Keys.onReturnPressed: event => { tile.clicked(); event.accepted = true }
                                Keys.onEnterPressed: event => { tile.clicked(); event.accepted = true }
                                Keys.onEscapePressed: event => { root.expanded = false; event.accepted = true }
                                background: Rectangle {
                                    radius: DesktopTokens.radius
                                    color: tile.hovered || tile.activeFocus ? DesktopTokens.hover : "transparent"
                                    border.width: tile.activeFocus ? 2 : 0
                                    border.color: Theme.focus
                                    Rectangle {
                                        visible: tile.chosen
                                        x: 0; y: DesktopTokens.px(8); width: DesktopTokens.px(3); height: parent.height - DesktopTokens.px(16)
                                        color: Theme.focus
                                    }
                                }
                                DesktopSettingsIcon {
                                    id: optionIcon
                                    visible: !!tile.modelData.glyph
                                    anchors.left: parent.left; anchors.leftMargin: 12
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: 20; height: 20
                                    glyph: tile.modelData.glyph || "controller"
                                    ink: Theme.label
                                }
                                Column {
                                    anchors.verticalCenter: parent.verticalCenter
                                    x: optionIcon.visible ? 42 : DesktopTokens.px(14)
                                    width: parent.width - x - 12; spacing: 2
                                    Text { width: parent.width; text: tile.modelData.label; color: tile.chosen ? Theme.focus : Theme.label; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(14); font.weight: tile.chosen ? Font.DemiBold : Font.Normal; elide: Text.ElideRight }
                                    Text { visible: text !== ""; width: parent.width; text: tile.modelData.detail || ""; color: tile.modelData.detailColor || Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(12); elide: Text.ElideRight }
                                }
                            }
                        }
                    }
                }
            }
            Text { visible: root.groups.length === 0; text: qsTr("No matching options"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(13) }
        }
    }
    Column {
        id: footerContent
        x: header.labelInset
        y: header.height + (search.height + root.optionsHeight + DesktopTokens.px(28)) * reveal.progress
        width: parent.width - x - DesktopTokens.settingsInset
        spacing: DesktopTokens.px(8)
    }
}
