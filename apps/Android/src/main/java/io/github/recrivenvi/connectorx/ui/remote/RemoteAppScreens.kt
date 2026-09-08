package io.github.recrivenvi.connectorx.ui.remote

import io.github.recrivenvi.connectorx.connection.AppCatalog
import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.connection.RemoteApp
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.session.RemoteSession

import androidx.activity.compose.BackHandler
import io.github.recrivenvi.connectorx.ui.component.AdaptiveTopAppBar
import io.github.recrivenvi.connectorx.ui.component.blur.BlurredBar
import io.github.recrivenvi.connectorx.ui.component.blur.rememberBlurBackdrop
import io.github.recrivenvi.connectorx.ui.util.horizontalCutoutPadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun RemoteAppsPage(engine:ConnectorEngine,catalog:AppCatalog,launch:(RemoteApp)->Unit,pin:(RemoteApp)->Unit) {
    ApplicationsPage(catalog.peer.name,catalog.apps.map { ApplicationItem(it.packageName,it.name) },engine.status,
        engine.connectingPeerId==null,engine::closeCatalog,
        launch={launch(RemoteApp(it.packageName,it.name))},pin={pin(RemoteApp(it.packageName,it.name))})
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RemoteApplicationPage(engine: ConnectorEngine, session: RemoteSession) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val packageName = session.app!!.packageName
    var layout by remember(packageName) { mutableStateOf(AppLayoutPreferences.load(context,packageName)) }
    fun save(value: AppLayoutPreferences) { layout=value; value.save(context,packageName) }
    val landscape=LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var videoReserved = WindowInsets(0,0,0,0)
    if(!layout.underStatus) videoReserved=videoReserved.union(WindowInsets.statusBarsIgnoringVisibility)
    if(!layout.underNavigation) videoReserved=videoReserved.union(WindowInsets.navigationBarsIgnoringVisibility)
    videoReserved=videoReserved.union(WindowInsets.captionBar)
    if(!landscape && !layout.underStatus) videoReserved=videoReserved.union(WindowInsets.displayCutout)
    DisposableEffect(session, menu) {
        session.localUiActive = menu
        onDispose { session.localUiActive = false }
    }
    var viewport by remember(session) { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(session, viewport) {
        if (viewport.width < 128 || viewport.height < 128) return@LaunchedEffect
        delay(250)
        session.requestResize(viewport.width, viewport.height)
    }
    LaunchedEffect(session, menu) {
        while (session.running && menu) { engine.refreshSessionInfo(session); delay(2000) }
    }
    BackHandler(enabled = engine.prompt == null && engine.controlError == null && !menu) {
        if (session.running) session.key(android.view.KeyEvent.KEYCODE_BACK) else engine.disconnect()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(videoReserved)) {
      Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        RemoteVideo(session, Modifier.fillMaxSize())
        if (!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("远程应用会话已结束")
                Text(engine.status)
                TextButton("返回设备", { engine.disconnect() }, Modifier.fillMaxWidth())
            }
        }
      }
      }
      TextButton("⋮", { menu = true }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).size(44.dp))
    }
    WindowDialog(show = menu, title = session.appTitle ?: session.app!!.packageName, onDismissRequest = { menu = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.audioStatus)
            Text("视频 ${session.media.videoCodec} · ${session.media.maxFps} fps 上限 · ${session.media.videoBitRate} bps")
            val info = session.info
            val app = session.app!!
            Text("${session.peer.name}\n请求 ${app.width}×${app.height} / ${app.dpi}dpi · ${app.refreshRate}Hz")
            Text("远端 Display ${info.optInt("displayId", -1)} · Activity Display ${info.optInt("activityDisplayId", -1)}\n" +
                "实际 ${info.optInt("width")}×${info.optInt("height")} / ${info.optInt("dpi")}dpi · ${info.optDouble("refreshRate", 0.0)}Hz\n" +
                "方向 rotation=${info.optInt("rotation", -1)} · 视频 ${session.width}×${session.height}\n" +
                "解码输出约 ${"%.1f".format(session.renderedFps)}fps（不是端到端延迟）")
            if (!info.optBoolean("refreshRequestApplied")) Text("系统未提供请求刷新率接口，使用平台默认值。")
            session.infoError?.let { Text("状态读取：$it") }
            if (info.has("error")) Text(info.optString("error"))
            Text("系统返回发送给远端；本机 Home 保留本机行为。退出将断开当前会话并释放远端虚拟显示；切到后台仍保持连接。")
            session.videoError?.let { Text(it); TextButton("恢复视频",{ session.retryVideo() },Modifier.fillMaxWidth()) }
            Text("画面布局 · 按包名保存在本机\n$packageName")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("延伸到通知栏下方")
                Switch(checked=layout.underStatus,onCheckedChange={ save(layout.copy(underStatus=it)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("延伸到导航栏下方")
                Switch(checked=layout.underNavigation,onCheckedChange={ save(layout.copy(underNavigation=it)) })
            }
            Text("画面 ${viewport.width}×${viewport.height}。系统栏区域起手不会控制远端；画面内起手可继续拖出边界。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                Text("显示远端系统栏")
                Switch(checked=layout.systemDecorations,onCheckedChange={ save(layout.copy(systemDecorations=it)) })
            }
            Text("当前虚拟屏系统装饰：${if(app.systemDecorations) "开启" else "关闭"}。此开关按包名保存，下次新建远程应用会话生效；实际显示哪些栏由远端系统决定。")
            TextButton(if(session.restarting) "正在强制重启…" else "强制重启远端应用", { engine.restartRemoteApp(session) },
                Modifier.fillMaxWidth(), enabled=session.running && !session.restarting)
            Text("强制停止目标包并在当前虚拟屏重新启动，未保存内容可能丢失；不会重建虚拟屏或应用新的系统栏开关。")
            session.restartMessage?.let { Text(it) }
            TextButton("继续使用", { menu = false }, Modifier.fillMaxWidth())
            TextButton("退出远程应用", { menu = false; engine.disconnect() }, Modifier.fillMaxWidth())
        }
    }
}
