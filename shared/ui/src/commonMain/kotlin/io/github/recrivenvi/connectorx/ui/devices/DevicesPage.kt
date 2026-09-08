package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.ui.component.*
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DevicesPage(model:DevicesModel,bottomPadding:Dp=0.dp) {
    val scroll=MiuixScrollBehavior(); val backdrop=rememberBlurBackdrop()
    var address by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<DeviceAction?>(null) }
    Scaffold(topBar={ BlurredBar(backdrop) {
        AdaptiveTopAppBar("ConnectorX",color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,scrollBehavior=scroll)
    } }) { inner -> WideContentBox { side ->
        LazyColumn(Modifier.fillMaxSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier)
            .scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding=PaddingValues(start=side,end=side,top=inner.calculateTopPadding(),bottom=bottomPadding)) {
            item { SmallTitle("本机") }
            groupedCardItems("local",buildList {
                add(CardItem("device") { BasicComponent(title=model.name,summary="设备身份 ${model.identityId.take(16)}") })
                add(CardItem("receive") {
                    if(model.toggleReceiving!=null) SwitchPreference(title="接收局域网连接",summary=model.status,checked=model.receiving,onCheckedChange={model.toggleReceiving.invoke()})
                    else BasicComponent(title="Windows · 仅控制方",summary=model.status)
                })
                if(model.enablePrivilege!=null) add(CardItem("permission") { ArrowPreference(title="被控权限",summary=model.privilege,onClick=model.enablePrivilege) })
                add(CardItem("pair") { ArrowPreference(title=if(model.pairing) "配对入口已开放" else "允许新配对",summary="与新设备核对校验码后建立持久配对",enabled=model.receiving,onClick=model.enablePairing) })
            })
            if(model.sessions.isNotEmpty()) item { SmallTitle("已连接会话") }
            model.sessions.forEach { card -> groupedCardItems("session-${card.id}",listOf(
                CardItem("title") { BasicComponent(title=card.name,summary=card.summary) },
                CardItem("actions") { Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    card.actions.forEach { action -> TextButton(action.title,action.invoke,Modifier.weight(1f),enabled=action.enabled) }
                } }
            )) }
            item { SmallTitle("已配对设备") }
            if(model.peers.isEmpty()) item { Card(Modifier.padding(horizontal=12.dp).padding(bottom=12.dp)) {
                BasicComponent(title="尚未配对",summary="先在对方设备允许新配对，再从附近设备或手动地址发起。")
            } }
            model.peers.forEach { card -> groupedCardItems("peer-${card.id}",listOf(CardItem("title") {
                BasicComponent(title=card.name,summary=card.summary)
            })+card.actions.map { action -> CardItem(action.key) {
                ArrowPreference(title=action.title,enabled=action.enabled,onClick={
                    if(action.key=="remove") removing=action else action.invoke()
                })
            } }) }
            item { SmallTitle("附近设备") }
            item { TextButton("刷新发现",model.refresh,Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp)) }
            model.nearby.forEach { device -> item(key="candidate-${device.id}") { Card(Modifier.padding(horizontal=12.dp).padding(bottom=12.dp)) {
                ArrowPreference(title=device.name,summary="${device.host} · 尚未验证身份",onClick=device.pair)
            } } }
            item { SmallTitle("手动连接") }
            item { TextField(address,{address=it},label="例如 192.168.0.101",modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp)) }
            item { TextButton("发起配对",{model.pair(address.trim())},Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp),enabled=address.isNotBlank()) }
            model.manualConnections(address.trim()).forEach { action -> item(key="manual-${action.key}") {
                TextButton(action.title,action.invoke,Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp),enabled=address.isNotBlank()&&action.enabled)
            } }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    } }
    removing?.let { action -> WindowDialog(show=true,title="解除配对？",onDismissRequest={removing=null}) {
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("本机会立即撤销对方权限并中止连接。再次连接需要重新配对。")
            TextButton("解除配对",{action.invoke();removing=null},Modifier.fillMaxWidth())
        }
    } }
}
