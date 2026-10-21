package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.runtime.Composable
import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun LocalPerformance(stats: JSONObject) {
    if (stats.has("unavailable")) { Text(stringResource(Res.string.ui_local_stats_unavailable, stats.optString("unavailable"))); return }
    if (!stats.has("queueDepth")) { Text(stringResource(Res.string.ui_local_stats_reading)); return }
    fun number(key: String, scale: Double = 1.0) = if (stats.has(key)) String.format(Locale.ROOT, "%.2f", stats.optDouble(key) / scale) else "—"
    val ime = stringResource(when (stats.optInt("imePolicyReported", -1)) {
        0 -> Res.string.ui_ime_local; 1 -> Res.string.ui_ime_fallback; 2 -> Res.string.ui_ime_hidden; else -> Res.string.ui_unknown
    })
    Text(stringResource(Res.string.ui_local_stats_ime, ime, stats.optInt("displayId", -1)))
    Text(stringResource(Res.string.ui_local_stats_refresh, number("physicalRefreshRate"), number("effectiveRefreshRate"), number("schedulerRefreshRate"),
        stats.optBoolean("surfaceRateHintApplied").toString(), stats.optBoolean("followPhysicalRequestApplied").toString()))
    Text(stringResource(Res.string.ui_local_stats_queue, stats.optInt("queueDepth"), stats.optInt("queueCapacity"), stats.optLong("queueMerged").toString(),
        number("queueAverageMs"), number("queueMaxMs"), number("binderAverageMs"), number("binderMaxMs")))
    Text(stringResource(Res.string.ui_local_stats_injection, number("inputSampleAgeNanos", 1e6), number("inputMaxSampleAgeNanos", 1e6),
        number("injectDurationNanos", 1e6), number("injectMaxDurationNanos", 1e6), stats.optLong("inputEvents").toString(), stats.optLong("injectFailures").toString(),
        stats.optLong("binderFailures").toString(), stats.optLong("rejectedTouchFrames").toString(), stats.optInt("activePointers")))
    Text(stringResource(Res.string.ui_local_stats_scroll, stats.optLong("scrollTicks").toString(), stats.optLong("scrollSkippedTicks").toString(),
        number("scrollMaxLatenessNanos", 1e6), stats.optLong("inputResets").toString()))
    Text(stringResource(Res.string.ui_local_stats_definitions))
}
