package io.github.recrivenvi.connectorx.ui.mirror

import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.session.RemoteSession

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DeviceMirrorPage(engine: ConnectorEngine, session: RemoteSession) {
    var details by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusHeight = WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat()
    val navigationHeight = WindowInsets.navigationBars.getBottom(density).toFloat()
    LaunchedEffect(session, session.videoEpoch) {
        while (session.running) { engine.refreshGeometry(session); delay(2000) }
    }
    BackHandler(enabled = !details && engine.prompt == null) {
        if (session.running) session.key(android.view.KeyEvent.KEYCODE_BACK) else engine.disconnect()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        val border = with(density) { 4.dp.toPx() }
        val margin = maxOf(statusHeight * 1.5f, navigationHeight + with(density) { 44.dp.toPx() }, statusHeight + with(density) { 24.dp.toPx() })
        val geometry = session.geometry
        val useGeometry = geometry != null && (session.width == 0 || (!session.geometryStale && geometry.matchesVideo(session.width, session.height)))
        val sourceWidth = if (useGeometry) geometry.logicalWidth.toFloat() else session.width.coerceAtLeast(1).toFloat()
        val sourceHeight = if (useGeometry) geometry.logicalHeight.toFloat() else session.height.coerceAtLeast(1).toFloat()
        val layout = ScreenMath.fit(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, margin, border, sourceWidth, sourceHeight)
        if (layout[0] > 0 && layout[1] > 0) {
            AndroidView(modifier = Modifier.align(Alignment.Center)
                .width(with(density) { (layout[0] + 2 * border).toDp() }).height(with(density) { (layout[1] + 2 * border).toDp() }),
                factory = { context -> DeviceMirrorView(context, session, border) },
                update = { it.geometry = if (session.geometryStale) null else geometry })
        }
        Text(session.peer.name, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton("返回", { session.key(android.view.KeyEvent.KEYCODE_BACK) }, Modifier.weight(1f), enabled = session.running)
            TextButton("桌面", { session.key(android.view.KeyEvent.KEYCODE_HOME) }, Modifier.weight(1f), enabled = session.running)
            TextButton("几何", { details = true }, Modifier.weight(1f))
            TextButton("退出", { engine.disconnect() }, Modifier.weight(1f))
        }
        if (!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(engine.status)
                TextButton("返回设备", { engine.disconnect() }, Modifier.fillMaxWidth())
            }
        }
    }
    WindowDialog(show = details, title = "G2 连续圆角预览", onDismissRequest = { details = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.audioStatus)
            val g = session.geometry
            if (g != null) {
                Text("主屏 ${g.naturalWidth}×${g.naturalHeight} · rotation=${g.rotation}\n视频 ${session.width}×${session.height}")
                Text("轮廓：系统参数 + ConnectorX G2 曲线\n挖孔遮罩：暂时关闭")
                Text("自然方向系统半径：${g.requestedRadii.joinToString()} px\n实际尺寸参数：${g.effectiveRadii.joinToString { "%.2f".format(it) }} px\n过渡长度：1.5 × 参数。灰边仅向外扩展。")
                if (g.effectiveRadii.any { it == 0.0 }) Text("系统未提供有效半径的角保留为直角。")
                val issues = g.raw.optJSONArray("issues")
                if (issues != null) for (i in 0 until issues.length()) Text(issues.getString(i))
            }
            if (session.geometryStale) Text("正在同步方向与几何，触摸暂时暂停。")
            session.geometryError?.let { Text(it) }
            Text("这是自定义 G2 造型，不代表还原面板物理曲率。摄像头挖孔以后仍使用具体贴图，本次仅比较圆角。")
            TextButton("关闭", { details = false }, Modifier.fillMaxWidth())
        }
    }
}
