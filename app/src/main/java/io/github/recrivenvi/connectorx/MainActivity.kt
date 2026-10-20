package io.github.recrivenvi.connectorx

import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.connection.RemoteApp
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.shortcuts.RemoteShortcuts
import io.github.recrivenvi.connectorx.ui.devices.androidDevices
import io.github.recrivenvi.connectorx.ui.devices.androidLibrary
import io.github.recrivenvi.connectorx.ui.devices.androidAutomation
import io.github.recrivenvi.connectorx.ui.remote.ApplicationSettingsDialog
import io.github.recrivenvi.connectorx.ui.mirror.DeviceMirrorPage
import io.github.recrivenvi.connectorx.ui.remote.ControlPage
import io.github.recrivenvi.connectorx.ui.remote.RemoteApplicationPage
import io.github.recrivenvi.connectorx.ui.remote.RemoteAppsPage
import io.github.recrivenvi.connectorx.ui.remote.LocalApplicationPage

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import io.github.recrivenvi.connectorx.overlay.GlobalScrollService
import io.github.recrivenvi.connectorx.overlay.GlobalScrollState
import io.github.recrivenvi.connectorx.ui.library.GlobalScrollModel

class MainActivity : ComponentActivity() {
    private val engine get() = (application as ConnectorApp).engine
    private var overlayPermission by mutableStateOf(false)
    private var pendingOverlay=false
    override fun onResume() {
        super.onResume()
        overlayPermission=Settings.canDrawOverlays(this)
        if(pendingOverlay) {
            pendingOverlay=false
            if(overlayPermission)openGlobalScroll()
        }
    }
    private fun openGlobalScroll() {
        if(GlobalScrollState.visible)return
        runCatching {
            overlayPermission=Settings.canDrawOverlays(this)
            if(!overlayPermission) {
                pendingOverlay=true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            } else if(engine.runtime.service==null) {
                GlobalScrollState.error="请先连接 Shizuku，再开启悬浮窗"
                engine.runtime.enable()
            } else startForegroundService(Intent(this,GlobalScrollService::class.java))
        }.onFailure {pendingOverlay=false;GlobalScrollState.error=it.message?:"无法开启悬浮窗"}
    }
    private fun globalScrollModel()=GlobalScrollModel(overlayPermission,GlobalScrollState.visible,GlobalScrollState.direction,
        GlobalScrollState.error?:when {
            GlobalScrollState.visible&&!GlobalScrollState.ready->"正在连接权限服务…"
            GlobalScrollState.direction>0->"正在上滚"
            GlobalScrollState.direction<0->"正在下滚"
            GlobalScrollState.visible->"滚动已停止"
            !overlayPermission->"允许 ConnectorX 显示在其他应用上层"
            engine.runtime.service==null->"需要已运行的 Shizuku"
            else->"在其他应用上显示上滚、停止、下滚控件"
        },::openGlobalScroll,
        {if(GlobalScrollState.visible)startService(Intent(this,GlobalScrollService::class.java).setAction(GlobalScrollService.ACTION_STOP))},
        {stopService(Intent(this,GlobalScrollService::class.java))})
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
            var appSettings by remember {mutableStateOf<RemoteApp?>(null)}
            LaunchedEffect(engine.runtime.service) {
                if(engine.runtime.service!=null&&engine.appCatalogs[engine.identity.id]==null)engine.showLocalApps(navigate=false)
            }
            val shellState=rememberSaveableStateHolder()
            val dark = themeConfig.resolveIsDark(isSystemInDarkTheme())
            val remoteApp = !engine.showingLocal && engine.remote?.app != null
            val localMode=engine.showingLocal
            DisposableEffect(localMode) {
                val previous=window.attributes.softInputMode
                val imeFlag=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                val previousImeFlag=window.attributes.flags and imeFlag
                if(localMode) {
                    // This window displays a different Display's editor/IME. A physical DOWN must
                    // not start an empty host InputConnection and dismiss that editor's keyboard.
                    // Keep normal window focus, touch and Back delivery; do not focus another task.
                    window.addFlags(imeFlag)
                    window.setSoftInputMode(
                        (previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                }
                onDispose {if(localMode) {
                    window.setFlags(previousImeFlag,imeFlag)
                    window.setSoftInputMode(previous)
                }}
            }
            DisposableEffect(dark, remoteApp, localMode) {
                val bars = if (remoteApp) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(bars, bars)
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
                if (remoteApp || localMode) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                onDispose {}
            }
            ConnectorTheme(themeConfig) {
                val session = engine.remote
                when {
                    engine.showingLocal && engine.localSession!=null -> LocalApplicationPage(engine,engine.localSession!!)
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
                            library=androidLibrary(engine,::launchApp,{appSettings=it},{peer,app->
                                runCatching {RemoteShortcuts.request(this,peer,app,engine::message)}.onFailure {engine.message("快捷方式失败：${it.message}")}
                            }),
                            automation=androidAutomation(engine,globalScrollModel()) {app,direction->
                                runCatching {engine.control(engine.localPeer(),"",profile(app.packageName,true),app.name,initialScroll=direction)}
                                    .onFailure {engine.message("无法开始滚动：${it.message}")}
                            },
                            backHandler={enabled,action->androidx.activity.compose.BackHandler(enabled,action)})
                    }
                }
                appSettings?.let {app->ApplicationSettingsDialog(app){appSettings=null}}
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
        val local = engine.localSession?.takeIf {engine.showingLocal}
        val localKeys = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)
        if(local!=null&&local.running&&!local.localUiActive&&engine.prompt==null&&engine.controlError==null
            &&event.keyCode !in localKeys&&event.device?.isVirtual==false&&event.action in 0..1) {
            local.key(event.action,event.keyCode,event.repeatCount,event.metaState)
            return true
        }
        if (session?.app != null && session.running && !session.localUiActive && engine.prompt == null && engine.controlError == null
            && event.keyCode !in localKeys && event.device?.isVirtual == false && event.action in 0..1) {
            session.send(Wire.key(event.action, event.keyCode, event.repeatCount, event.metaState))
            return true
        }
        return false
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = forwardRemoteKey(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = forwardRemoteKey(event) || super.onKeyUp(keyCode, event)
    private fun profile(packageName: String, fullDisplay: Boolean = false): AppRequest {
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
        val reserved = if(fullDisplay) androidx.core.graphics.Insets.NONE
            else insets?.getInsetsIgnoringVisibility(types) ?: androidx.core.graphics.Insets.NONE
        @Suppress("DEPRECATION")
        val rate = (if (Build.VERSION.SDK_INT >= 30) display?.refreshRate else windowManager.defaultDisplay.refreshRate)
            ?.takeIf { it.isFinite() && it >= 24 }?.coerceAtMost(240f) ?: 60f
        val mode=display?.mode
        val modePortrait=(mode?.physicalWidth?:bounds.width())<=(mode?.physicalHeight?:bounds.height())
        val boundsPortrait=bounds.width()<=bounds.height()
        val fullWidth=when {mode==null->bounds.width();modePortrait==boundsPortrait->mode.physicalWidth;else->mode.physicalHeight}
        val fullHeight=when {mode==null->bounds.height();modePortrait==boundsPortrait->mode.physicalHeight;else->mode.physicalWidth}
        return AppRequest(packageName, (if(fullDisplay)fullWidth else bounds.width()) - reserved.left - reserved.right,
            (if(fullDisplay)fullHeight else bounds.height()) - reserved.top - reserved.bottom, metrics.densityDpi, rate, layout.systemDecorations)
    }
    private fun launchApp(peer: Peer, host: String, app: RemoteApp, localControl:Boolean=false) {
        runCatching { engine.control(peer, host, profile(app.packageName,localControl), app.name) }
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
