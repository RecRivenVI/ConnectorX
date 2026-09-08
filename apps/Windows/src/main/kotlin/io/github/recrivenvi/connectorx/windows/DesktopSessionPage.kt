package io.github.recrivenvi.connectorx.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

@Composable
fun DesktopSessionPage(engine:DesktopEngine,session:DesktopSession) {
    var menu by remember {mutableStateOf(false)}
    LaunchedEffect(menu,session.running) {while(menu&&session.running) {engine.refreshInfo(session);delay(2000)}}
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton("返回",{session.key(4)},enabled=session.running)
            TextButton("主页",{session.key(3)},enabled=session.running)
            TextButton("最近任务",{session.key(187)},enabled=session.running)
            TextButton("会话设置",{session.panel.cancelInputs();menu=true})
            TextButton("退出",{session.close()})
        }
        // Heavyweight AWT video must not obscure the Compose settings dialog. Detaching the view keeps the session/decoder alive.
        if(!menu) SwingPanel(factory={session.panel},modifier=Modifier.weight(1f).fillMaxWidth())
        else Spacer(Modifier.weight(1f).fillMaxWidth())
        Text("${session.width}×${session.height} · 解码输出约 ${"%.1f".format(session.renderedFps)} fps · ${session.touchSupport}",Modifier.padding(horizontal=12.dp))
        Text(session.audioStatus,Modifier.padding(horizontal=12.dp))
        session.videoError?.let {Text(it,Modifier.padding(horizontal=12.dp));TextButton("恢复视频",session::retryVideo)}
        session.error?.let {Text(it,Modifier.padding(12.dp))}
    }
    WindowDialog(show=menu,title=session.app?.packageName ?: "${session.peer.name} · 会话",onDismissRequest={menu=false}) {
        Column(Modifier.heightIn(max=580.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${session.peer.name}\nDisplay ${session.info.optInt("displayId",-1)} · Activity Display ${session.info.optInt("activityDisplayId",-1)}")
            Text("实际 ${session.info.optInt("width",session.width)}×${session.info.optInt("height",session.height)} / ${session.info.optInt("dpi")} dpi · ${session.info.optDouble("refreshRate")} Hz\nrotation=${session.info.optInt("rotation",-1)}")
            Text("${session.media.videoCodec} · ${session.media.maxFps} fps 上限 · ${session.media.videoBitRate} bps")
            Text(session.audioStatus)
            if(session.app!=null) {
                AppOptionsEditor(session.options) {engine.saveOptions(session,it)}
                Text("尺寸、密度、刷新率和远端系统栏设置按包名保存，下次新建会话生效。跟随窗口大小立即生效，属于 flex resize，不代表真实旋转。")
                TextButton(if(session.restarting) "正在强制重启…" else "强制重启远端应用",{engine.restartApp(session)},enabled=session.running&&!session.restarting)
                Text("强制停止当前目标包并在同一虚拟显示重开，未保存内容可能丢失；不会重建显示或应用新的系统栏开关。")
                session.restartMessage?.let {Text(it)}
            }
            TextButton("粘贴本机剪贴板文本",{
                runCatching {Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String}
                    .onSuccess(session::paste).onFailure {session.error="剪贴板没有可用文本：${it.message}"}
            },enabled=session.running&&session.capabilities.optBoolean("clipboardPaste"))
            Text("输入法只转发已提交文本；Unicode 使用远端剪贴板粘贴，不持续同步剪贴板。")
            TextButton("恢复视频",session::retryVideo,enabled=session.running)
            TextButton("继续使用",{menu=false})
            TextButton("退出会话",{menu=false;session.close()})
        }
    }
}

@Composable private fun AppOptionsEditor(options:AppOptions,save:(AppOptions)->Unit) {
    var width by remember(options.width) {mutableStateOf(options.width.toString())}
    var height by remember(options.height) {mutableStateOf(options.height.toString())}
    var dpi by remember(options.dpi) {mutableStateOf(options.dpi.toString())}
    var rate by remember(options.refreshRate) {mutableStateOf(options.refreshRate.toString())}
    var error by remember {mutableStateOf<String?>(null)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextField(width,{width=it},label="宽度 px",modifier=Modifier.weight(1f))
        TextField(height,{height=it},label="高度 px",modifier=Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextField(dpi,{dpi=it},label="密度 dpi",modifier=Modifier.weight(1f))
        TextField(rate,{rate=it},label="刷新率 Hz",modifier=Modifier.weight(1f))
    }
    TextButton("保存显示参数",{
        runCatching {options.copy(width=width.toInt(),height=height.toInt(),dpi=dpi.toInt(),refreshRate=rate.toFloat()).also {it.request("io.github.connectorx.validation")}}
            .onSuccess {save(it);error=null}.onFailure {error=it.message ?: "参数无效"}
    })
    error?.let {Text(it)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("跟随窗口尺寸");Switch(options.followWindow,{save(options.copy(followWindow=it))})}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("显示远端系统栏");Switch(options.systemDecorations,{save(options.copy(systemDecorations=it))})}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("输入法 Unicode 粘贴");Switch(options.unicodePaste,{save(options.copy(unicodePaste=it))})}
}
