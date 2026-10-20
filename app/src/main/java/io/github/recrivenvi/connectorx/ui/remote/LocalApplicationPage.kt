package io.github.recrivenvi.connectorx.ui.remote

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.recrivenvi.connectorx.R
import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import io.github.recrivenvi.connectorx.session.LocalAppSession
import io.github.recrivenvi.connectorx.session.LocalScrollSpeed
import io.github.recrivenvi.connectorx.ui.mirror.DeviceMirrorView
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Controls an application running on this device in an isolated VirtualDisplay. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LocalApplicationPage(engine:ConnectorEngine,session:LocalAppSession) {
    var menu by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val density=LocalDensity.current
    DisposableEffect(session) {onDispose {session.resetImeBack()}}
    val packageName=session.app.packageName
    var layout by remember(packageName) {mutableStateOf(AppLayoutPreferences.load(context,packageName))}
    fun save(value:AppLayoutPreferences) {layout=value;value.save(context,packageName)}
    DisposableEffect(session,menu,session.imeBackError) {
        session.localUiActive=menu||session.imeBackError!=null
        onDispose {session.localUiActive=false}
    }
    LaunchedEffect(session,menu,session.running) {
        while(menu&&session.running) {
            session.samplePerformance()
            delay(1000)
        }
    }
    BackHandler(enabled=!menu&&session.imeBackError==null&&engine.prompt==null&&engine.controlError==null) {
        if(session.running)session.back() else engine.disconnect()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        val border=with(density){4.dp.toPx()}
        val status=WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat()
        val navigation=WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toFloat()
        // Fit the largest proportional frame within the button zones and side margins, then
        // center it in the whole window. Recompute from live viewport and display dimensions.
        val sideSpace=with(density){LocalScrollLayout.MirrorSideSpace.toPx()}
        val margin=maxOf(status+with(density){56.dp.toPx()},navigation+with(density){64.dp.toPx()})+with(density){8.dp.toPx()}
        val sourceWidth=(session.width.takeIf {it>0}?:session.app.width).toFloat()
        val sourceHeight=(session.height.takeIf {it>0}?:session.app.height).toFloat()
        val fitted=ScreenMath.fit((with(density){maxWidth.toPx()}-2*sideSpace).coerceAtLeast(0f),
            with(density){maxHeight.toPx()},margin,border,sourceWidth,sourceHeight)
        if(fitted[0]>0&&fitted[1]>0) {
            AndroidView(
                modifier=Modifier.align(Alignment.Center).width(with(density){(fitted[0]+2*border).toDp()})
                    .height(with(density){(fitted[1]+2*border).toDp()}),
                factory={DeviceMirrorView(it,session,border)},
                update={it.geometry=session.geometry})
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal=16.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton("功能",{menu=true},Modifier.width(66.dp).height(48.dp),insideMargin=PaddingValues(0.dp))
            Text(session.appTitle,Modifier.weight(1f),textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            TextButton("退出",{engine.disconnect()},Modifier.width(66.dp).height(48.dp),insideMargin=PaddingValues(0.dp))
        }
        val railHeight=minOf(LocalScrollLayout.PreferredHeight,
            (maxHeight-with(density){(2*margin).toDp()}).coerceAtLeast(0.dp))
        val frameWidth=with(density){(fitted[0]+2*border).toDp()}
        val rightGutterWidth=((maxWidth-frameWidth)/2).coerceAtLeast(0.dp)
        if(railHeight>=LocalScrollLayout.MinimumHeight) LocalScrollControl(
            direction=session.scrollDirection,enabled=session.running&&!session.inputBlocked&&!session.inputOverloaded,
            onDirectionChange=session::selectScrollDirection,rightGutterWidth=rightGutterWidth,
            modifier=Modifier.align(AbsoluteAlignment.CenterRight).height(railHeight))
        fun navigate(key:Int) {
            session.selectScrollDirection(0)
            session.cancelTouches()
            session.key(key)
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal=34.dp),
            horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            LocalNavigationButton(R.drawable.ic_aosp_recent,"更多：最近任务",session.running&&!session.inputBlocked) {
                navigate(KeyEvent.KEYCODE_APP_SWITCH)
            }
            LocalNavigationButton(R.drawable.ic_aosp_home,"桌面",session.running&&!session.inputBlocked) {
                navigate(KeyEvent.KEYCODE_HOME)
            }
            LocalNavigationButton(R.drawable.ic_aosp_back,"返回",session.running) {session.back()}
        }
        if(!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("本机应用控制会话已结束")
                Text(engine.status)
                TextButton("返回设备",{engine.disconnect()},Modifier.fillMaxWidth())
            }
        }
    }
    session.imeBackError?.let {message->
        WindowDialog(show=true,title="无法收起键盘",onDismissRequest=session::dismissImeBackError) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(message)
                TextButton("知道了",session::dismissImeBackError,Modifier.fillMaxWidth())
            }
        }
    }
    WindowDialog(show=menu,title=session.appTitle,onDismissRequest={menu=false}) {
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("本机独立应用模式 · 始终使用本机完整分辨率，不提供主屏镜像")
            Text("点击或按住滑过右侧上滚、停止、下滚三个选项，松手保持选择；再次点击当前滚动方向或点击中间停止。离开画面或重启应用后停止。")
            Text("上下滚动均固定为最高速度：每秒 ${LocalScrollSpeed.UNITS_PER_SECOND.toInt()} 个原生滚轮单位，落点固定在画面中央。实际移动距离由目标应用决定。")
            Text(if(session.scrollDirection==0)"当前：停止" else
                "当前：${if(session.scrollDirection>0)"上滚" else "下滚"} · 最高速度")
            val info=session.info
            Text("直连 Surface · 无编码、无解码、无网络\nVirtualDisplay ${info.optInt("displayId",-1)} · ${info.optInt("width")}×${info.optInt("height")} / ${info.optInt("dpi")}dpi · rotation=${info.optInt("rotation",-1)}")
            LocalPerformance(session.performance)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("显示虚拟屏系统栏")
                Switch(checked=layout.systemDecorations,onCheckedChange={save(layout.copy(systemDecorations=it))})
            }
            Text("按包名保存，下次新建本机应用会话生效。")
            TextButton(if(session.restarting)"正在强制重启…" else "强制重启应用",{session.restart()},
                Modifier.fillMaxWidth(),enabled=session.running&&!session.restarting)
            session.restartMessage?.let {Text(it)}
            session.error?.let {Text(it)}
            TextButton("继续使用",{menu=false},Modifier.fillMaxWidth())
            TextButton("返回 ConnectorX",{menu=false;engine.showLibrary()},Modifier.fillMaxWidth())
            TextButton("退出本机应用",{menu=false;engine.disconnect()},Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LocalNavigationButton(icon:Int,label:String,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick,Modifier.width(64.dp).height(52.dp).semantics {if(!enabled)disabled()},enabled=enabled) {
        Icon(painterResource(icon),label,Modifier.size(22.dp),
            tint=MiuixTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else 0.38f))
    }
}

