package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

@Composable
fun DevicesPage(model:DevicesModel,bottomPadding:Dp=0.dp) {
    val scroll=MiuixScrollBehavior(); val backdrop=rememberBlurBackdrop()
    var address by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<DeviceAction?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(topBar={ BlurredBar(backdrop) {
        AdaptiveTopAppBar("设备",color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,scrollBehavior=scroll,
            actions={IconButton(onClick={adding=true;model.refresh()}) {Icon(MiuixIcons.Add,"添加设备",tint=MiuixTheme.colorScheme.onSurface)}})
    } }) { inner -> WideContentBox { side ->
        LazyColumn(Modifier.fillMaxSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier)
            .scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding=PaddingValues(start=side,end=side,top=inner.calculateTopPadding(),bottom=bottomPadding)) {
            item { SmallTitle("本机") }
            groupedCardItems("local",buildList {
                add(CardItem("device") { ArrowPreference(title=model.name,summary="此设备 · 本机独立应用",onClick={selected="local"}) })
                add(CardItem("receive") {
                    SwitchPreference(title="接收局域网连接",summary=model.status,checked=model.receiving,onCheckedChange={model.toggleReceiving()})
                })
                add(CardItem("permission") { ArrowPreference(title="被控权限",summary=model.privilege,onClick=model.enablePrivilege) })
                add(CardItem("pair") { ArrowPreference(title=if(model.pairing) "配对入口已开放" else "允许新配对",summary="与新设备核对校验码后建立持久配对",enabled=model.receiving,onClick=model.enablePairing) })
            })
            item { SmallTitle("已配对设备") }
            if(model.peers.isEmpty()) item { Card(Modifier.padding(horizontal=12.dp).padding(bottom=12.dp)) {
                BasicComponent(title="添加另一台设备",summary="首次核对校验码后保存配对关系。",onClick={adding=true;model.refresh()})
            } }
            model.peers.forEach { card -> groupedCardItems("peer-${card.id}",listOf(CardItem("title") {
                ArrowPreference(title=card.name,summary=if(model.sessions.any {it.id==card.id})"已有会话" else if(model.nearby.any {it.id==card.id})"附近 · 已配对" else "已保存配对",onClick={selected=card.id})
            })) }
            item {TextButton("添加设备",{adding=true;model.refresh()},Modifier.fillMaxWidth().padding(horizontal=12.dp))}
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    } }
    val detail=model.peers.firstOrNull {it.id==selected}
    WindowDialog(show=selected!=null,title=detail?.name?:model.name,onDismissRequest={selected=null}) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(detail?.summary?:"此设备 · ${model.privilege}")
            (detail?.actions?:model.localActions).forEach {action->
                ArrowPreference(title=action.title,enabled=action.enabled,onClick={
                    selected=null
                    if(action.key=="remove")removing=action else action.invoke()
                })
            }
        }
    }
    WindowDialog(show=adding,title="添加设备",onDismissRequest={adding=false}) {
        LazyColumn(Modifier.heightIn(max=460.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {Text("先在对方设备允许新配对，再核对双方校验码。")}
            item {TextButton("刷新附近设备",model.refresh,Modifier.fillMaxWidth())}
            items(model.nearby,key={it.id}) {device->ArrowPreference(title=device.name,summary="${device.host} · 尚未验证身份",onClick={adding=false;device.pair()})}
            item {SmallTitle("手动地址")}
            item {TextField(address,{address=it},label="例如 192.168.0.101",modifier=Modifier.fillMaxWidth())}
            item {TextButton("发起配对",{adding=false;model.pair(address.trim())},Modifier.fillMaxWidth(),enabled=address.isNotBlank())}
            model.manualConnections(address.trim()).forEach {action->item(key=action.key) {
                TextButton(action.title,{adding=false;action.invoke()},Modifier.fillMaxWidth(),enabled=address.isNotBlank()&&action.enabled)
            }}
        }
    }
    removing?.let { action -> WindowDialog(show=true,title="解除配对？",onDismissRequest={removing=null}) {
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("本机会立即撤销对方权限并中止连接。再次连接需要重新配对。")
            TextButton("解除配对",{action.invoke();removing=null},Modifier.fillMaxWidth())
        }
    } }
}
