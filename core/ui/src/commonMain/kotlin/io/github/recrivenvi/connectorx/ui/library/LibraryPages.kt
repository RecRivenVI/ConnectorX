package io.github.recrivenvi.connectorx.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.StateCard
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import io.github.recrivenvi.connectorx.ui.devices.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
private fun ApplicationLetter(app: LibraryApplication) {
    Box(Modifier.size(40.dp).background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(app.name.take(1), color = MiuixTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ApplicationRow(app: LibraryApplication, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicComponent(modifier = Modifier.weight(1f), title = app.name, enabled = app.enabled, onClick = app.launch,
            startAction = { Box(Modifier.padding(end = 12.dp)) { ApplicationLetter(app) } })
        IconButton(onMore, Modifier.padding(end = 8.dp)) { Icon(MiuixIcons.More, stringResource(Res.string.ui_app_options, app.name)) }
    }
}

@Composable
fun LibraryPage(model: LibraryModel, bottom: Dp, active: Boolean, onDetails: (String) -> Unit) {
    var query by rememberSaveable(model.selectedSource) { mutableStateOf("") }
    var chosenKey by remember { mutableStateOf<String?>(null) }
    val source = model.sources.firstOrNull { it.id == model.selectedSource }
    LaunchedEffect(active, model.selectedSource, model.enablePrivilege == null) {
        if (active && !model.loading && model.enablePrivilege == null) model.refresh()
    }
    TaskPage(stringResource(Res.string.ui_apps), bottomPadding = bottom, refreshing = model.loading,
        onRefresh = model.refresh) {
        if (model.sessions.isNotEmpty()) {
            item { SmallTitle(stringResource(Res.string.ui_continue)) }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(model.sessions) { session ->
                    Card(onClick = { session.invoke("resume") }, modifier = Modifier.width(248.dp)) {
                        BasicComponent(title = session.title(), summary = session.name + " · " + stringResource(sessionTypeLabel(session.sessionType)))
                    }
                }
            } }
        }
        item { TabRow(model.sources.map { (if (it.presence == DevicePresence.Saved) "○ " else "● ") +
            if (it.local) stringResource(Res.string.ui_local) else it.name },
            model.sources.indexOfFirst { it.id == model.selectedSource }.coerceAtLeast(0),
            { model.sources[it].select() }, Modifier.fillMaxWidth().padding(12.dp)) }
        when {
            model.enablePrivilege != null -> item {
                StateCard(stringResource(Res.string.ui_local_needs_privilege), stringResource(Res.string.ui_local_needs_privilege_reason)) {
                    TextButton(stringResource(Res.string.ui_open_shizuku), model.enablePrivilege, Modifier.fillMaxWidth())
                }
            }
            source?.presence == DevicePresence.Saved -> item {
                StateCard(stringResource(Res.string.ui_offline_title), stringResource(Res.string.ui_offline_reason)) {
                    TextButton(stringResource(Res.string.ui_retry_connection), model.refresh, Modifier.fillMaxWidth(), enabled = !model.loading)
                    TextButton(stringResource(Res.string.ui_device_details), { onDetails(source.id) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (model.readFailed) item {
            StateCard(stringResource(Res.string.ui_apps_failed), stringResource(if (source?.local == true) Res.string.ui_apps_failed_local else Res.string.ui_apps_failed_remote)) {
                TextButton(stringResource(Res.string.ui_retry), model.refresh, Modifier.fillMaxWidth(), enabled = !model.loading)
            }
        }
        if (model.loading) item { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfiniteProgressIndicator(); Text(stringResource(Res.string.ui_reading_apps))
        } }
        if (model.favorites.isNotEmpty()) {
            item { SmallTitle(stringResource(Res.string.ui_favorites)) }
            model.favorites.chunked(4).forEach { row -> item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { app -> Column(Modifier.weight(1f).clickable(enabled = app.enabled, onClick = app.launch).padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ApplicationLetter(app); Text(app.name, maxLines = 2)
                    } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            } }
        }
        item { SmallTitle(stringResource(Res.string.ui_all_apps)) }
        item { TextField(query, { query = it }, label = stringResource(Res.string.ui_search_apps), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) }
        val filtered = model.applications.filter { query.isBlank() || it.name.contains(query, true) || it.packageName.contains(query, true) }
        items(filtered, key = { it.key }) { app -> Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) { ApplicationRow(app) { chosenKey = app.key } } }
        if (model.loaded && filtered.isEmpty() && !model.loading) item {
            StateCard(stringResource(Res.string.ui_apps_empty), stringResource(if (query.isBlank()) Res.string.ui_apps_empty_reason else Res.string.ui_search_empty_reason)) {
                TextButton(stringResource(Res.string.ui_retry), { if (query.isNotBlank()) query = "" else model.refresh() }, Modifier.fillMaxWidth())
            }
        }
    }
    val chosen = (model.applications + model.favorites).firstOrNull { it.key == chosenKey }
    if (chosen != null) ApplicationSheet(chosen) { chosenKey = null }
}

@Composable
private fun ApplicationSheet(app: LibraryApplication, onDismiss: () -> Unit) {
    var status by remember(app.key) { mutableStateOf(app.underStatus) }
    var navigation by remember(app.key) { mutableStateOf(app.underNavigation) }
    WindowBottomSheet(show = true, title = app.name, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(stringResource(Res.string.ui_open), { onDismiss(); app.launch() }, Modifier.fillMaxWidth(), enabled = app.enabled, colors = ButtonDefaults.textButtonColorsPrimary())
            SwitchPreference(title = stringResource(Res.string.ui_add_favorite), checked = app.favorite, onCheckedChange = { app.toggleFavorite() })
            app.pin?.let { pin -> TextButton(stringResource(Res.string.ui_pin_app), { pin(); onDismiss() }, Modifier.fillMaxWidth()) }
            SmallTitle(stringResource(Res.string.ui_display_settings))
            SwitchPreference(title = stringResource(Res.string.ui_under_status), checked = status, onCheckedChange = { status = it; app.setLayout(status, navigation) })
            SwitchPreference(title = stringResource(Res.string.ui_under_navigation), checked = navigation, onCheckedChange = { navigation = it; app.setLayout(status, navigation) })
            SmallTitle(stringResource(Res.string.ui_app_info))
            BasicComponent(title = stringResource(Res.string.ui_package), summary = app.packageName)
        }
    }
}
