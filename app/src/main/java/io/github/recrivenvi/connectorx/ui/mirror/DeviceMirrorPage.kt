package io.github.recrivenvi.connectorx.ui.mirror

import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.session.RemoteSession
import io.github.recrivenvi.connectorx.ui.screen.settings.LocalSessionDiagnostics

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
internal fun DeviceMirrorPage(engine: ConnectorEngine, session: RemoteSession) {
    io.github.recrivenvi.connectorx.ui.remote.NetworkSessionPage(engine, session)
}

@Composable
internal fun GeometryPreview(geometry: io.github.recrivenvi.connectorx.display.MirrorGeometry) {
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
