package io.github.recrivenvi.connectorx.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.ui.component.*
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun LibraryScaffold(title:String,bottom:Dp,content:LazyListScope.()->Unit) {
    val scroll=MiuixScrollBehavior();val backdrop=rememberBlurBackdrop()
    Scaffold(topBar={BlurredBar(backdrop) {AdaptiveTopAppBar(title,color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,scrollBehavior=scroll)}}) {padding->
        WideContentBox {side->
            LazyColumn(Modifier.fillMaxSize().then(if(backdrop!=null)Modifier.layerBackdrop(backdrop)else Modifier)
                .scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
                contentPadding=PaddingValues(start=side,end=side,top=padding.calculateTopPadding(),bottom=bottom+24.dp),content=content)
        }
    }
}

@Composable
private fun ApplicationRow(app:LibraryApplication,onMore:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        BasicComponent(modifier=Modifier.weight(1f),title=app.name,summary=app.deviceName,enabled=app.enabled,onClick=app.launch,
            startAction={Box(Modifier.padding(end=12.dp).size(40.dp).background(MiuixTheme.colorScheme.secondaryContainer,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
                Text(app.name.take(1),color=MiuixTheme.colorScheme.onSecondaryContainer)
            }})
        IconButton(onClick=onMore,modifier=Modifier.padding(end=8.dp)) {Icon(MiuixIcons.More,"${app.name} 的选项",tint=MiuixTheme.colorScheme.onSurfaceContainer)}
    }
}

@Composable
fun LibraryPage(model:LibraryModel,bottom:Dp) {
    var query by rememberSaveable(model.selectedSource) {mutableStateOf("")}
    var sources by remember {mutableStateOf(false)}
    var chosen by remember {mutableStateOf<LibraryApplication?>(null)}
    LibraryScaffold("应用",bottom) {
        if(model.sessions.isNotEmpty()) {
            item {SmallTitle("继续使用")}
            groupedCardItems("sessions",model.sessions.map {s->CardItem(s.id) {
                ArrowPreference(title=s.summary,summary="${s.name} · 会话保持中",onClick={s.actions.firstOrNull {it.key=="resume"}?.invoke?.invoke()})
            }})
        }
        if(model.favorites.isNotEmpty()) {
            item {SmallTitle("常用应用")}
            groupedCardItems("favorites",model.favorites.map {app->CardItem(app.key) {ApplicationRow(app){chosen=app}}})
        }
        if(model.recent.isNotEmpty()) {
            item {SmallTitle("最近使用")}
            groupedCardItems("recent",model.recent.take(4).map {app->CardItem(app.key) {ApplicationRow(app){chosen=app}}})
        }
        item {SmallTitle("应用目录")}
        groupedCardItems("source",listOf(CardItem("picker") {
            ArrowPreference(title=model.sources.firstOrNull {it.id==model.selectedSource}?.name?:"选择设备",summary=if(model.loading)"正在读取应用…" else "切换本机或已配对设备",onClick={sources=true})
        }))
        item {TextField(query,{query=it},label="搜索应用或包名",modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=12.dp))}
        if(!model.loaded) groupedCardItems("load",listOf(CardItem("state") {
            BasicComponent(title=if(model.loading)"正在读取应用" else "读取这台设备的应用",summary=model.status)
        },CardItem("action") {TextButton(if(model.loading)"正在读取…" else "读取应用列表",model.refresh,Modifier.fillMaxWidth().padding(12.dp),enabled=!model.loading)}))
        if(model.enablePrivilege!=null) groupedCardItems("permission",listOf(CardItem("enable") {
            ArrowPreference(title="连接 Shizuku",summary="本机应用控制需要已运行的权限服务",onClick=model.enablePrivilege)
        }))
        val filtered=model.applications.filter {query.isBlank()||it.name.contains(query,true)||it.packageName.contains(query,true)}
        groupedCardItems("catalog",filtered.map {app->CardItem(app.key) {ApplicationRow(app){chosen=app}}})
        if(model.loaded&&filtered.isEmpty()) item {BasicComponent(title="没有匹配的应用",summary="可以更换搜索词或刷新列表")}
        if(model.loaded) item {TextButton(if(model.loading)"正在刷新…" else "刷新应用列表",model.refresh,Modifier.fillMaxWidth().padding(horizontal=12.dp),enabled=!model.loading)}
    }
    WindowDialog(show=sources,title="选择设备",onDismissRequest={sources=false}) {
        Column {model.sources.forEach {source->ArrowPreference(title=source.name,summary=source.summary,onClick={sources=false;source.select()})}}
    }
    chosen?.let {app->WindowDialog(show=true,title=app.name,onDismissRequest={chosen=null}) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${app.deviceName}\n${app.packageName}")
            TextButton(if(app.favorite)"取消收藏" else "加入常用应用",{chosen=null;app.toggleFavorite()},Modifier.fillMaxWidth())
            TextButton("应用显示设置",{chosen=null;app.settings()},Modifier.fillMaxWidth())
            app.pin?.let {pin->TextButton("添加到桌面",{chosen=null;pin()},Modifier.fillMaxWidth())}
        }
    }}
}

