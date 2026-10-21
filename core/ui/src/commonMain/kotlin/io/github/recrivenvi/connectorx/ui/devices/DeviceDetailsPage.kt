package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.StateCard
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DeviceDetailsPage(
    model: DevicesModel,
    id: String,
    onBack: () -> Unit,
    onApps: (String) -> Unit
) {
    val card = model.peers.firstOrNull { it.id == id }
    var removing by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf<DeviceCard?>(null) }
    TaskPage(card?.name ?: stringResource(Res.string.ui_device_details), onBack = onBack) {
        if (card == null) item {
            StateCard(
                stringResource(Res.string.ui_device_missing),
                stringResource(Res.string.ui_device_missing_reason)
            ) {
                TextButton(
                    stringResource(Res.string.ui_return_devices),
                    onBack,
                    Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                BasicComponent(
                    title = card.name,
                    startAction = {
                        Icon(
                            vectorResource(Res.drawable.ic_aosp_smartphone),
                            null,
                            Modifier.padding(end = 16.dp)
                        )
                    },
                    endActions = { DeviceStatusBadge(card.presence) })
            }
            if (card.presence == DevicePresence.Saved) item {
                StateCard(
                    stringResource(Res.string.ui_offline_title),
                    stringResource(Res.string.ui_offline_reason)
                ) {
                    TextButton(
                        stringResource(Res.string.ui_retry_connection),
                        { card.invoke("apps") },
                        Modifier.fillMaxWidth(),
                        enabled = card.enabled("apps")
                    )
                }
            }
            item { SmallTitle(stringResource(Res.string.ui_actions)) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = stringResource(Res.string.ui_open_apps),
                        onClick = { onApps(id) },
                        enabled = card.presence != DevicePresence.Saved && card.enabled("apps")
                    )
                    ArrowPreference(
                        title = stringResource(Res.string.ui_device_mirror),
                        onClick = { card.invoke("mirror") },
                        enabled = card.presence != DevicePresence.Saved && card.enabled("mirror")
                    )
                }
            }
            val sessions = model.sessions.filter { it.id == id }
            if (sessions.isNotEmpty()) item { SmallTitle(stringResource(Res.string.ui_session)) }
            sessions.forEachIndexed { index, session ->
                item(key = "session-$index") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        ArrowPreference(
                            title = session.title(),
                            summary = stringResource(Res.string.ui_session_kept),
                            onClick = { session.invoke("resume") })
                        TextButton(
                            stringResource(Res.string.ui_exit_session),
                            { exiting = session },
                            Modifier.fillMaxWidth().padding(16.dp)
                        )
                    }
                }
            }
            item { SmallTitle(stringResource(Res.string.ui_connection)) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = stringResource(Res.string.ui_lan_address),
                        summary = card.address
                    )
                    BasicComponent(
                        title = stringResource(Res.string.ui_identity),
                        summary = card.identity.take(8) + "…" + card.identity.takeLast(8)
                    )
                }
            }
            item {
                TextButton(
                    stringResource(Res.string.ui_unpair),
                    { removing = true },
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                )
            }
        }
    }
    if (card != null) WindowDialog(
        show = removing,
        title = stringResource(Res.string.ui_unpair_title, card.name),
        onDismissRequest = { removing = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.ui_unpair_reason))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    stringResource(Res.string.ui_cancel),
                    { removing = false },
                    Modifier.weight(1f)
                )
                TextButton(
                    stringResource(Res.string.ui_unpair),
                    { removing = false; card.invoke("remove"); onBack() },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                )
            }
        }
    }
    ExitSessionDialog(exiting != null, { exiting = null }) {
        exiting?.invoke("close"); exiting = null
    }
}

@Composable
fun ExitSessionDialog(show: Boolean, onDismiss: () -> Unit, onExit: () -> Unit) {
    WindowDialog(
        show = show,
        title = stringResource(Res.string.ui_exit_title),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.ui_exit_reason))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(stringResource(Res.string.ui_cancel), onDismiss, Modifier.weight(1f))
                TextButton(
                    stringResource(Res.string.ui_exit_session), onExit, Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                )
            }
        }
    }
}
