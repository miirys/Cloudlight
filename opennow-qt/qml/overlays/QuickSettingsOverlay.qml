pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import OpenNOW

FocusScope {
    id: root
    width: 560
    height: 900
    focus: true
    Accessible.role: Accessible.Pane
    Accessible.name: qsTr("Quick settings")

    readonly property var bitrates: [25, 50, 75, 100, 150, 200]
    readonly property var controllers: ControllerInput.controllers || []
    readonly property string tier: String(ShellStore.subscription
        && ShellStore.subscription.membershipTier || "")

    function nextBitrate() {
        const current = Number(ShellStore.settings.maxBitrateMbps || 75)
        const index = root.bitrates.indexOf(current)
        ShellStore.setSetting("maxBitrateMbps",
            root.bitrates[(index + 1) % root.bitrates.length])
    }
    function openAllSettings() {
        AppController.showOverlay("")
        AppController.navigate("settings-streaming")
    }

    // One list row: label left, value or control right, hairline divider below.
    // Focus shows as a raised fill with an accent bar, as in the in-stream menu.
    component QuickRow: Rectangle {
        id: row
        required property string title
        property string value: ""
        property bool toggleVisible: false
        property bool checked: false
        property bool sliderVisible: false
        property real sliderProgress: 0
        signal triggered()

        width: root.contentWidth
        height: 68
        color: activeFocus ? Theme.surfaceHover : "transparent"
        activeFocusOnTab: enabled
        opacity: enabled ? 1 : 0.45
        Accessible.role: Accessible.Button
        Accessible.name: title

        Rectangle { visible: row.activeFocus; width: 4; height: parent.height; color: Theme.focus }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.seam }
        Text {
            x: 24
            width: parent.width - controlArea.width - 72
            anchors.verticalCenter: parent.verticalCenter
            text: row.title
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: 19
            font.weight: row.activeFocus ? Font.DemiBold : Font.Medium
            elide: Text.ElideRight
        }
        Item {
            id: controlArea
            anchors.right: parent.right
            anchors.rightMargin: 24
            anchors.verticalCenter: parent.verticalCenter
            width: 220
            height: 36

            Row {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                spacing: 14
                visible: row.sliderVisible
                Rectangle {
                    width: 110; height: 4; radius: 2
                    anchors.verticalCenter: parent.verticalCenter
                    color: Theme.surfaceStrong
                    Rectangle {
                        width: parent.width * Math.max(0, Math.min(1, row.sliderProgress))
                        height: parent.height; radius: parent.radius
                        color: Theme.focus
                    }
                }
                Text {
                    anchors.verticalCenter: parent.verticalCenter
                    text: row.value
                    color: Theme.textMuted
                    font.family: Theme.bodyFont
                    font.pixelSize: 17
                    font.features: { "tnum": 1 }
                }
            }
            Rectangle {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                width: 48; height: 26; radius: height / 2
                visible: row.toggleVisible
                color: row.checked ? Theme.focus : Theme.surfaceStrong
                Rectangle {
                    x: row.checked ? parent.width - width - 4 : 4
                    anchors.verticalCenter: parent.verticalCenter
                    width: 18; height: 18; radius: 9
                    color: row.checked ? Theme.focusText : Theme.label
                    Behavior on x { NumberAnimation { duration: AppController.reducedMotion ? 0 : 110; easing.type: Easing.OutCubic } }
                }
            }
            Text {
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                visible: !row.sliderVisible && !row.toggleVisible
                width: parent.width
                horizontalAlignment: Text.AlignRight
                text: row.value
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: 17
                elide: Text.ElideRight
            }
        }
        HoverHandler { cursorShape: row.enabled ? Qt.PointingHandCursor : Qt.ForbiddenCursor }
        TapHandler { enabled: row.enabled; onTapped: { row.forceActiveFocus(); row.triggered() } }
        Keys.onPressed: event => {
            if ((event.key === Qt.Key_Return || event.key === Qt.Key_Enter
                    || event.key === Qt.Key_Space) && row.enabled) {
                row.triggered()
                event.accepted = true
            }
        }
    }

    component ControllerRow: Item {
        id: controllerRow
        required property var controller
        width: root.contentWidth
        height: 60
        readonly property int battery: Number(controller.batteryPercent === undefined
            ? -1 : controller.batteryPercent)

        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.seam }
        Text {
            x: 24; width: 56; anchors.verticalCenter: parent.verticalCenter
            text: qsTr("P%1").arg(Number(controllerRow.controller.slot || 1))
            color: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: 17
            font.weight: Font.DemiBold
        }
        Text {
            x: 80; width: parent.width - x - 150; anchors.verticalCenter: parent.verticalCenter
            text: String(controllerRow.controller.name || qsTr("Game controller"))
            color: Theme.label
            font.family: Theme.bodyFont
            font.pixelSize: 17
            elide: Text.ElideRight
        }
        Text {
            anchors.right: parent.right; anchors.rightMargin: 24
            anchors.verticalCenter: parent.verticalCenter
            text: controllerRow.battery >= 0 ? qsTr("Battery %1%").arg(controllerRow.battery) : qsTr("Connected")
            color: controllerRow.battery >= 0 && controllerRow.battery < 30 ? Theme.yellow : Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: 16
        }
    }

    component SectionLabel: Item {
        property alias text: label.text
        width: root.contentWidth
        height: 52
        Text {
            id: label
            x: 24
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 10
            color: Theme.textMuted
            font.family: Theme.bodyFont
            font.pixelSize: 16
            font.weight: Font.DemiBold
        }
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.seam }
    }

    readonly property int contentWidth: width

    Rectangle {
        anchors.fill: parent
        color: Theme.surface
        Rectangle { width: 1; height: parent.height; color: Theme.seam }

        Column {
            width: parent.width

            Item {
                width: parent.width; height: 112
                Column {
                    x: 24; anchors.verticalCenter: parent.verticalCenter
                    spacing: 6
                    Text { text: qsTr("Quick settings"); color: Theme.label; font.family: Theme.displayFont; font.pixelSize: 26; font.weight: Font.Bold }
                    Text { text: root.tier !== "" ? qsTr("%1 · applies to your next launch").arg(root.tier) : qsTr("Applies to your next launch"); color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 16 }
                }
                Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.seam }
            }

            QuickRow {
                id: regionRow
                title: qsTr("Server location")
                value: String(ShellStore.selectedRegion || qsTr("Automatic"))
                KeyNavigation.down: bitrateRow
                onTriggered: { AppController.showOverlay(""); AppController.navigate("settings-network") }
            }
            QuickRow {
                id: bitrateRow
                title: qsTr("Maximum bit rate")
                value: Math.round(Number(ShellStore.settings.maxBitrateMbps || 75)) + " Mbps"
                sliderVisible: true
                sliderProgress: Number(ShellStore.settings.maxBitrateMbps || 75) / 200
                KeyNavigation.up: regionRow; KeyNavigation.down: statsRow
                onTriggered: root.nextBitrate()
            }
            QuickRow {
                id: statsRow
                title: qsTr("Statistics overlay")
                toggleVisible: true
                checked: Boolean(ShellStore.settings.showNativeStreamerStats)
                KeyNavigation.up: bitrateRow; KeyNavigation.down: syncRow
                onTriggered: ShellStore.setSetting("showNativeStreamerStats", !checked)
            }
            QuickRow {
                id: syncRow
                title: qsTr("Cloud G-SYNC")
                toggleVisible: true
                checked: Boolean(ShellStore.settings.enableCloudGsync)
                KeyNavigation.up: statsRow; KeyNavigation.down: micRow
                onTriggered: ShellStore.setSetting("enableCloudGsync", !checked)
            }
            QuickRow {
                id: micRow
                title: qsTr("Microphone")
                value: ShellStore.microphoneLabel
                enabled: ShellStore.microphoneCanToggle
                KeyNavigation.up: syncRow
                KeyNavigation.down: controllerButton.visible ? controllerButton : null
                onTriggered: ShellStore.toggleMicrophone()
            }

            SectionLabel { text: qsTr("Controllers (%1 connected)").arg(AppController.controllerCount) }
            Repeater {
                model: root.controllers.slice(0, 2)
                delegate: ControllerRow { required property var modelData; controller: modelData }
            }
            QuickRow {
                id: controllerButton
                visible: root.controllers.length === 0
                title: qsTr("Connect player two")
                KeyNavigation.up: micRow
                onTriggered: { AppController.showOverlay(""); AppController.navigate("joining") }
            }
        }

        Item {
            anchors.bottom: parent.bottom
            width: parent.width; height: 72
            Rectangle { width: parent.width; height: 1; color: Theme.seam }
            Row {
                x: 24; anchors.verticalCenter: parent.verticalCenter; spacing: 28
                Repeater {
                    model: [
                        { key: "A", label: qsTr("Change") },
                        { key: "Y", label: qsTr("All settings") },
                        { key: "RT", label: qsTr("Close") }
                    ]
                    delegate: Row {
                        required property var modelData
                        spacing: 10
                        ControllerGlyph { glyph: modelData.key; label: ""; glyphSize: 28; glyphColor: Theme.label }
                        Text { anchors.verticalCenter: parent.verticalCenter; text: modelData.label; color: Theme.textMuted; font.family: Theme.bodyFont; font.pixelSize: 16 }
                    }
                }
            }
        }
    }

    onVisibleChanged: if (visible) Qt.callLater(regionRow.forceActiveFocus)
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Y) {
            root.openAllSettings()
            event.accepted = true
        }
    }
}
