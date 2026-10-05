pragma Singleton
import QtQuick

// Flat, opaque theme modelled on the GeForce NOW app: neutral charcoal surfaces,
// hairline dividers, one accent colour. Theme packs only choose the accent (and
// light/dark base); the pack ids are fixed because opennow-core validates them.
QtObject {
    readonly property var packs: [
        {id:"nocturne", name:"Graphite", author:"CLOUDLIGHT", category:"Dark", detail:"BLUE", bg:"#141414", lightBg:"#F2F2F2", mid:"#2A2A2A", accent:"#4C9EFF", lightAccent:"#1764C0"},
        {id:"aurora", name:"Graphite Green", author:"CLOUDLIGHT", category:"Dark", detail:"GREEN", bg:"#141414", lightBg:"#F2F2F2", mid:"#2A2A2A", accent:"#76B900", lightAccent:"#4A7A00"},
        {id:"kraft", name:"Graphite Amber", author:"CLOUDLIGHT", category:"Dark", detail:"AMBER", bg:"#141414", lightBg:"#F2F2F2", mid:"#2A2A2A", accent:"#E8A33D", lightAccent:"#8A5A0B"},
        {id:"phosphor", name:"High Contrast", author:"CLOUDLIGHT", category:"High contrast", detail:"WHITE", bg:"#000000", lightBg:"#FFFFFF", mid:"#1F1F1F", accent:"#FFFFFF", lightAccent:"#000000"},
        {id:"bone", name:"Light", author:"CLOUDLIGHT", category:"Light", detail:"WARM", bg:"#F2F2F2", darkBg:"#141414", mid:"#D6D6D6", accent:"#8A5A0B", darkAccent:"#E8A33D"},
        {id:"cobalt", name:"Light Blue", author:"CLOUDLIGHT", category:"Light", detail:"BLUE", bg:"#F2F2F2", darkBg:"#141414", mid:"#D6D6D6", accent:"#1764C0", darkAccent:"#4C9EFF"},
        {id:"hibiscus", name:"Graphite Rose", author:"CLOUDLIGHT", category:"Dark", detail:"ROSE", bg:"#141414", lightBg:"#F2F2F2", mid:"#2A2A2A", accent:"#F0607F", lightAccent:"#A8243F"},
        {id:"chapel", name:"Graphite Violet", author:"CLOUDLIGHT", category:"Dark", detail:"VIOLET", bg:"#141414", lightBg:"#F2F2F2", mid:"#2A2A2A", accent:"#9B7BFF", lightAccent:"#5B3FC0"},
        // Cloudlight's house theme: a black dress and white hair. Neutrals carry a faint
        // lilac cast in both modes, and the accent is pearl on dark, ink on light.
        {id:"cloudlight", name:"Cloudlight", author:"CLOUDLIGHT", category:"Dark", detail:"PEARL", bg:"#100E13", lightBg:"#F4F2F7", mid:"#2A2530", accent:"#ECE6F5", lightAccent:"#1D1823",
         dark:{surface:"#18151C", raised:"#211D26", hover:"#2A252F", strong:"#352F3C", seam:"#2C2732", label:"#F4F1F8", muted:"#A39CAD"},
         light:{surface:"#FFFFFF", raised:"#EBE7F0", hover:"#E2DDE9", strong:"#D4CEDD", seam:"#D9D3E1", label:"#17141B", muted:"#5E5768"}}
    ]
    readonly property string mode: String(ShellStore.settings.appTheme || "auto")
    readonly property string themePack: ShellStore.previewThemePack !== ""
                                        ? ShellStore.previewThemePack
                                        : String(ShellStore.settings.themePack || "cloudlight")
    readonly property var pack: packs.find(item => item.id === themePack) || packs[0]
    readonly property bool packLight: pack.category === "Light"
    readonly property bool systemLight: Qt.styleHints.colorScheme === Qt.Light
    readonly property bool lightMode: ShellStore.previewThemePack !== "" ? packLight
        : mode === "light" || (mode === "auto" && systemLight)
    // Kept for settings compatibility; the UI is always opaque.
    readonly property bool translucent: false
    readonly property string accent: String(ShellStore.settings.appAccentColor || "blue")

    // Surfaces, darkest to lightest. Everything is opaque.
    readonly property color shell: lightMode ? (pack.lightBg || pack.bg) : (pack.darkBg || pack.bg)
    readonly property var tone: (lightMode ? pack.light : pack.dark) || ({})
    readonly property color surface: tone.surface || (lightMode ? "#FFFFFF" : "#1C1C1C")
    readonly property color surfaceRaised: tone.raised || (lightMode ? "#E9E9E9" : "#252525")
    readonly property color surfaceHover: tone.hover || (lightMode ? "#E0E0E0" : "#2E2E2E")
    readonly property color surfaceStrong: tone.strong || (lightMode ? "#D4D4D4" : "#383838")

    readonly property color face: lightMode ? "#141414" : "#FFFFFF"
    readonly property color faceText: lightMode ? "#FFFFFF" : "#141414"
    // Artwork scrims and dark store-colour fallbacks always need a light foreground,
    // independently of the shell's light/dark mode.
    readonly property color mediaForeground: "#FFFFFF"
    readonly property color mediaMuted: "#B3B3B3"
    readonly property color mediaAccent: accentOverridden ? accentColor(accent, false) : (pack.darkAccent || pack.accent)
    readonly property color seam: tone.seam || (lightMode ? "#D0D0D0" : "#333333")
    readonly property color label: tone.label || (lightMode ? "#141414" : "#F2F2F2")
    readonly property color textMuted: tone.muted || (lightMode ? "#5C5C5C" : "#A6A6A6")
    readonly property var accentChoices: ["green", "blue", "violet", "rose", "coral", "amber", "white"]
    function accentColor(value, forLightMode = lightMode) {
        const dark = {green:"#76B900", blue:"#4C9EFF", violet:"#9B7BFF", rose:"#F0607F", coral:"#FF7A59", amber:"#E8A33D", white:"#F2F2F2"}
        const light = {green:"#4A7A00", blue:"#1764C0", violet:"#5B3FC0", rose:"#A8243F", coral:"#B23E1E", amber:"#8A5A0B", white:"#2B2B2B"}
        return (forLightMode ? light : dark)[value] || (forLightMode ? light.blue : dark.blue)
    }
    readonly property color customAccent: accentColor(accent)
    readonly property color packAccent: lightMode ? (pack.lightAccent || pack.accent) : (pack.darkAccent || pack.accent)
    readonly property bool accentOverridden: ShellStore.previewThemePack === "" && ShellStore.settings.themeAccentOverride === true
    readonly property color focus: accentOverridden ? customAccent : packAccent
    readonly property color focusText: contrastText(focus)
    // Status colours: success, info, warning, error.
    readonly property color mint: lightMode ? "#3D7A00" : "#76B900"
    readonly property color violet: lightMode ? "#5B3FC0" : "#9B7BFF"
    readonly property color yellow: lightMode ? "#8A5A0B" : "#E8A33D"
    readonly property color coral: lightMode ? "#B3261E" : "#F2665B"
    // Former translucent "glass" fills, now solid panel colours.
    readonly property color glass: surface
    readonly property color glassStrong: surfaceRaised
    readonly property color cartSteam: "#1B2838"
    readonly property color cartEpic: "#202020"
    readonly property color cartUbisoft: "#2F4BB8"
    readonly property color cartXbox: "#107C10"
    readonly property color cartGog: "#6B35C9"
    readonly property color cartBattlenet: "#1F6FAE"

    function contrastText(background) {
        const linear = value => value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4)
        const luminance = linear(background.r) * 0.2126 + linear(background.g) * 0.7152 + linear(background.b) * 0.0722
        return (luminance + 0.05) / 0.0592 > 1.05 / (luminance + 0.05) ? "#141414" : "#FFFFFF"
    }

    // Inter is the closest open match to SF Pro: "Inter Display" for titles
    // (tighter spacing, like SF Pro Display) and "Inter" for everything else.
    // Only Regular, Medium, SemiBold and Bold are bundled; there is no thin text.
    readonly property string displayFont: "Inter Display"
    readonly property string bodyFont: "Inter"
    readonly property string monoFont: "IBM Plex Mono"
    // Brand face: the Cloudlight wordmark and a few editorial headlines only.
    readonly property string brandFont: "Cormorant Garamond"

    // Corner radii: soft, continuous-looking corners in the Apple range.
    readonly property int radiusSmall: 6
    readonly property int radius: 8
    readonly property int radiusLarge: 12

    readonly property int focusDuration: AppController.reducedMotion ? 0 : 100
    readonly property int enterDuration: AppController.reducedMotion ? 0 : 160
    readonly property int heroDuration: AppController.reducedMotion ? 0 : 160
    readonly property int overlayDuration: AppController.reducedMotion ? 0 : 140
    readonly property int panelDuration: AppController.reducedMotion ? 0 : 160
    readonly property var easeOut: [0.16, 1.0, 0.3, 1.0]
    // Spring-like curves (Apple-style): a fast start that settles with a
    // ~1.5% overshoot, and a critically damped one for things that must not
    // overshoot (fades, large surfaces). Both are 4-value BezierSpline paths.
    readonly property var spring: [0.2, 1.08, 0.32, 1.0, 1, 1]
    readonly property var springSoft: [0.32, 0.72, 0.0, 1.0, 1, 1]
    readonly property int springDuration: AppController.reducedMotion ? 0 : 460
    readonly property var easeEmphasized: [0.2, 0.9, 0.1, 1.0]

    function unit(windowWidth, windowHeight) {
        return Math.min(windowWidth / 100, windowHeight / 56.25)
    }
}
