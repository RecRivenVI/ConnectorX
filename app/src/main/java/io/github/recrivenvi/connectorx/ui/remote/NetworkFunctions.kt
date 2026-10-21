package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.session.RemoteSession
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.mirror.GeometryPreview
import io.github.recrivenvi.connectorx.ui.screen.settings.LocalSessionDiagnostics
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun NetworkFunctions(session: RemoteSession, show: Boolean, onDismiss: () -> Unit,
    layout: AppLayoutPreferences, onLayout: (AppLayoutPreferences) -> Unit,
    geometryOverlay: Boolean, onGeometryOverlay: (Boolean) -> Unit, restart: () -> Unit) {
    val diagnostics = LocalSessionDiagnostics.current
    var sending by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    WindowBottomSheet(show = show, title = stringResource(Res.string.ui_functions), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowPreference(title = stringResource(Res.string.ui_send_text), onClick = { sending = !sending })
            if (sending) {
                Text(stringResource(Res.string.ui_send_text_note))
                TextField(text, { text = it }, label = stringResource(Res.string.ui_text), modifier = Modifier.fillMaxWidth())
                TextButton(stringResource(Res.string.ui_send), { session.text(text); text = "" }, Modifier.fillMaxWidth(), enabled = session.running && text.isNotEmpty())
            }
            session.videoError?.let {
                BasicComponent(title = stringResource(Res.string.ui_video_failed), summary = stringResource(Res.string.ui_video_failed_reason))
                TextButton(stringResource(Res.string.ui_retry_video), session::retryVideo, Modifier.fillMaxWidth(), enabled = session.running)
            }
            if (session.app != null) {
                SmallTitle(stringResource(Res.string.ui_picture))
                SwitchPreference(title = stringResource(Res.string.ui_under_status), checked = layout.underStatus, onCheckedChange = { onLayout(layout.copy(underStatus = it)) })
                SwitchPreference(title = stringResource(Res.string.ui_under_navigation), checked = layout.underNavigation, onCheckedChange = { onLayout(layout.copy(underNavigation = it)) })
                BasicComponent(title = stringResource(Res.string.ui_layout_next_open))
                SwitchPreference(title = stringResource(Res.string.ui_remote_system_bars), summary = stringResource(Res.string.ui_next_session),
                    checked = layout.systemDecorations, onCheckedChange = { onLayout(layout.copy(systemDecorations = it)) })
                TextButton(stringResource(if (session.restarting) Res.string.ui_restarting else Res.string.ui_restart_app), restart,
                    Modifier.fillMaxWidth(), enabled = session.running && !session.restarting,
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error))
                Text(stringResource(Res.string.ui_restart_warning, session.appTitle ?: stringResource(Res.string.ui_remote_app)))
                session.restartMessage?.let { Text(it) }
            }
            if (diagnostics) {
                SmallTitle(stringResource(Res.string.ui_diagnostics_group))
                StreamQualityDetails(session)
                session.videoError?.let { Text(it) }
                if (session.app != null) {
                    val info = session.info
                    Text(stringResource(Res.string.ui_display_diagnostics, info.optInt("displayId", -1), info.optInt("activityDisplayId", -1),
                        info.optInt("width"), info.optInt("height"), info.optInt("dpi"), info.optDouble("refreshRate", 0.0).toString(), info.optInt("rotation", -1)))
                    session.infoError?.let { Text(it) }
                    if (info.has("error")) Text(info.optString("error"))
                }
                if (session.deviceMirror) {
                    SwitchPreference(title = stringResource(Res.string.ui_geometry_overlay), checked = geometryOverlay, onCheckedChange = onGeometryOverlay)
                    session.geometry?.let { geometry ->
                        GeometryPreview(geometry)
                        Text(stringResource(Res.string.ui_geometry_legend))
                        Text(stringResource(Res.string.ui_geometry_details, geometry.naturalWidth, geometry.naturalHeight, geometry.rotation,
                            geometry.requestedRadii.joinToString(), geometry.effectiveRadii.joinToString(), geometry.cutoutBoundsLogical.joinToString(),
                            geometry.safeInsetsLogical.joinToString(), geometry.waterfallInsetsLogical.joinToString(), geometry.cutoutPathKind))
                        geometry.preciseCutoutCircle?.let { circle -> Text(stringResource(Res.string.ui_geometry_circle,
                            circle[0].toString(), circle[1].toString(), circle[2].toString(), geometry.preciseCutoutSource.orEmpty())) }
                            ?: Text(stringResource(Res.string.ui_geometry_no_circle))
                        Text(stringResource(Res.string.ui_geometry_limits))
                    }
                    session.geometryError?.let { Text(it) }
                }
            } else Text(stringResource(Res.string.ui_diagnostics_footer))
        }
    }
}
