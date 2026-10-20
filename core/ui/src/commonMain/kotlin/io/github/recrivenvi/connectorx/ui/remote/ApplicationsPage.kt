package io.github.recrivenvi.connectorx.ui.remote
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.ui.component.AdaptiveTopAppBar
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.util.horizontalCutoutPadding
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

data class ApplicationItem(val packageName:String,val name:String)

@Composable
fun ApplicationsPage(deviceName:String, apps:List<ApplicationItem>, status:String, enabled:Boolean, onBack:()->Unit, launch:(ApplicationItem)->Unit, pin:((ApplicationItem)->Unit)?=null, localControl:Boolean=false) {
    val scroll=MiuixScrollBehavior()
    val backdrop=rememberBlurBackdrop()
    var query by remember(deviceName) { mutableStateOf("") }
    var chosen by remember { mutableStateOf<ApplicationItem?>(null) }
    Scaffold(topBar = { BlurredBar(backdrop) {
        AdaptiveTopAppBar(title=if(localControl) "本机应用控制" else "远程应用",color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,scrollBehavior=scroll,
            navigationIcon={ IconButton(onClick={ onBack() }) { Icon(MiuixIcons.Back,"返回",tint=MiuixTheme.colorScheme.onSurface) } })
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().horizontalCutoutPadding().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier)
            .scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding=PaddingValues(top=padding.calculateTopPadding(),start=12.dp,end=12.dp)) {
            item { Spacer(Modifier.height(12.dp)) }
            item { TextField(value = query, onValueChange = { query = it }, label = "搜索应用或包名", modifier = Modifier.fillMaxWidth().padding(bottom=12.dp)) }
            item { Text(status, modifier = Modifier.padding(vertical = 6.dp)) }
            items(apps.filter { query.isBlank() || it.name.contains(query, true) || it.packageName.contains(query, true) }, key = { it.packageName }) { app ->
                Card(Modifier.padding(bottom=12.dp)) {
                    BasicComponent(title = app.name, summary = app.packageName,
                        enabled = enabled, onClick = { chosen = app })
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }
    chosen?.let { app ->
        WindowDialog(show = true, title = app.name, onDismissRequest = { chosen = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("在 ${deviceName} 的独立虚拟显示中运行。")
                TextButton(if(localControl) "打开并控制" else "打开远程应用", { chosen = null; launch(app) }, Modifier.fillMaxWidth())
                if(pin!=null) TextButton("添加到桌面", { pin(app); chosen = null }, Modifier.fillMaxWidth())
            }
        }
    }
}
