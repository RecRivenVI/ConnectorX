package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.StateCard
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun AddDevicePage(model: DevicesModel, onBack: () -> Unit, onApps: (String) -> Unit) {
    var role by rememberSaveable { mutableIntStateOf(0) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var lastAddress by rememberSaveable { mutableStateOf("") }
    val originalPeers = remember { model.peers.map { it.id }.toSet() }
    val added = model.peers.firstOrNull { it.id !in originalPeers }
    val failed = attempted && model.pairingFailed
    LaunchedEffect(Unit) { model.refresh() }
    fun pair(host: String) { lastAddress = host; attempted = true; model.pair(host) }
    TaskPage(stringResource(Res.string.ui_add_device), onBack = onBack) {
        item { TabRow(listOf(stringResource(Res.string.ui_pair_control), stringResource(Res.string.ui_pair_host)), role, { role = it }, Modifier.padding(12.dp)) }
        if (added != null) item {
            StateCard(stringResource(Res.string.ui_pair_success, added.name), stringResource(Res.string.ui_pair_success_reason)) {
                TextButton(stringResource(Res.string.ui_done), onBack, Modifier.fillMaxWidth())
                TextButton(stringResource(Res.string.ui_open_apps), { onApps(added.id) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        } else if (role == 0) {
            item { BasicComponent(title = stringResource(Res.string.ui_pair_step1)) }
            item { BasicComponent(title = stringResource(Res.string.ui_pair_step2)) }
            if (failed) item {
                StateCard(stringResource(Res.string.ui_pair_failed), stringResource(Res.string.ui_pair_failed_reason)) {
                    TextButton(stringResource(Res.string.ui_retry), { pair(lastAddress) }, Modifier.fillMaxWidth())
                }
            }
            item { SmallTitle(stringResource(Res.string.ui_nearby_devices)) }
            if (model.discovering) item { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfiniteProgressIndicator()
                Text(stringResource(Res.string.ui_searching))
            } }
            model.nearby.forEach { nearby -> item(key = nearby.id) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ArrowPreference(title = nearby.name, summary = stringResource(Res.string.ui_unverified_address, nearby.host), onClick = { pair(nearby.host) })
                }
            } }
            if (model.nearby.isEmpty()) item {
                StateCard(stringResource(if (model.discoveryFailed) Res.string.ui_discovery_failed else Res.string.ui_no_nearby),
                    stringResource(if (model.discoveryFailed) Res.string.ui_discovery_failed_reason else Res.string.ui_no_nearby_reason)) {
                    TextButton(stringResource(Res.string.ui_search_again), model.refresh, Modifier.fillMaxWidth())
                }
            }
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                ArrowPreference(title = stringResource(Res.string.ui_enter_address), summary = stringResource(Res.string.ui_enter_address_summary), onClick = { manual = !manual })
                if (manual) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(address, { address = it }, label = stringResource(Res.string.ui_peer_address), modifier = Modifier.fillMaxWidth())
                    TextButton(stringResource(Res.string.ui_pair_start), { pair(address.trim()) }, Modifier.fillMaxWidth(), enabled = address.isNotBlank() && !model.pairingAttempt, colors = ButtonDefaults.textButtonColorsPrimary())
                    model.manualConnections(address.trim()).forEach { action ->
                        TextButton(stringResource(Res.string.ui_connect_existing, action.title), action.invoke,
                            Modifier.fillMaxWidth(), enabled = action.enabled && address.isNotBlank())
                    }
                }
            } }
        } else if (!model.receiving) item {
            StateCard(stringResource(Res.string.ui_need_receive), stringResource(Res.string.ui_need_receive_reason)) {
                SwitchPreference(title = stringResource(Res.string.ui_receive), checked = model.receiving, onCheckedChange = { model.toggleReceiving() })
            }
        } else {
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchPreference(title = stringResource(Res.string.ui_accept_pairing), checked = model.pairing, enabled = !model.pairing,
                    summary = stringResource(if (model.pairing) Res.string.ui_accept_pairing_on else Res.string.ui_accept_pairing_off), onCheckedChange = { if (it) model.enablePairing() })
                BasicComponent(title = stringResource(if (model.pairing) Res.string.ui_pair_waiting else Res.string.ui_pair_not_accepting),
                    summary = stringResource(Res.string.ui_pair_local_address, model.name, model.address.ifBlank { stringResource(Res.string.ui_address_unavailable) }))
                if (model.pairing) InfiniteProgressIndicator(Modifier.padding(16.dp))
            } }
            item { BasicComponent(title = stringResource(Res.string.ui_pair_gate_limit)) }
        }
    }
}
