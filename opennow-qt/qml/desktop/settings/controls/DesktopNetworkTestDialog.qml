pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import OpenNOW

// GeForce NOW's network test idea (a path from this computer to the server,
// then measured / required / recommended), drawn calmly. Thresholds come from
// the service's own test session; nothing is estimated or invented here.
Popup {
    id: root
    objectName: "networkTestDialog"
    parent: Overlay.overlay
    anchors.centerIn: parent
    width: Math.min(DesktopTokens.px(600), (parent ? parent.width : 600) - DesktopTokens.px(32))
    padding: DesktopTokens.px(28)
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside

    readonly property string testState: ShellStore.networkTestState
    readonly property var result: ShellStore.networkTestResult || ({})
    readonly property var thresholds: result.thresholds || ({})
    readonly property bool running: testState === "running"
    readonly property bool manualRegion: String(ShellStore.settings.region || "") !== ""

    function regionName() {
        const zone = String(result.zone || "")
        const regions = ShellStore.regions || []
        for (const region of regions) {
            if (zone !== "" && String(region.url).indexOf(zone) >= 0)
                return region.name
        }
        const selected = String(ShellStore.settings.region || "")
        const chosen = regions.find(region => region.url === selected || region.name === selected)
        return chosen ? chosen.name : zone
    }

    // 2 good (beats recommended), 1 fair (beats required), 0 poor, -1 unknown.
    function grade(value, recommended, required) {
        if (value === null || value === undefined || !Number.isFinite(Number(value)))
            return -1
        const v = Number(value)
        if (Number.isFinite(Number(recommended)) && v < Number(recommended)) return 2
        if (Number.isFinite(Number(required)) && v < Number(required)) return 1
        return 0
    }
    readonly property int latencyGrade: grade(result.latencyMs, thresholds.latencyRecommendedMs, thresholds.latencyLimitMs)
    readonly property int lossGrade: grade(result.packetLossPct, thresholds.packetLossRecommendedPct, thresholds.packetLossLimitPct)
    readonly property int overall: result.status === "unreachable" ? 0
        : Math.min(...[latencyGrade, lossGrade].filter(value => value >= 0).concat([2]))

    function gradeColor(value) {
        return value === 2 ? Theme.mint : value === 1 ? Theme.yellow : value === 0 ? Theme.coral : Theme.textMuted
    }

    function headline() {
        if (testState === "failed") return qsTr("The network test couldn't run")
        if (running || testState === "idle") return qsTr("Analyzing network")
        if (result.status === "unreachable") return qsTr("Your network does not meet the requirements")
        return overall === 2 ? qsTr("Your network is ready")
            : overall === 1 ? qsTr("You may experience stutter or high latency")
            : qsTr("Your network does not meet the requirements")
    }

    function tips() {
        const out = []
        if (testState !== "done") return out
        if (root.manualRegion)
            out.push(qsTr("You have set a specific server location which may affect the network results."))
        if (result.status === "unreachable")
            out.push(qsTr("No reply came back from the test server. A firewall or VPN may be blocking UDP traffic."))
        if (latencyGrade >= 0 && latencyGrade < 2)
            out.push(qsTr("Try a server location closer to you, or use Automatic."))
        if (lossGrade >= 0 && lossGrade < 2)
            out.push(qsTr("Use a wired connection or 5 GHz Wi-Fi, and stop other streams, uploads or downloads on your network."))
        if (out.length === 0 || overall < 2)
            out.push(qsTr("Restart your router if problems continue."))
        return out
    }

    function formatNumber(value, digits) {
        return value === null || value === undefined || !Number.isFinite(Number(value)) ? "—" : Number(value).toFixed(digits)
    }

    onOpened: ShellStore.runNetworkTest()
    onClosed: ShellStore.cancelNetworkTest()

    enter: Transition {
        NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 200 }
        NumberAnimation { property: "scale"; from: 0.96; to: 1; duration: Theme.springDuration; easing.type: Easing.BezierSpline; easing.bezierCurve: Theme.spring }
    }
    exit: Transition {
        NumberAnimation { property: "opacity"; to: 0; duration: 140 }
        NumberAnimation { property: "scale"; to: 0.98; duration: 140 }
    }

    Overlay.modal: Rectangle { color: Qt.rgba(0, 0, 0, 0.45) }

    background: Item {
        MultiEffect {
            source: card
            anchors.fill: card
            shadowEnabled: true
            shadowColor: Qt.rgba(0, 0, 0, 0.45)
            shadowBlur: 1
            shadowVerticalOffset: DesktopTokens.px(12)
            blurMax: 48
        }
        Rectangle { id: card; anchors.fill: parent; radius: DesktopTokens.px(18); color: Theme.surfaceRaised }
    }

    contentItem: Column {
        spacing: DesktopTokens.px(22)

        Text {
            width: parent.width
            text: root.headline()
            color: Theme.label
            font.family: Theme.displayFont
            font.pixelSize: DesktopTokens.px(22)
            font.weight: Font.DemiBold
            wrapMode: Text.WordWrap
        }

        // Path: this computer → network → GeForce NOW server.
        Item {
            id: path
            width: parent.width
            height: DesktopTokens.px(84)
            readonly property var nodes: [
                {glyph: "monitor", label: qsTr("My computer")},
                {glyph: "wave", label: qsTr("Network")},
                {glyph: "globe", label: root.testState === "done" ? root.regionName() : qsTr("GeForce NOW")}
            ]
            readonly property real nodeSize: DesktopTokens.px(44)
            readonly property real step: (width - nodeSize) / 2
            readonly property color linkColor: root.testState === "done" ? root.gradeColor(root.overall)
                : root.testState === "failed" ? Theme.coral : Theme.textMuted

            Repeater {
                model: 2
                delegate: Item {
                    id: link
                    required property int index
                    x: path.nodeSize + index * path.step + DesktopTokens.px(8)
                    y: path.nodeSize / 2 - height / 2
                    width: path.step - path.nodeSize - DesktopTokens.px(16)
                    height: DesktopTokens.px(4)
                    Rectangle { anchors.fill: parent; radius: height / 2; color: Theme.surfaceStrong }
                    Rectangle {
                        width: root.running ? parent.width : root.testState === "done" || root.testState === "failed" ? parent.width : 0
                        height: parent.height; radius: height / 2
                        color: path.linkColor
                        opacity: root.running ? 0 : 1
                        Behavior on opacity { NumberAnimation { duration: 260 } }
                    }
                    Rectangle {
                        // Travelling pulse while the test runs.
                        visible: root.running
                        width: DesktopTokens.px(10); height: width; radius: width / 2
                        y: (parent.height - height) / 2
                        color: Theme.focus
                        SequentialAnimation on x {
                            running: root.running && root.visible
                            loops: Animation.Infinite
                            PauseAnimation { duration: link.index * 450 }
                            NumberAnimation { from: 0; to: link.width - DesktopTokens.px(10); duration: 900; easing.type: Easing.InOutSine }
                            PauseAnimation { duration: (1 - link.index) * 450 }
                        }
                    }
                }
            }
            Repeater {
                model: path.nodes
                delegate: Column {
                    id: node
                    required property int index
                    required property var modelData
                    x: index * path.step + path.nodeSize / 2 - width / 2
                    width: DesktopTokens.px(150)
                    spacing: DesktopTokens.px(8)
                    Rectangle {
                        anchors.horizontalCenter: parent.horizontalCenter
                        width: path.nodeSize; height: width; radius: width / 2
                        color: Theme.surfaceStrong
                        DesktopSettingsIcon {
                            anchors.centerIn: parent
                            width: DesktopTokens.px(20); height: width
                            glyph: node.modelData.glyph
                            ink: Theme.label
                        }
                    }
                    Text {
                        width: parent.width
                        horizontalAlignment: Text.AlignHCenter
                        text: node.modelData.label
                        color: DesktopTokens.textBody
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.px(13)
                        font.weight: Font.Medium
                        elide: Text.ElideRight
                    }
                }
            }
        }

        Text {
            visible: root.testState === "failed"
            width: parent.width
            text: ShellStore.networkTestMessage
            color: DesktopTokens.textBody
            font.family: Theme.bodyFont
            font.pixelSize: DesktopTokens.px(14)
            wrapMode: Text.WordWrap
        }

        // Measured / Required / Recommended, as in GeForce NOW.
        Column {
            visible: root.testState === "done"
            width: parent.width
            spacing: 0
            Row {
                width: parent.width
                height: DesktopTokens.px(28)
                Repeater {
                    model: ["", qsTr("Measured"), qsTr("Required"), qsTr("Recommended")]
                    delegate: Text {
                        required property int index
                        required property string modelData
                        width: index === 0 ? parent.width * 0.34 : parent.width * 0.22
                        horizontalAlignment: index === 0 ? Text.AlignLeft : Text.AlignRight
                        text: modelData
                        color: Theme.textMuted
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.px(12)
                        font.weight: Font.DemiBold
                    }
                }
            }
            Repeater {
                model: [
                    {label: qsTr("Latency (ms)"), measured: root.formatNumber(root.result.latencyMs, 0),
                     required: "< " + root.formatNumber(root.thresholds.latencyLimitMs, 0),
                     recommended: "< " + root.formatNumber(root.thresholds.latencyRecommendedMs, 0), grade: root.latencyGrade},
                    {label: qsTr("Packet loss (%)"), measured: root.formatNumber(root.result.packetLossPct, 1),
                     required: "< " + root.formatNumber(root.thresholds.packetLossLimitPct, 1),
                     recommended: "< " + root.formatNumber(root.thresholds.packetLossRecommendedPct, 1), grade: root.lossGrade},
                    {label: qsTr("Bandwidth (Mbps)"), measured: qsTr("Not measured"),
                     required: "> " + root.formatNumber(root.thresholds.bandwidthLimitMbps, 0),
                     recommended: "> " + root.formatNumber(root.thresholds.bandwidthRecommendedMbps, 0), grade: -1}
                ]
                delegate: Item {
                    id: metric
                    required property int index
                    required property var modelData
                    width: parent.width
                    height: DesktopTokens.px(44)
                    Rectangle {
                        anchors.fill: parent
                        radius: DesktopTokens.px(10)
                        color: metric.index % 2 === 0 ? Theme.surface : "transparent"
                    }
                    Row {
                        anchors.fill: parent
                        anchors.leftMargin: DesktopTokens.px(12)
                        anchors.rightMargin: DesktopTokens.px(12)
                        Text {
                            width: parent.width * 0.34
                            height: parent.height
                            verticalAlignment: Text.AlignVCenter
                            text: metric.modelData.label
                            color: Theme.label
                            font.family: Theme.bodyFont
                            font.pixelSize: DesktopTokens.px(14)
                            font.weight: Font.Medium
                        }
                        Item {
                            width: parent.width * 0.22
                            height: parent.height
                            Row {
                                anchors.right: parent.right
                                anchors.verticalCenter: parent.verticalCenter
                                spacing: DesktopTokens.px(8)
                                Rectangle {
                                    visible: metric.modelData.grade >= 0
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: DesktopTokens.px(8); height: width; radius: width / 2
                                    color: root.gradeColor(metric.modelData.grade)
                                }
                                Text {
                                    text: metric.modelData.measured
                                    color: metric.modelData.grade >= 0 ? Theme.label : Theme.textMuted
                                    font.family: Theme.bodyFont
                                    font.pixelSize: DesktopTokens.px(14)
                                    font.weight: Font.DemiBold
                                    font.features: { "tnum": 1 }
                                }
                            }
                        }
                        Repeater {
                            model: [metric.modelData.required, metric.modelData.recommended]
                            delegate: Text {
                                required property string modelData
                                width: metric.width * 0.22 - DesktopTokens.px(12)
                                height: metric.height
                                verticalAlignment: Text.AlignVCenter
                                horizontalAlignment: Text.AlignRight
                                text: modelData
                                color: DesktopTokens.textBody
                                font.family: Theme.bodyFont
                                font.pixelSize: DesktopTokens.px(14)
                                font.features: { "tnum": 1 }
                            }
                        }
                    }
                }
            }
            Text {
                width: parent.width
                topPadding: DesktopTokens.px(10)
                text: {
                    const parts = []
                    if (Number.isFinite(Number(root.result.jitterMs)))
                        parts.push(qsTr("Jitter %1 ms").arg(Number(root.result.jitterMs).toFixed(1)))
                    if (Number.isFinite(Number(root.result.mtuBytes)))
                        parts.push(qsTr("Path MTU %1 bytes").arg(root.result.mtuBytes))
                    parts.push(qsTr("%1 of %2 probes answered").arg(root.result.probesReceived || 0).arg(root.result.probesSent || 0))
                    return parts.join("  ·  ") + "\n" + qsTr("Bandwidth isn't measured yet: GeForce NOW's bandwidth probe hasn't been verified, so Cloudlight won't guess a number.")
                }
                color: Theme.textMuted
                font.family: Theme.bodyFont
                font.pixelSize: DesktopTokens.px(12)
                lineHeight: 1.25
                wrapMode: Text.WordWrap
            }
        }

        Column {
            visible: tipsRepeater.count > 0
            width: parent.width
            spacing: DesktopTokens.px(8)
            Repeater {
                id: tipsRepeater
                model: root.tips()
                delegate: Row {
                    required property string modelData
                    width: parent.width
                    spacing: DesktopTokens.px(10)
                    Rectangle { y: DesktopTokens.px(7); width: DesktopTokens.px(5); height: width; radius: width / 2; color: Theme.textMuted }
                    Text {
                        width: parent.width - DesktopTokens.px(15)
                        text: modelData
                        color: DesktopTokens.textBody
                        font.family: Theme.bodyFont
                        font.pixelSize: DesktopTokens.px(14)
                        lineHeight: 1.2
                        wrapMode: Text.WordWrap
                    }
                }
            }
        }

        Row {
            anchors.right: parent.right
            spacing: DesktopTokens.px(10)
            DesktopSettingsButton {
                objectName: "networkTestClose"
                text: qsTr("Close")
                onClicked: root.close()
            }
            DesktopSettingsButton {
                objectName: "networkTestRetry"
                primary: true
                text: qsTr("Try again")
                enabled: !root.running
                onClicked: ShellStore.runNetworkTest()
            }
        }
    }
}
