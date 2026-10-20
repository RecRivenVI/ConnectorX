package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.connection.RemoteApp
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ApplicationSettingsDialog(app:RemoteApp,onDismiss:()->Unit) {
    val context=LocalContext.current
    var value by remember(app.packageName) {mutableStateOf(AppLayoutPreferences.load(context,app.packageName))}
    fun save(next:AppLayoutPreferences){value=next;next.save(context,app.packageName)}
    WindowDialog(show=true,title=app.name,onDismissRequest=onDismiss) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("按包名保存，跨设备共用。正在使用的会话保持不变。")
            SwitchPreference(title="延伸至状态栏",summary="独立应用画面延伸到本机状态栏下方",checked=value.underStatus,onCheckedChange={save(value.copy(underStatus=it))})
            SwitchPreference(title="延伸至导航栏",summary="独立应用画面延伸到本机导航栏下方",checked=value.underNavigation,onCheckedChange={save(value.copy(underNavigation=it))})
            SwitchPreference(title="显示目标设备系统栏",summary="下次新建虚拟显示时生效",checked=value.systemDecorations,onCheckedChange={save(value.copy(systemDecorations=it))})
            TextButton("完成",onDismiss,Modifier.fillMaxWidth())
        }
    }
}
