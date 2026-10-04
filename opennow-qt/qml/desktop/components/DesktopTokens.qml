pragma Singleton
import QtQuick
import OpenNOW

QtObject {
    id: tokens

    function relativeLastPlayed(raw, nowMs) {
        const timestamp = Date.parse(String(raw || ""))
        if (isNaN(timestamp))
            return ""
        const seconds = Math.max(0, Math.floor((nowMs - timestamp) / 1000))
        if (seconds < 1)
            return qsTr("Just now")
        if (seconds < 60)
            return seconds === 1 ? qsTr("1 second ago") : qsTr("%1 seconds ago").arg(seconds)
        const minutes = Math.floor(seconds / 60)
        if (minutes < 60)
            return minutes === 1 ? qsTr("1 minute ago") : qsTr("%1 minutes ago").arg(minutes)
        const hours = Math.floor(minutes / 60)
        if (hours < 24)
            return hours === 1 ? qsTr("1 hour ago") : qsTr("%1 hours ago").arg(hours)
        const days = Math.floor(hours / 24)
        return days === 1 ? qsTr("1 day ago") : qsTr("%1 days ago").arg(days)
    }

    readonly property color shell: Theme.shell
    // All chrome is opaque: the rail, top bar and status bar sit on solid surfaces.
    readonly property color rail: Theme.surface
    readonly property color topBar: Theme.shell
    readonly property color statusBar: Theme.surface
    readonly property color surface: Theme.surface
    readonly property color raised: Theme.surfaceRaised
    readonly property color raisedStrong: Theme.surfaceStrong
    readonly property color hover: Theme.surfaceHover
    readonly property color seam: Theme.seam
    readonly property color seamSoft: Qt.tint(Theme.seam, Theme.lightMode ? "#40FFFFFF" : "#40000000")
    readonly property color text: Theme.label
    readonly property color textHigh: Theme.label
    readonly property color textBody: Qt.tint(Theme.label, Theme.lightMode ? "#38FFFFFF" : "#30000000")
    readonly property color textMuted: Theme.textMuted
    readonly property color textFaint: Qt.tint(Theme.textMuted, Theme.lightMode ? "#50FFFFFF" : "#50000000")
    readonly property color focus: Theme.focus
    readonly property color green: Theme.mint
    readonly property color mint: Theme.mint
    readonly property color amber: Theme.yellow
    readonly property color ledAmber: Theme.yellow
    readonly property color danger: Theme.coral
    readonly property int radius: px(Theme.radius)
    readonly property int radiusLarge: px(Theme.radiusLarge)
    readonly property string displayFont: Theme.displayFont
    readonly property string bodyFont: Theme.bodyFont
    readonly property string monoFont: Theme.monoFont
    property real uiScale: 1
    // Type ramp. Every desktop font size must come from here so text stays
    // proportionate on any display size; raw pixelSize literals drift.
    readonly property int titleSize: px(30)
    readonly property int headingSize: px(20)
    readonly property int bodySize: px(17)
    readonly property int captionSize: px(15)
    readonly property int monoSize: px(14)
    readonly property int smallSize: px(13)
    readonly property int microSize: px(12)
    readonly property int tinySize: px(11)
    readonly property int railWidth: px(232)
    readonly property int railCollapsedWidth: px(72)
    readonly property int topBarHeight: px(72)
    readonly property int navSize: px(18)
    readonly property int displaySize: px(44)
    // Horizontal safe margin: 96px at 1080p, the 10-foot overscan guide.
    readonly property int safeX: px(72)
    readonly property int focusOutline: Math.max(3, px(3))
    readonly property int statusBarHeight: px(52)
    readonly property int rowHeight: px(60)
    readonly property int controlHeight: px(38)
    readonly property int settingsInset: px(20)
    readonly property int settingsLabelInset: px(76)
    readonly property int settingsControlWidth: px(300)
    readonly property int settingsCompactWidth: px(760)
    readonly property int posterWidth: px(112)
    readonly property int posterHeight: px(168)
    readonly property int libraryCellWidth: px(146)
    readonly property int libraryCellHeight: px(214)
    readonly property int libraryArtWidth: px(132)
    readonly property int libraryArtHeight: px(198)
    property FontMetrics storeTitleMetrics: FontMetrics {
        font.family: Theme.bodyFont
        font.pixelSize: tokens.bodySize
        font.weight: Font.DemiBold
    }
    readonly property int storeCardInfoHeight: px(12) + Math.ceil(storeTitleMetrics.height) * 2 + px(4) + px(24) + px(8)
    readonly property int quickDuration: AppController.reducedMotion ? 0 : 150
    readonly property int motionDuration: AppController.reducedMotion ? 0 : 250
    readonly property int revealDuration: AppController.reducedMotion ? 0 : 320
    readonly property real cardHoverScale: 1.06
    readonly property int cardOutlinePad: 2
    readonly property color cardOutlineIdle: Theme.seam

    function px(value) {
        return Math.max(1, Math.round(Number(value) * uiScale))
    }

    function scaleForWindow(width, height) {
        // Laid out against a 1440x810 canvas, so a 1080p screen (a TV or
        // projector seen from a couch) gets ~1.33x and a 4K screen at 100%
        // gets ~2.67x, the same layout drawn at native resolution. Smaller
        // windows reflow instead of shrinking below 0.95.
        return Math.max(0.95, Math.min(3, Math.min(width / 1440, height / 810)))
    }

    function storeKey(value) {
        const key = String(value || "").toLowerCase()
        if (key.indexOf("steam") >= 0) return "steam"
        if (key.indexOf("epic") >= 0) return "epic"
        if (key.indexOf("ubisoft") >= 0 || key.indexOf("uplay") >= 0) return "ubisoft"
        if (key.indexOf("battle") >= 0) return "battlenet"
        if (key.indexOf("xbox") >= 0) return "xbox"
        if (key.indexOf("gog") >= 0) return "gog"
        if (key.indexOf("gaijin") >= 0) return "gaijin"
        if (key === "nvidia") return "nvidia"
        if (key === "ea" || key === "ea_app" || key === "origin") return "ea"
        return ""
    }
    function storeIconUrl(value) {
        const key = storeKey(value)
        return key ? "qrc:/qt/qml/OpenNOW/res/icons/store-" + key + ".svg" : ""
    }
    function storeLabel(value) {
        const labels = {steam:"Steam", epic:"Epic Games", ubisoft:"Ubisoft Connect", battlenet:"Battle.net",
            xbox:"Xbox", gog:"GOG", gaijin:"Gaijin", ea:"EA app", nvidia:"NVIDIA"}
        return labels[storeKey(value)] || (String(value).toUpperCase() === "NONE" ? qsTr("Direct launch") : String(value))
    }
    function genreLabel(value) {
        return String(value).toLowerCase().replace(/_/g, " ").replace(/\b\w/g, letter => letter.toUpperCase())
    }

    function artworkUrl(game, preferHero) {
        if (!game)
            return ""
        const raw = preferHero
            ? String(game.heroImageUrl || game.imageUrl || game.screenshotUrl || game.boxArtUrl || "")
            : String(game.imageUrl || game.heroImageUrl || game.screenshotUrl || game.boxArtUrl || "")
        return decodeArtworkUrl(raw)
    }

    function decodeArtworkUrl(url) {
        return String(url || "").split(";f=webp").join(";f=jpg")
    }

    // The image CDN resizes on request (";w=<px>"). Ask for the next width step at or
    // above what the screen will show, so 1440p and 4K screens get sharp key art.
    function artworkForWidth(url, pixelWidth) {
        const source = String(url || "")
        const match = source.match(/;w=(\d+)/)
        if (!match || Number(pixelWidth) <= Number(match[1]))
            return source
        const steps = [1920, 2560, 3840]
        let wanted = steps[steps.length - 1]
        for (const step of steps) {
            if (step >= pixelWidth) {
                wanted = step
                break
            }
        }
        return wanted > Number(match[1]) ? source.replace(/;w=\d+/, ";w=" + wanted) : source
    }

    function consoleModeOn(win) {
        return win
            ? Boolean(win.forceConsole || win.desktopSurfaceActive === false)
            : ShellStore.settings.launchInConsoleMode === true
    }
    function consoleModeTargetOn(win) {
        return win
            ? !Boolean(win.targetDesktopSurface)
            : ShellStore.settings.launchInConsoleMode === true
    }
    function consoleModePending(win) {
        return ShellStore.consoleSurfaceRequestId !== "" || Boolean(win
            && win.targetDesktopSurface !== undefined
            && win.targetDesktopSurface !== win.desktopSurfaceActive)
    }
}
