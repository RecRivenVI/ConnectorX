package io.github.recrivenvi.connectorx.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.StateCard
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import io.github.recrivenvi.connectorx.ui.component.PreferenceNote
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
private fun ScrollThumbnail() {
    Column(
        Modifier.padding(16.dp).width(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(MiuixIcons.More, null, Modifier.size(16.dp).graphicsLayer { rotationZ = 90f })
        Column(
            Modifier.background(
                MiuixTheme.colorScheme.sliderBackground,
                RoundedCornerShape(SliderDefaults.MinHeight / 2)
            ).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                MiuixIcons.ChevronBackward,
                null,
                Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
            Icon(MiuixIcons.Pause, null, Modifier.size(20.dp))
            Icon(
                MiuixIcons.ChevronForward,
                null,
                Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
        }
        Icon(MiuixIcons.Close, null, Modifier.size(16.dp))
    }
}

@Composable
fun AutomationPage(model: AutomationModel, bottom: Dp) {
    var picking by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    TaskPage(stringResource(Res.string.ui_automation), bottomPadding = bottom) {
        model.global?.let { global ->
            item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScrollThumbnail()
                        BasicComponent(
                            modifier = Modifier.weight(1f),
                            title = stringResource(Res.string.ui_global_scroll),
                            summary = stringResource(
                                when {
                                    global.failed -> Res.string.ui_global_failed
                                    global.visible && !global.ready -> Res.string.ui_permission_connecting
                                    global.visible && global.direction > 0 -> Res.string.ui_scroll_up
                                    global.visible && global.direction < 0 -> Res.string.ui_scroll_down
                                    global.visible -> Res.string.ui_scroll_stopped
                                    !global.permissionGranted -> Res.string.ui_overlay_permission_reason
                                    !model.permissionAvailable -> Res.string.ui_local_needs_privilege_reason
                                    else -> Res.string.ui_global_closed
                                }
                            )
                        )
                    }
                    if (global.visible) Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            stringResource(Res.string.ui_stop_scroll),
                            global.stop,
                            Modifier.weight(1f),
                            enabled = global.direction != 0
                        )
                        TextButton(
                            stringResource(Res.string.ui_close_global),
                            global.close,
                            Modifier.weight(1f)
                        )
                    } else TextButton(
                        stringResource(
                            when {
                                !global.permissionGranted -> Res.string.ui_grant_overlay
                                !model.permissionAvailable -> Res.string.ui_open_shizuku
                                else -> Res.string.ui_open_global
                            }
                        ),
                        if (!model.permissionAvailable && global.permissionGranted) model.openPrivilege else global.open,
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
            item { PreferenceNote(stringResource(Res.string.ui_global_scroll_note)) }
        }
        item { SmallTitle(stringResource(Res.string.ui_window_scroll)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = stringResource(Res.string.ui_target_app),
                    summary = model.targetName ?: stringResource(Res.string.ui_choose_app),
                    onClick = { picking = true; if (model.targets.isEmpty()) model.refresh() })
                if (!model.permissionAvailable) BasicComponent(
                    title = stringResource(Res.string.ui_local_needs_privilege),
                    summary = stringResource(Res.string.ui_local_needs_privilege_reason)
                )
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        stringResource(Res.string.ui_start_up),
                        { model.start(1) },
                        Modifier.weight(1f),
                        enabled = model.enabled
                    )
                    TextButton(
                        stringResource(Res.string.ui_start_down),
                        { model.start(-1) },
                        Modifier.weight(1f),
                        enabled = model.enabled
                    )
                }
                if (!model.permissionAvailable) TextButton(
                    stringResource(Res.string.ui_open_shizuku),
                    model.openPrivilege,
                    Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
        item { PreferenceNote(stringResource(Res.string.ui_window_scroll_note)) }
        model.activeTitle?.let { title ->
            item { SmallTitle(stringResource(Res.string.ui_in_progress)) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = title, summary = stringResource(
                            when {
                                model.direction > 0 -> Res.string.ui_scroll_up; model.direction < 0 -> Res.string.ui_scroll_down; else -> Res.string.ui_scroll_stopped
                            }
                        ), onClick = model.resume
                    )
                    TextButton(
                        stringResource(Res.string.ui_stop_scroll),
                        model.stop,
                        Modifier.fillMaxWidth().padding(16.dp),
                        enabled = model.direction != 0
                    )
                }
            }
        }
    }
    WindowDialog(
        show = picking,
        title = stringResource(Res.string.ui_choose_local_app),
        onDismissRequest = { picking = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                query,
                { query = it },
                label = stringResource(Res.string.ui_search_apps),
                modifier = Modifier.fillMaxWidth()
            )
            if (model.loading) InfiniteProgressIndicator()
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(model.targets.filter {
                    it.name.contains(
                        query,
                        true
                    ) || it.packageName.contains(query, true)
                }, key = { it.packageName }) { app ->
                    ArrowPreference(title = app.name, onClick = { app.select(); picking = false })
                }
                if (model.targets.isEmpty() && !model.loading) item {
                    BasicComponent(
                        title = stringResource(if (model.readFailed) Res.string.ui_apps_failed else Res.string.ui_apps_empty),
                        summary = stringResource(if (model.readFailed) Res.string.ui_apps_failed_local else Res.string.ui_apps_empty_reason)
                    )
                }
            }
            TextButton(
                stringResource(Res.string.ui_refresh_local),
                model.refresh,
                Modifier.fillMaxWidth(),
                enabled = !model.loading
            )
        }
    }
}
