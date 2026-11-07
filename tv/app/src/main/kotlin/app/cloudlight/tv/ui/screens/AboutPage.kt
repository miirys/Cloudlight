package app.cloudlight.tv.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.view.Display
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cloudlight.tv.BuildConfig
import app.cloudlight.tv.CrashLog
import app.cloudlight.tv.R
import app.cloudlight.tv.data.AppState
import app.cloudlight.tv.stream.TvDecoderFactory
import app.cloudlight.tv.ui.motion.reveal
import app.cloudlight.tv.ui.theme.LocalType
import app.cloudlight.tv.ui.theme.Palette
import java.util.Locale

/** One fact in the spec sheet; [detail] is an optional second line in small type. */
private data class Spec(val label: String, val value: String, val detail: String? = null)

/**
 * The about page is a spec sheet rather than a settings list: who made the app, and the
 * technical facts about this TV and this account that matter when streaming.
 */
@Composable
fun AboutPage(app: AppState) {
    val type = LocalType.current
    val context = LocalContext.current
    val display = LocalView.current.display
    val device = remember { deviceSpecs(context) }
    val screen = remember(display) { displaySpecs(display) }
    val video = remember { decoderSpecs() }
    val network = remember { networkSpecs(context) }
    val problem = remember { runCatching { CrashLog.latest(context) }.getOrNull() }
    val account = listOfNotNull(
        app.profile?.let { Spec("Account", it.name, listOfNotNull(app.membership?.tier ?: it.tier, it.email).joinToString("  ·  ").ifEmpty { null }) },
        app.allowedModes?.maxWithOrNull(compareBy({ it.height }, { it.fps }))?.let {
            Spec("Best stream", "${if (it.height >= 2160) "4K" else "${it.height}p"} at ${it.fps} fps", "${it.width} × ${it.height}")
        },
        app.region?.let { Spec("Region", it) },
        Spec("Session service", app.streamingBase.removePrefix("https://").trimEnd('/')),
        Spec("Library", "${app.library.size} games"),
    )

    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        Row(Modifier.reveal(1), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.icon), null, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(16.dp))
            Column {
                BasicText("Cloudlight", style = type.display.copy(fontSize = type.title.fontSize * 1.25f))
                BasicText(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE} build",
                    style = type.body,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).reveal(2), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SpecGroup("This device", device)
                SpecGroup("Display", screen)
            }
            Column(Modifier.weight(1f).reveal(3), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SpecGroup("Video decoding", video)
                SpecGroup("Network", network)
            }
            Column(Modifier.weight(1f).reveal(4), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SpecGroup("GeForce NOW", account)
                if (problem != null) {
                    val where = problem.detail.lineSequence().map { it.trim() }
                        .firstOrNull { it.startsWith("at ") && "cloudlight" in it }?.removePrefix("at ")
                    SpecGroup(
                        "Last unexpected close",
                        listOfNotNull(
                            Spec("When", CrashLog.format(problem.whenMs)),
                            Spec("What", problem.summary.ifBlank { "Unknown" }, where),
                        ),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GroupTitle("Credits")
                    BasicText("Not affiliated with NVIDIA. GeForce NOW is a trademark of NVIDIA Corporation.", style = type.caption)
                    BasicText("Inter by Rasmus Andersson, SIL Open Font License 1.1.", style = type.caption)
                    BasicText("WebRTC by the WebRTC project authors, BSD licence.", style = type.caption)
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Column {
        BasicText(text.uppercase(), style = LocalType.current.overline)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(Palette.hairlineStrong))
    }
}

@Composable
private fun SpecGroup(title: String, specs: List<Spec>) {
    val type = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        GroupTitle(title)
        for (s in specs) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    BasicText(s.label, Modifier.width(92.dp), style = type.caption.copy(color = Palette.textTertiary))
                    BasicText(s.value, Modifier.weight(1f), style = type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (s.detail != null) {
                    BasicText(s.detail, Modifier.padding(start = 92.dp), style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- Facts ----------

private fun deviceSpecs(context: Context): List<Spec> {
    val mem = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
    val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ") else ""
    return listOfNotNull(
        Spec("Model", "$maker ${Build.MODEL}"),
        Spec("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        soc.takeIf { it.isNotBlank() }?.let { Spec("Chipset", it) } ?: Spec("Board", Build.HARDWARE),
        Spec("Memory", "%.1f GB".format(mem.totalMem / 1_073_741_824.0)),
        Spec("CPU", "${Runtime.getRuntime().availableProcessors()} cores, ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"),
    )
}

private fun displaySpecs(display: Display?): List<Spec> {
    if (display == null) return listOf(Spec("Output", "Unknown"))
    val mode = display.mode
    val rates = display.supportedModes
        .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
        .map { Math.round(it.refreshRate) }
        .distinct()
        .sorted()
    val best = display.supportedModes.maxWithOrNull(compareBy({ it.physicalHeight }, { it.refreshRate }))
    @Suppress("DEPRECATION")
    val types: IntArray = display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
    val hdr = types.toList().mapNotNull {
        when (it) {
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
            else -> null
        }
    }
    return listOfNotNull(
        Spec("Output", "${mode.physicalWidth} × ${mode.physicalHeight} at ${Math.round(mode.refreshRate)} Hz"),
        Spec("Refresh rates", rates.joinToString(", ") { "$it Hz" }.ifEmpty { "Unknown" }),
        best?.takeIf { it.physicalHeight > mode.physicalHeight }?.let { Spec("Highest mode", "${it.physicalWidth} × ${it.physicalHeight} at ${Math.round(it.refreshRate)} Hz") },
        Spec("HDR", hdr.joinToString(", ").ifEmpty { "Not supported" }),
    )
}

private fun decoderSpecs(): List<Spec> = listOf(
    "HEVC" to TvDecoderFactory.MIME_HEVC,
    "AV1" to TvDecoderFactory.MIME_AV1,
    "H.264" to TvDecoderFactory.MIME_AVC,
).map { (name, mime) ->
    val info = TvDecoderFactory.find(mime) ?: return@map Spec(name, "No hardware decoder")
    val summary = runCatching { capability(info, mime) }.getOrNull() ?: "Hardware"
    Spec(name, summary, info.name)
}

private fun capability(info: MediaCodecInfo, mime: String): String {
    val caps = info.getCapabilitiesForType(mime)
    val v = caps.videoCapabilities
    val w = v.supportedWidths.upper
    val h = v.supportedHeights.upper
    val (tw, th) = when {
        v.isSizeSupported(3840, 2160) -> 3840 to 2160
        v.isSizeSupported(2560, 1440) -> 2560 to 1440
        v.isSizeSupported(1920, 1080) -> 1920 to 1080
        else -> w to h
    }
    val fps = runCatching { v.getSupportedFrameRatesFor(tw, th).upper.toInt() }.getOrNull()
    val res = when (th) {
        2160 -> "4K"
        else -> "${th}p"
    }
    val tenBit = caps.profileLevels.any {
        (mime == TvDecoderFactory.MIME_HEVC && it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) ||
            (mime == TvDecoderFactory.MIME_AV1 && it.profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10)
    }
    return listOfNotNull(
        if (fps != null) "$res up to ${fps.coerceAtMost(240)} fps" else res,
        if (tenBit) "10-bit" else null,
    ).joinToString(", ")
}

private fun networkSpecs(context: Context): List<Spec> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return listOf(Spec("Connection", "Offline"))
    val kind = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Connected"
    }
    val out = mutableListOf(Spec("Connection", kind))
    if (kind == "Wi-Fi") {
        @Suppress("DEPRECATION")
        val wifi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        if (wifi != null && wifi.linkSpeed > 0) {
            val band = if (wifi.frequency >= 5900) "6 GHz" else if (wifi.frequency >= 4900) "5 GHz" else "2.4 GHz"
            out += Spec("Link", "${wifi.linkSpeed} Mbps, $band")
        }
    }
    val down = caps.linkDownstreamBandwidthKbps
    if (down > 0) out += Spec("Estimated", "${down / 1000} Mbps down")
    return out
}