@Composable
fun AutomationPage(model:AutomationModel,bottom:Dp) {
    var picking by remember {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    LibraryScaffold("自动化",bottom) {
        model.global?.let {global->
            item {SmallTitle("全局悬浮控制")}
            groupedCardItems("global-scroll",buildList {
                add(CardItem("open") {ArrowPreference(
                    title=if(global.visible)"悬浮窗已开启" else if(global.permissionGranted)"开启悬浮窗" else "授予悬浮窗权限",
                    summary=global.status,onClick=global.open)})
                if(global.visible)add(CardItem("actions") {Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton("停止滚动",global.stop,Modifier.weight(1f),enabled=global.direction!=0)
                    TextButton("退出悬浮窗",global.close,Modifier.weight(1f))
                }})
            })
            item {BasicComponent(title="在当前应用中滚动",summary="滚动落点位于屏幕中央。点击或按住滑过上滚、停止、下滚三个选项，松手保持；再次点击当前方向或点击中间停止。顶部横线拖动，下方胶囊退出。锁屏停止并保留悬浮窗。")}
        }
        item {SmallTitle("本机连续滚动")}
        groupedCardItems("scroll",listOf(CardItem("target") {
            ArrowPreference(title=model.targetName?:"选择目标应用",summary="在本机独立窗口中运行 · 记住本次选择",onClick={picking=true;if(model.targets.isEmpty())model.refresh()})
        },CardItem("start") {Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton("开始上滚",{model.start(1)},Modifier.weight(1f),enabled=model.enabled)
            TextButton("开始下滚",{model.start(-1)},Modifier.weight(1f),enabled=model.enabled)
        }}))
        item {BasicComponent(title="持续滚动",summary="固定速度，落点位于应用画面中央。进入画面后可再次点击停止或切换方向；离开画面会停止滚动。")}
        if(model.activeTitle!=null) {
            item {SmallTitle("当前本机会话")}
            groupedCardItems("active",listOf(CardItem("resume") {
                ArrowPreference(title=model.activeTitle,summary=when {model.direction>0->"正在上滚";model.direction<0->"正在下滚";else->"滚动已停止"},onClick=model.resume)
            },CardItem("stop") {TextButton("停止滚动",model.stop,Modifier.fillMaxWidth().padding(12.dp),enabled=model.direction!=0)}))
        }
        if(model.status.isNotBlank()) item {BasicComponent(title="运行条件",summary=model.status)}
        item {SmallTitle("后续能力")}
        item {BasicComponent(title="动作录制与宏编辑",summary="尚未实现；当前仅提供本机持续滚动。")}
    }
    WindowDialog(show=picking,title="选择本机应用",onDismissRequest={picking=false}) {
        Column {
            TextField(query,{query=it},label="搜索应用",modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                items(model.targets.filter {it.name.contains(query,true)||it.packageName.contains(query,true)},key={it.packageName}) {app->
                    ArrowPreference(title=app.name,onClick={app.select();picking=false})
                }
            }
            TextButton(if(model.loading)"正在读取…" else "刷新本机列表",model.refresh,Modifier.fillMaxWidth(),enabled=!model.loading)
        }
    }
}