@Composable
private fun LocalPerformance(stats:JSONObject) {
    if(stats.has("unavailable")) {Text("性能统计暂不可用：${stats.optString("unavailable")}");return}
    if(!stats.has("queueDepth")) {Text("正在读取本机会话性能…");return}
    fun number(key:String,scale:Double=1.0)=if(stats.has(key)) String.format(Locale.ROOT,"%.2f",stats.optDouble(key)/scale) else "—"
    Text("本机诊断 · 每秒更新")
    val imePolicy=when(stats.optInt("imePolicyReported",-1)) {
        0 -> "虚拟屏内"
        1 -> "本机主屏（系统回退）"
        2 -> "隐藏"
        else -> "系统未确认"
    }
    Text("输入法策略：$imePolicy · 目标 Display ${stats.optInt("displayId",-1)}")
    Text("物理屏 ${number("physicalRefreshRate")} Hz · 虚拟屏 ${number("effectiveRefreshRate")} Hz\n"+
        "滚动调度 ${number("schedulerRefreshRate")} Hz · Surface 帧率提示 ${if(stats.optBoolean("surfaceRateHintApplied"))"已提交" else "不可用"}")
    Text("虚拟屏刷新策略：${if(stats.optBoolean("followPhysicalRequestApplied"))"请求跟随物理屏" else "平台默认"}")
    Text("输入队列 ${stats.optInt("queueDepth")}/${stats.optInt("queueCapacity")} · 合并 MOVE ${stats.optLong("queueMerged")}\n"+
        "排队平均 / 最大 ${number("queueAverageMs")} / ${number("queueMaxMs")} ms\n"+
        "Binder 平均 / 最大 ${number("binderAverageMs")} / ${number("binderMaxMs")} ms")
    Text("最近采样至注入 ${number("inputSampleAgeNanos",1e6)} ms · 最大 ${number("inputMaxSampleAgeNanos",1e6)} ms\n"+
        "最近注入调用 ${number("injectDurationNanos",1e6)} ms · 最大 ${number("injectMaxDurationNanos",1e6)} ms\n"+
        "注入调用 ${stats.optLong("inputEvents")} · 失败 ${stats.optLong("injectFailures")} · Binder 异常 ${stats.optLong("binderFailures")}\n"+
        "拒绝异常触摸帧 ${stats.optLong("rejectedTouchFrames")} · 活动触点 ${stats.optInt("activePointers")}")
    Text("滚动次数 ${stats.optLong("scrollTicks")} · 错过调度 ${stats.optLong("scrollSkippedTicks")}\n"+
        "最大调度偏差 ${number("scrollMaxLatenessNanos",1e6)} ms · 输入保护重置 ${stats.optLong("inputResets")}")
    Text("刷新率为系统报告值；上述耗时属于输入链路，不代表画面 FPS 或触摸到显示的完整延迟。最大值从本次会话开始累计。")
}
