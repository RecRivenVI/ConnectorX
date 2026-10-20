package io.github.recrivenvi.connectorx.ui.mirror

import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.session.RemoteSession

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import io.github.recrivenvi.connectorx.ui.remote.StreamQualityDetails
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DeviceMirrorPage(engine: ConnectorEngine, session: RemoteSession) {
    var details by remember { mutableStateOf(false) }
    var showGeometryOverlay by remember(session) { mutableStateOf(false) }
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
                update = {
                    it.geometry = if (session.geometryStale) null else geometry
                    it.showGeometryOverlay = showGeometryOverlay
                })
        }
        Text(session.peer.name, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton("返回", { session.key(android.view.KeyEvent.KEYCODE_BACK) }, Modifier.weight(1f), enabled = session.running)
            TextButton("桌面", { session.key(android.view.KeyEvent.KEYCODE_HOME) }, Modifier.weight(1f), enabled = session.running)
            TextButton("功能", { details = true }, Modifier.weight(1f))
            TextButton("退出", { engine.disconnect() }, Modifier.weight(1f))
        }
        if (!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(engine.status)
                TextButton("返回设备", { engine.disconnect() }, Modifier.fillMaxWidth())
            }
        }
    }
    WindowDialog(show = details, title = "设备镜像", onDismissRequest = { details = false }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StreamQualityDetails(session)
            val g = session.geometry
            if (g != null) {
                Text("主屏 ${g.naturalWidth}×${g.naturalHeight} · rotation=${g.rotation}\n视频 ${session.width}×${session.height}")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("叠加到实时画面", Modifier.weight(1f))
                    Switch(checked = showGeometryOverlay, onCheckedChange = { showGeometryOverlay = it })
                }
                GeometryPreview(g)
                Text("灰色：系统圆角 + G2 预览　橙色：bounding rect　红色：系统 cutout path")
                Text("自然方向系统半径：${g.requestedRadii.joinToString()} px\n实际尺寸参数：${g.effectiveRadii.joinToString { "%.2f".format(it) }} px\n过渡长度：1.5 × 参数。灰边仅向外扩展。")
                Text("挖孔：${if (g.cutoutBoundsLogical.isEmpty()) "无" else "有"}\n" +
                    "bounding rect：${g.cutoutBoundsLogical.joinToString { "[${it.left.toInt()}, ${it.top.toInt()}, ${it.right.toInt()}, ${it.bottom.toInt()}]" }}\n" +
                    "safeInsets [左, 上, 右, 下]：${g.safeInsetsLogical.joinToString()} px\n" +
                    "waterfallInsets：${g.waterfallInsetsLogical.joinToString()} px\n" +
                    "cutout path：${g.cutoutPathKind}，${g.cutoutContours.sumOf { it.size / 2 }} 个采样点")
                val circle = g.preciseCutoutCircle
                Text(if (circle != null) "精确圆孔：center=(${"%.3f".format(circle[0])}, ${"%.3f".format(circle[1])}) px，radius=${"%.3f".format(circle[2])} px\n来源：${g.preciseCutoutSource}"
                    else "精确圆孔：设备未提供，保持不绘制")
                if (g.effectiveRadii.any { it == 0.0 }) Text("系统未提供有效半径的角保留为直角。")
                val issues = g.raw.optJSONArray("issues")
                if (issues != null) for (i in 0 until issues.length()) Text(issues.getString(i))
            }
            if (session.geometryStale) Text("正在同步方向与几何，触摸暂时暂停。")
            session.geometryError?.let { Text(it) }
            Text("G2 造型不代表面板物理曲率；DisplayCutout 也可能只是窗口避让范围。")
            TextButton("关闭", { details = false }, Modifier.fillMaxWidth())
            TextButton("返回 ConnectorX", {details=false;engine.showLibrary()}, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GeometryPreview(geometry: io.github.recrivenvi.connectorx.display.MirrorGeometry) {
    Canvas(Modifier.fillMaxWidth().height(280.dp).background(Color.Black)) {
        val scale = minOf(size.width / geometry.logicalWidth, size.height / geometry.logicalHeight) * 0.92f
        val previewWidth = geometry.logicalWidth * scale
        val previewHeight = geometry.logicalHeight * scale
        val left = (size.width - previewWidth) / 2f
        val top = (size.height - previewHeight) / 2f
        val matrix = geometry.matrix(previewWidth, previewHeight).apply { postTranslate(left, top) }
        val outline = android.graphics.Path(geometry.outline).apply { transform(matrix) }
        drawPath(outline.asComposePath(), Color(0xFF33363B))
        drawPath(outline.asComposePath(), Color(0xFFB8BCC4), style = Stroke(width = 2.dp.toPx()))
        geometry.cutoutBoundsLogical.forEach { rect ->
            drawRect(Color(0xFFFFA000),
                topLeft = androidx.compose.ui.geometry.Offset(left + rect.left * scale, top + rect.top * scale),
                size = androidx.compose.ui.geometry.Size(rect.width() * scale, rect.height() * scale),
                style = Stroke(width = 2.dp.toPx()))
        }
        if (!geometry.cutoutPath.isEmpty) {
            val path = android.graphics.Path(geometry.cutoutPath).apply { transform(matrix) }
            drawPath(path.asComposePath(), Color(0x99FF3B30))
            drawPath(path.asComposePath(), Color(0xFFFF6B60), style = Stroke(width = 1.dp.toPx()))
        }
        geometry.preciseCutoutCircle?.let { circle ->
            val path = android.graphics.Path().apply { addCircle(circle[0], circle[1], circle[2], android.graphics.Path.Direction.CW); transform(matrix) }
            drawPath(path.asComposePath(), Color.Black)
        }
    }
}
