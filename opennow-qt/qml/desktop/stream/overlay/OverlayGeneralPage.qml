import QtQuick
import QtQuick.Window
import OpenNOW

// About Cloudlight: version, project links and the GeForce NOW membership line.
OverlayPage {
    id: page
    property var menu
    pageName: "general"
    title: qsTr("General")

    readonly property var subscription: ShellStore.subscription || ({})
    readonly property string tier: String(subscription.membershipTier || "")
    readonly property string membershipText: tier === "" ? ""
        : qsTr("GeForce NOW %1 membership").arg(tier.charAt(0).toUpperCase() + tier.slice(1).toLowerCase())
    readonly property string membershipDetail: {
        const parts = []
        const end = Date.parse(String(subscription.currentSpanEndDateTime || ""))
        if (!isNaN(end))
            parts.push(qsTr("Current period ends %1").arg(new Date(end).toLocaleDateString(Qt.locale(), "MMMM d, yyyy")))
        if (subscription.isUnlimited === true)
            parts.push(qsTr("Unlimited hours"))
        else if (subscription.remainingHours !== undefined && subscription.remainingHours !== null)
            parts.push(qsTr("%1 h left").arg(Math.max(0, Number(subscription.remainingHours)).toFixed(1)))
        return parts.join(" · ")
    }

    Item {
        width: parent.width
        height: OverlayStyle.u(140)
        Image {
            id: appIcon
            // The icon artwork has a transparent margin; this shows the tile itself at 80 px.
            x: OverlayStyle.u(15)
            y: OverlayStyle.u(36)
            width: OverlayStyle.u(91)
            height: width
            source: "qrc:/icons/opennow-256.png"
            sourceSize: Qt.size(Math.ceil(width * Screen.devicePixelRatio), Math.ceil(height * Screen.devicePixelRatio))
            fillMode: Image.PreserveAspectFit
            smooth: true
        }
        Column {
            x: OverlayStyle.u(123)
            width: parent.width - x - OverlayStyle.gutter
            anchors.verticalCenter: appIcon.verticalCenter
            spacing: OverlayStyle.u(4)
            Text {
                width: parent.width
                text: "Cloudlight"
                color: OverlayStyle.text
                font.family: Theme.bodyFont
                font.pixelSize: OverlayStyle.bodySize
                font.weight: OverlayStyle.bodyWeight
                elide: Text.ElideRight
            }
            Text {
                width: parent.width
                text: qsTr("Version %1").arg(String(ShellStore.updaterState.currentVersion || Qt.application.version))
                color: OverlayStyle.subtitle
                font.family: Theme.bodyFont
                font.pixelSize: OverlayStyle.subtitleSize
                elide: Text.ElideRight
            }
            Text {
                width: parent.width
                text: qsTr("Independent client. Not affiliated with NVIDIA.")
                color: OverlayStyle.subtitle
                font.family: Theme.bodyFont
                font.pixelSize: OverlayStyle.subtitleSize
                elide: Text.ElideRight
            }
        }
    }
    component Link: OverlayRow {
        required property string url
        trailing: "external"
        height: OverlayStyle.u(64)
        onActivated: AppController.openExternalUrl(url)
    }
    Link { title: qsTr("Release notes"); url: "https://github.com/miirys/OpenNOW/commits/main" }
    Link { title: qsTr("Cloudlight source code"); url: "https://github.com/miirys/OpenNOW" }
    Link { title: qsTr("Report a problem"); url: "https://github.com/miirys/OpenNOW/issues" }
    Link { title: qsTr("NVIDIA GeForce NOW Terms of Use"); url: "https://www.nvidia.com/en-us/geforce-now/terms-of-use/" }
    Link { title: qsTr("Privacy Policy"); url: "https://www.nvidia.com/en-us/about-nvidia/privacy-policy/" }
    OverlayDivider { visible: page.membershipText !== "" }
    OverlayRow {
        visible: page.membershipText !== ""
        title: page.membershipText
        subtitle: page.membershipDetail
        activeFocusOnTab: false
        showHighlight: false
    }
}
