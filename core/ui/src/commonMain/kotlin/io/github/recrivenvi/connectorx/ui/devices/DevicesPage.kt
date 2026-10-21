package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.StateCard
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun DevicesPage(
    model: DevicesModel,
    bottomPadding: Dp,
    onDetails: (String) -> Unit,
    onAdd: () -> Unit,
    onApps: (String) -> Unit
) {
    TaskPage(stringResource(Res.string.ui_devices), bottomPadding = bottomPadding, actions = {
        IconButton(onAdd) { Icon(MiuixIcons.Add, stringResource(Res.string.ui_add_device)) }
    }) {
        item { SmallTitle(stringResource(Res.string.ui_local)) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                BasicComponent(
                    title = model.name,
                    summary = stringResource(Res.string.ui_this_device),
                    startAction = {
                        Icon(
                            vectorResource(Res.drawable.ic_aosp_smartphone),
                            null,
                            Modifier.padding(end = 16.dp)
                        )
                    })
                SwitchPreference(
                    title = stringResource(Res.string.ui_receive),
                    summary = receivingSummary(model),
                    checked = model.receiving,
                    onCheckedChange = { model.toggleReceiving() })
                if (model.permission.kind == PermissionKind.Ready) BasicComponent(
                    title = stringResource(Res.string.ui_permission),
                    summary = stringResource(Res.string.ui_permission_ready)
                )
            }
        }
        if (model.permission.kind != PermissionKind.Ready) item {
            PermissionCard(
                model.permission,
                model.openPrivilege,
                model.enablePrivilege
            )
        }
        item { SmallTitle(stringResource(Res.string.ui_paired_devices)) }
        if (model.peers.isEmpty()) item {
            StateCard(
                stringResource(Res.string.ui_no_devices),
                stringResource(Res.string.ui_no_devices_reason)
            ) {
                TextButton(
                    stringResource(Res.string.ui_add_device),
                    onAdd,
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
        model.peers.forEach { card ->
            item(key = card.id) {
                Card(
                    onClick = { onDetails(card.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicComponent(
                            modifier = Modifier.weight(1f), title = card.name,
                            summary = stringResource(if (card.presence == DevicePresence.Saved) Res.string.ui_saved_reason else Res.string.ui_nearby_reason),
                            startAction = {
                                Icon(
                                    vectorResource(Res.drawable.ic_aosp_smartphone),
                                    null,
                                    Modifier.padding(end = 16.dp)
                                )
                            })
                        DeviceStatusBadge(card.presence, Modifier.padding(end = 16.dp))
                    }
                    model.sessions.filter { it.id == card.id }.forEach { session ->
                        BasicComponent(
                            title = stringResource(Res.string.ui_session_held, session.title()),
                            summary = stringResource(Res.string.ui_resume_session),
                            onClick = { session.invoke("resume") })
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (card.presence == DevicePresence.Saved) TextButton(
                            stringResource(Res.string.ui_retry_connection),
                            { card.invoke("apps") },
                            Modifier.weight(1f),
                            enabled = card.enabled("apps")
                        )
                        else {
                            TextButton(
                                stringResource(Res.string.ui_open_apps),
                                { onApps(card.id) },
                                Modifier.weight(1f),
                                enabled = card.enabled("apps")
                            )
                            TextButton(
                                stringResource(Res.string.ui_mirror),
                                { card.invoke("mirror") },
                                Modifier.weight(1f),
                                enabled = card.enabled("mirror")
                            )
                        }
                    }
                }
            }
        }
        item {
            TextButton(
                stringResource(Res.string.ui_add_device),
                onAdd,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
internal fun receivingSummary(model: DevicesModel): String = if (model.receiving)
    stringResource(
        Res.string.ui_receiving,
        model.address.ifBlank { stringResource(Res.string.ui_address_unavailable) })
else stringResource(Res.string.ui_receive_off)

@Composable
fun PermissionCard(
    permission: PermissionPresentation,
    open: () -> Unit,
    retry: () -> Unit,
    title: String? = null
) {
    StateCard(
        title ?: stringResource(Res.string.ui_permission_unavailable),
        stringResource(permissionReason(permission.kind))
    ) {
        val waiting = permission.action == PermissionAction.Wait
        if (waiting) InfiniteProgressIndicator()
        TextButton(
            stringResource(
                when (permission.action) {
                    PermissionAction.OpenService -> Res.string.ui_open_shizuku
                    PermissionAction.Reconnect -> Res.string.ui_reconnect
                    PermissionAction.Wait -> Res.string.ui_connecting
                }
            ),
            if (permission.action == PermissionAction.OpenService) open else retry,
            Modifier.fillMaxWidth(),
            enabled = !waiting,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

internal fun DeviceCard.invoke(key: String) {
    actions.firstOrNull { it.key == key && it.enabled }?.invoke?.invoke()
}

internal fun DeviceCard.enabled(key: String): Boolean = actions.any { it.key == key && it.enabled }
@Composable
internal fun DeviceCard.title(): String =
    summary.ifBlank { stringResource(sessionTypeLabel(sessionType)) }
