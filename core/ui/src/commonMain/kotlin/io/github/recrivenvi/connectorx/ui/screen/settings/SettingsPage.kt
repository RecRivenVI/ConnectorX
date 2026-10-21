package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsPage(bottomPadding: Dp, onTheme: () -> Unit, media: MediaSettingsModel?, onMedia: (() -> Unit)?,
    diagnosticsEnabled: Boolean, onDiagnosticsChange: (Boolean) -> Unit, versionName: String,
    predictive: Boolean, onPredictive: ((Boolean) -> Unit)?, swipe: Boolean, onSwipe: (Boolean) -> Unit) {
    TaskPage(stringResource(Res.string.ui_settings), bottomPadding = bottomPadding) {
        item { SmallTitle(stringResource(Res.string.ui_personalization)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ArrowPreference(title = stringResource(Res.string.settings_theme_title), summary = stringResource(Res.string.settings_theme_summary), onClick = onTheme)
        } }
        if (media != null && onMedia != null) {
            item { SmallTitle(stringResource(Res.string.ui_remote_control)) }
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val values = media.values
                val resolution = when (values["videoMaxSize"]) {
                    "-1" -> stringResource(Res.string.ui_auto); "0" -> stringResource(Res.string.ui_native); else -> values["videoMaxSize"].orEmpty() + " px"
                }
                ArrowPreference(title = stringResource(Res.string.ui_media_title), summary = stringResource(Res.string.ui_media_summary, resolution,
                    values["maxFps"].orEmpty().removeSuffix(".0"), displayRate(values["videoBitRate"].orEmpty(), 6),
                    stringResource(if (values["audio"] == "true") Res.string.ui_sound_on else Res.string.ui_sound_off)), onClick = onMedia)
            } }
        }
        item { SmallTitle(stringResource(Res.string.ui_gestures)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            onPredictive?.let { SwitchPreference(title = stringResource(Res.string.settings_predictive_back), summary = stringResource(Res.string.settings_predictive_back_summary), checked = predictive, onCheckedChange = it) }
            SwitchPreference(title = stringResource(Res.string.settings_swipe_dismiss), summary = stringResource(Res.string.settings_swipe_dismiss_summary), checked = swipe, onCheckedChange = onSwipe)
        } }
        item { SmallTitle(stringResource(Res.string.ui_advanced)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SwitchPreference(title = stringResource(Res.string.ui_diagnostics), summary = stringResource(Res.string.ui_diagnostics_summary), checked = diagnosticsEnabled, onCheckedChange = onDiagnosticsChange)
        } }
        item { SmallTitle(stringResource(Res.string.ui_about)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { BasicComponent(title = stringResource(Res.string.ui_version), summary = versionName) } }
    }
}
