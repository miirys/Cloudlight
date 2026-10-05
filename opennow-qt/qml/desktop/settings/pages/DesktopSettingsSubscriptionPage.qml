import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import OpenNOW

// Membership summary in plain words: which plan, the best the plan streams at,
// and how much playtime is left with when it resets.
DesktopSettingsPanel {
    id: page
    required property real availableWidth
    required property var settingsScreen

    readonly property var subscription: ShellStore.subscription
    readonly property string tierName: {
        const tier = page.subscription ? String(page.subscription.membershipTier || "") : ""
        return tier === "" ? "" : tier.charAt(0).toUpperCase() + tier.slice(1).toLowerCase()
    }
    readonly property var entitled: page.subscription ? (page.subscription.entitledResolutions || []) : []

    function resolutionName(width, height) {
        if (width >= 7680) return "8K"
        if (width >= 5120 && height >= 2880) return "5K"
        if (width >= 3840 && height >= 2160) return "4K"
        if (height > 0) return qsTr("%1p").arg(height)
        return ""
    }
    // The highest resolution and its best frame rate, then the fastest frame rate
    // if it needs a lower resolution.
    function qualityLines() {
        if (!page.entitled.length) return []
        let best = page.entitled[0]
        for (const item of page.entitled) {
            const w = Number(item.width || 0), h = Number(item.height || 0), f = Number(item.fps || 0)
            const bw = Number(best.width || 0), bh = Number(best.height || 0), bf = Number(best.fps || 0)
            if (w * h > bw * bh || (w * h === bw * bh && f > bf)) best = item
        }
        let fastest = best
        for (const item of page.entitled) {
            const f = Number(item.fps || 0), ff = Number(fastest.fps || 0)
            if (f > ff || (f === ff && Number(item.width || 0) * Number(item.height || 0)
                    > Number(fastest.width || 0) * Number(fastest.height || 0))) fastest = item
        }
        const lines = [qsTr("Up to %1 at %2 FPS")
            .arg(page.resolutionName(Number(best.width || 0), Number(best.height || 0))).arg(Number(best.fps || 0))]
        if (Number(fastest.fps || 0) > Number(best.fps || 0))
            lines.push(qsTr("Up to %1 FPS at %2").arg(Number(fastest.fps || 0))
                .arg(page.resolutionName(Number(fastest.width || 0), Number(fastest.height || 0))))
        return lines
    }
    function hoursText(hours) {
        const minutes = Math.max(0, Math.round(Number(hours || 0) * 60))
        const h = Math.floor(minutes / 60), m = minutes % 60
        if (h === 0) return qsTr("%1 min").arg(m)
        return m === 0 ? qsTr("%1 h").arg(h) : qsTr("%1 h %2 min").arg(h).arg(m)
    }
    readonly property bool unlimited: Boolean(page.subscription && page.subscription.isUnlimited)
    readonly property bool hasPlaytime: Boolean(page.subscription) && !page.unlimited
        && page.subscription.remainingHours !== undefined && page.subscription.remainingHours !== null
    readonly property real totalHours: page.subscription ? Number(page.subscription.totalHours || 0) : 0
    readonly property real remainingHours: page.subscription ? Number(page.subscription.remainingHours || 0) : 0
    readonly property string resetText: {
        const end = page.subscription ? page.subscription.currentSpanEndDateTime : null
        const date = end ? new Date(end) : null
        return date && !isNaN(date.getTime())
            ? qsTr("Resets %1").arg(date.toLocaleDateString(Qt.locale(), "MMMM d")) : ""
    }

    width: page.availableWidth; paperStyle: true
    DesktopSettingsSection { text: qsTr("Membership") }

    DesktopSettingsRow {
        objectName: "membershipPlanRow"
        width: parent.width; paperStyle: true; glyph: "crown"
        title: page.tierName !== "" ? page.tierName
            : ShellStore.signedIn ? qsTr("Membership unavailable") : qsTr("Not signed in")
        description: page.subscription ? page.qualityLines().join("\n")
            : ShellStore.signedIn ? qsTr("Your plan could not be loaded. Try refreshing.")
            : qsTr("Sign in to see your plan.")
        showDivider: false
        DesktopSettingsButton { text: qsTr("Manage on NVIDIA"); onClicked: AppController.openExternalUrl("https://www.nvidia.com/en-us/account/") }
    }

    // Playtime: what is left this period, out of how much, and when it resets.
    Item {
        objectName: "membershipPlaytime"
        visible: page.hasPlaytime || page.unlimited
        width: parent.width
        implicitHeight: playtime.implicitHeight + DesktopTokens.px(24)
        Column {
            id: playtime
            y: DesktopTokens.px(8)
            width: parent.width
            spacing: DesktopTokens.px(8)
            RowLayout {
                width: parent.width
                spacing: DesktopTokens.px(16)
                Column {
                    Layout.fillWidth: true
                    spacing: DesktopTokens.px(3)
                    Text {
                        text: qsTr("Playtime left")
                        color: Theme.label
                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(15); font.weight: Font.Medium
                    }
                    Text {
                        visible: text !== ""
                        text: page.unlimited ? qsTr("No monthly limit on your plan")
                            : [page.resetText,
                               Number(page.subscription && page.subscription.rolledOverHours || 0) > 0
                                   ? qsTr("includes %1 rolled over").arg(page.hoursText(page.subscription.rolledOverHours)) : ""]
                                .filter(part => part !== "").join(" · ")
                        color: Theme.textMuted
                        font.family: Theme.bodyFont; font.pixelSize: DesktopTokens.px(13)
                    }
                }
                Text {
                    Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
                    text: page.unlimited ? qsTr("Unlimited")
                        : page.totalHours > 0
                        ? qsTr("%1 of %2").arg(page.hoursText(page.remainingHours)).arg(page.hoursText(page.totalHours))
                        : page.hoursText(page.remainingHours)
                    color: Theme.label
                    font.family: DesktopTokens.displayFont; font.pixelSize: DesktopTokens.px(17); font.weight: Font.DemiBold
                }
            }
            Rectangle {
                visible: !page.unlimited && page.totalHours > 0
                width: parent.width; height: DesktopTokens.px(6); radius: height / 2
                color: DesktopTokens.raisedStrong
                Rectangle {
                    width: parent.width * Math.max(0, Math.min(1, page.remainingHours / Math.max(0.01, page.totalHours)))
                    height: parent.height; radius: parent.radius
                    color: page.remainingHours / Math.max(0.01, page.totalHours) < 0.1 ? Theme.coral : DesktopTokens.focus
                    Behavior on width { enabled: !AppController.reducedMotion; NumberAnimation { duration: 400; easing.type: Easing.OutCubic } }
                }
            }
        }
    }

    DesktopSettingsRow {
        width: parent.width; paperStyle: true; glyph: "refresh"
        title: qsTr("Refresh membership")
        description: qsTr("Reload your plan and playtime from NVIDIA.")
        showDivider: false
        DesktopSettingsButton { text: qsTr("Refresh"); onClicked: ShellStore.refreshAccountServices() }
    }
}
