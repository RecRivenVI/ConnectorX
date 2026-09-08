package io.github.recrivenvi.connectorx

import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.connection.RemoteApp
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.shortcuts.RemoteShortcuts
import io.github.recrivenvi.connectorx.ui.devices.androidDevices
import io.github.recrivenvi.connectorx.ui.mirror.DeviceMirrorPage
import io.github.recrivenvi.connectorx.ui.remote.ControlPage
import io.github.recrivenvi.connectorx.ui.remote.RemoteApplicationPage
import io.github.recrivenvi.connectorx.ui.remote.RemoteAppsPage

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.protocol.Wire
import top.yukonga.miuix.kmp.basic.*
import io.github.recrivenvi.connectorx.ui.theme.ConnectorTheme
import io.github.recrivenvi.connectorx.ui.theme.readThemeConfig
import io.github.recrivenvi.connectorx.ui.theme.resolveIsDark
import io.github.recrivenvi.connectorx.ui.navigation.AppShell
import io.github.recrivenvi.connectorx.platform.PlatformStorage
import io.github.recrivenvi.connectorx.platform.LocalBackSettings
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import top.yukonga.miuix.kmp.window.WindowDialog
import io.github.recrivenvi.connectorx.protocol.AppRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val engine get() = (application as ConnectorApp).engine
    override fun onStart() {
        super.onStart()
        runCatching { engine.runtime.enable() }.onFailure { engine.message("自动连接 Shizuku 失败：${it.message}") }
        runCatching { startForegroundService(Intent(this, HostService::class.java)) }
            .onFailure { engine.message("自动开启接收失败：${it.message}") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val storage=remember { PlatformStorage(applicationContext) }
            var themeConfig by remember { mutableStateOf(readThemeConfig(storage)) }
            val shellState=rememberSaveableStateHolder()
            val dark = themeConfig.resolveIsDark(isSystemInDarkTheme())
            val remoteApp = engine.remote?.app != null
            DisposableEffect(dark, remoteApp) {
                val bars = if (remoteApp) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(bars, bars)
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
                if (remoteApp) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                onDispose {}
            }
            ConnectorTheme(themeConfig) {
                val session = engine.remote
                when {
                    session?.deviceMirror == true -> DeviceMirrorPage(engine, session)
                    session?.app != null -> RemoteApplicationPage(engine, session)
                    session != null -> ControlPage(engine, session)
                    else -> shellState.SaveableStateProvider("main-shell") {
                        AppShell(androidDevices(engine,::toggleHost),storage,themeConfig,{ themeConfig=it },
                            predictiveBack=if(Build.VERSION.SDK_INT>=34) { { enabled ->
                                LocalBackSettings.apply(applicationInfo,enabled)
                                recreate()
                            } } else null,
                            media=io.github.recrivenvi.connectorx.ui.screen.settings.MediaSettingsModel(engine.mediaOptions.values(),io.github.recrivenvi.connectorx.protocol.MediaOptions.defaults().values(),engine::saveMedia),
                            catalogVisible=engine.catalog!=null,closeCatalog=engine::closeCatalog,
                            backHandler={enabled,action->androidx.activity.compose.BackHandler(enabled,action)},
                            catalogContent={ engine.catalog?.let { catalog ->
                                RemoteAppsPage(engine,catalog,
                                    launch={ app -> launchApp(catalog.peer,catalog.host,app) },
                                    pin={ app -> runCatching { RemoteShortcuts.request(this,catalog.peer,app,engine::message) }
                                        .onFailure { engine.message("快捷方式失败：${it.message}") } })
                            } })
                    }
                }
                engine.castApps?.let { catalog ->
                    WindowDialog(show=true,title="投送本机应用到 ${catalog.peer.name}",onDismissRequest={engine.dismissCastApps()}) {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=480.dp)) {
                            items(catalog.apps.size) { index -> val app=catalog.apps[index]
                                top.yukonga.miuix.kmp.preference.ArrowPreference(title=app.name,summary=app.packageName,onClick={
                                    engine.cast(catalog.peer,catalog.host,app.packageName);engine.dismissCastApps()
                                })
                            }
                        }
                    }
                }
                engine.controlError?.let { reason ->
                    WindowDialog(show = true, title = "无法开始远程控制", onDismissRequest = { engine.dismissControlError() }) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(reason)
                            TextButton("知道了", { engine.dismissControlError() }, Modifier.fillMaxWidth())
                        }
                    }
                }
                engine.prompt?.let { prompt ->
                    WindowDialog(show = true, title = "与 ${prompt.name} 配对", onDismissRequest = { prompt.response.complete(false) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("请核对两台设备显示的校验码完全一致。确认后，对方可查看并控制本机屏幕。")
                            Text(prompt.code)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton("取消", { prompt.response.complete(false) }, Modifier.weight(1f))
                                TextButton("一致，允许配对", { prompt.response.complete(true) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) handleShortcut(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShortcut(intent) }
    private fun forwardRemoteKey(event: KeyEvent): Boolean {
        val session = engine.remote
        val localKeys = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)
        if (session?.app != null && session.running && !session.localUiActive && engine.prompt == null && engine.controlError == null
            && event.keyCode !in localKeys && event.device?.isVirtual == false && event.action in 0..1) {
            session.send(Wire.key(event.action, event.keyCode, event.repeatCount, event.metaState))
            return true
        }
        return false
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = forwardRemoteKey(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = forwardRemoteKey(event) || super.onKeyUp(keyCode, event)
    private fun profile(packageName: String): AppRequest {
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val insets = if (Build.VERSION.SDK_INT >= 30)
            androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(windowManager.currentWindowMetrics.windowInsets)
        else androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
        val layout = AppLayoutPreferences.load(this, packageName)
        val cutoutTypes = if (layout.underStatus || resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 0
            else androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        val types = androidx.core.view.WindowInsetsCompat.Type.captionBar() or cutoutTypes or
            (if (layout.underStatus) 0 else androidx.core.view.WindowInsetsCompat.Type.statusBars()) or
            (if (layout.underNavigation) 0 else androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        val reserved = insets?.getInsetsIgnoringVisibility(types) ?: androidx.core.graphics.Insets.NONE
        @Suppress("DEPRECATION")
        val rate = (if (Build.VERSION.SDK_INT >= 30) display?.refreshRate else windowManager.defaultDisplay.refreshRate)
            ?.takeIf { it.isFinite() && it >= 24 }?.coerceAtMost(240f) ?: 60f
        return AppRequest(packageName, bounds.width() - reserved.left - reserved.right,
            bounds.height() - reserved.top - reserved.bottom, metrics.densityDpi, rate, layout.systemDecorations)
    }
    private fun launchApp(peer: Peer, host: String, app: RemoteApp) {
        runCatching { engine.control(peer, host, profile(app.packageName), app.name) }
            .onFailure { engine.message("无法打开远程应用：${it.message}") }
    }
    private fun handleShortcut(intent: Intent) {
        if (intent.action != RemoteShortcuts.ACTION) return
        runCatching {
            val target = RemoteShortcuts.resolve(this, intent) ?: error("快捷方式记录不存在，请在应用中重新添加")
            val peer = engine.identity.peers().firstOrNull { it.id == target.peerId } ?: error("设备已解除配对，请先重新配对")
            if (engine.remote?.let { it.running && it.peer.id == peer.id && it.app?.packageName == target.packageName } == true) return
            lifecycleScope.launch {
                engine.discover()
                val deadline = android.os.SystemClock.elapsedRealtime() + 2000
                while (engine.candidates.none { it.id == peer.id } && android.os.SystemClock.elapsedRealtime() < deadline) delay(100)
                val host = engine.candidates.firstOrNull { it.id == peer.id }?.host ?: peer.host
                launchApp(peer, host, RemoteApp(target.packageName, target.name))
            }
        }.onFailure { engine.message("快捷方式无法打开：${it.message}") }
    }
    private fun toggleHost() {
        if (engine.hosting) engine.stopHost()
        else {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            startForegroundService(Intent(this, HostService::class.java))
        }
    }
}

