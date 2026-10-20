package io.github.recrivenvi.connectorx.ui.remote

import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.session.RemoteSession

import android.view.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.recrivenvi.connectorx.ui.component.AdaptiveTopAppBar
import io.github.recrivenvi.connectorx.ui.component.blur.BlurredBar
import io.github.recrivenvi.connectorx.ui.component.blur.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ControlPage(engine: ConnectorEngine, session: RemoteSession) {
    var text by remember { mutableStateOf("") }
    var keyboard by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(false) }
    val backdrop=rememberBlurBackdrop()
    BackHandler(enabled = engine.prompt == null) {
        if (keyboard) keyboard = false else if (session.running) session.key(KeyEvent.KEYCODE_BACK) else engine.disconnect()
    }
    Scaffold(topBar = { BlurredBar(backdrop) {
        AdaptiveTopAppBar(title=session.peer.name,color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,
            navigationIcon={ IconButton(onClick={ engine.disconnect() }) { Icon(MiuixIcons.Back,"退出远程控制",tint=MiuixTheme.colorScheme.onSurface) } })
    } }) { padding ->
        Column(Modifier.fillMaxSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier).padding(padding)) {
            RemoteVideo(session, Modifier.weight(1f).fillMaxWidth())
            Text(engine.status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Box(Modifier.padding(horizontal=12.dp)) { StreamQualitySummary(session) }
            Text(session.audioStatus,Modifier.padding(horizontal=12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton("返回", { session.key(KeyEvent.KEYCODE_BACK) }, Modifier.weight(1f), enabled = session.running)
                TextButton("桌面", { session.key(KeyEvent.KEYCODE_HOME) }, Modifier.weight(1f), enabled = session.running)
                TextButton("质量", { quality = true }, Modifier.weight(1f))
                TextButton("文本", { keyboard = true }, Modifier.weight(1f), enabled = session.running)
                TextButton("退出", { engine.disconnect() }, Modifier.weight(1f))
            }
        }
    }
    WindowDialog(show = quality, title = "投屏质量", onDismissRequest = { quality = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StreamQualityDetails(session)
            TextButton("关闭", { quality = false }, Modifier.fillMaxWidth())
            TextButton("返回 ConnectorX", {quality=false;engine.showLibrary()}, Modifier.fillMaxWidth())
        }
    }
    WindowDialog(show = keyboard, title = "发送文本", onDismissRequest = { keyboard = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("首版复用 scrcpy 文本注入，建议先测试英文和数字；完整输入法稍后验证。")
            TextField(value = text, onValueChange = { text = it }, label = "文本")
            TextButton("发送", { session.text(text); keyboard = false }, Modifier.fillMaxWidth())
        }
    }
}
