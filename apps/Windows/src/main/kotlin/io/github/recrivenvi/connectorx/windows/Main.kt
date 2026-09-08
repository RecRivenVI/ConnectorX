package io.github.recrivenvi.connectorx.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.recrivenvi.connectorx.ui.theme.*
import io.github.recrivenvi.connectorx.ui.navigation.AppShell
import io.github.recrivenvi.connectorx.ui.remote.*

fun main(args:Array<String>) {
    check(System.getProperty("os.name").startsWith("Windows")&&System.getProperty("os.arch") in listOf("amd64","x86_64")) { "此客户端仅支持 Windows x64" }
    val engine=DesktopEngine()
    val instance=DesktopInstance(engine.identity.directory)
    val command=if(args.size==2&&args[0]=="--open") args[1] else ""
    if(!instance.primary) {instance.forward(command);instance.close();engine.close();return}
    var mainWindow:java.awt.Window?=null
    fun activate(command:String) {engine.ui {
        mainWindow?.let {if(it is java.awt.Frame) it.extendedState=java.awt.Frame.NORMAL;it.toFront()}
        if(command.isNotEmpty()) engine.openShortcut(command)
    }}
    instance.listen(::activate)
    val storage=WindowsAppearanceStorage(engine.identity.directory)
    var theme by mutableStateOf(readThemeConfig(storage))
    engine.start()
    application {
        Window(onCloseRequest={ engine.close(); instance.close(); exitApplication() },title="ConnectorX · Windows 控制端",state=rememberWindowState(width=960.dp,height=780.dp)) {
            SideEffect {mainWindow=window}
            LaunchedEffect(Unit) {if(command.isNotEmpty()) engine.openShortcut(command)}
            ConnectorTheme(theme) {
                AppShell(windowsDevices(engine),storage,theme,{theme=it},predictiveBack=null,
                    media=io.github.recrivenvi.connectorx.ui.screen.settings.MediaSettingsModel(engine.mediaOptions.values(),io.github.recrivenvi.connectorx.protocol.MediaOptions.defaults().values(),engine::saveMedia),
                    catalogVisible=engine.apps!=null,closeCatalog=engine::dismissApps,
                    catalogContent={engine.apps?.let { (peer,apps) ->
                        ApplicationsPage(peer.name,apps.map {ApplicationItem(it.packageName,it.name)},engine.status,engine.connectingPeerId==null,
                            engine::dismissApps,launch={engine.control(peer,it.packageName)},pin={engine.pin(peer,DesktopApp(it.packageName,it.name))})
                    }})
            }
        }
        engine.sessions.filter { it.running }.forEach { session -> key(session) {
            val screenScale=remember {java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX}
            val workArea=remember {java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds}
            val initialWidth=remember(session) {(session.app?.width?.div(screenScale)?.toInt() ?: 600).coerceIn(360,workArea.width.coerceAtLeast(360))}
            val initialHeight=remember(session) {(session.app?.height?.div(screenScale)?.toInt()?.plus(120) ?: 900).coerceIn(360,workArea.height.coerceAtLeast(360))}
            Window(onCloseRequest={ session.close() },title="${session.peer.name} · ${session.app?.packageName ?: "设备镜像"}",
                state=rememberWindowState(width=initialWidth.dp,height=initialHeight.dp)) {
                SideEffect { session.window=window }
                ConnectorTheme(theme) {DesktopSessionPage(engine,session)}
            }
        } }
        engine.controlError?.let { reason ->
            Window(onCloseRequest=engine::dismissError,title="ConnectorX · 操作未完成",state=rememberWindowState(width=540.dp,height=280.dp)) {
                ConnectorTheme(theme) {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(reason);TextButton("知道了",engine::dismissError)
                }}
            }
        }
        engine.approval?.let { a ->
            Window(onCloseRequest={a.result.complete(false)},title=a.title,alwaysOnTop=true,state=rememberWindowState(width=520.dp,height=270.dp)) {
                ConnectorTheme(theme) { Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text(a.title); Text(a.detail)
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        TextButton("接受",{a.result.complete(true)}); TextButton("拒绝",{a.result.complete(false)})
                    }
                } }
            }
        }
    }
}
