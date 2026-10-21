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
import io.github.recrivenvi.connectorx.ui.screen.settings.LocalSessionDiagnostics
import io.github.recrivenvi.connectorx.ui.mirror.DeviceMirrorView
import kotlinx.coroutines.delay
import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.stringResource
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
    val diagnostics=LocalSessionDiagnostics.current
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
    LaunchedEffect(session,menu,session.running,diagnostics) {
        while(menu&&session.running&&diagnostics) {
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
            TextButton(stringResource(Res.string.ui_functions),{menu=true},Modifier.width(66.dp).height(48.dp),insideMargin=PaddingValues(0.dp))
            Text(session.appTitle,Modifier.weight(1f),textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            TextButton(stringResource(Res.string.ui_exit),{engine.disconnect()},Modifier.width(66.dp).height(48.dp),insideMargin=PaddingValues(0.dp))
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
            LocalNavigationButton(R.drawable.ic_aosp_recent,stringResource(Res.string.ui_recents),session.running&&!session.inputBlocked) {
                navigate(KeyEvent.KEYCODE_APP_SWITCH)
            }
            LocalNavigationButton(R.drawable.ic_aosp_home,stringResource(Res.string.ui_home),session.running&&!session.inputBlocked) {
                navigate(KeyEvent.KEYCODE_HOME)
            }
            LocalNavigationButton(R.drawable.ic_aosp_back,stringResource(Res.string.ui_back),session.running) {session.back()}
        }
        if(!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.ui_local_session_ended))
                Text(stringResource(Res.string.ui_session_ended_reason))
                TextButton(stringResource(Res.string.ui_return_devices),{engine.disconnect()},Modifier.fillMaxWidth())
            }
        }
    }
    session.imeBackError?.let {message->
        WindowDialog(show=true,title=stringResource(Res.string.ui_ime_hide_failed),onDismissRequest=session::dismissImeBackError) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(message)
                TextButton(stringResource(Res.string.ui_understood),session::dismissImeBackError,Modifier.fillMaxWidth())
            }
        }
    }
    WindowDialog(show=menu,title=session.appTitle,onDismissRequest={menu=false}) {
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.ui_local_mode_note))
            Text(stringResource(Res.string.ui_local_scroll_note))
            Text(stringResource(Res.string.ui_local_scroll_speed, LocalScrollSpeed.UNITS_PER_SECOND.toInt()))
            Text(stringResource(when {session.scrollDirection>0 -> Res.string.ui_scroll_up; session.scrollDirection<0 -> Res.string.ui_scroll_down; else -> Res.string.ui_scroll_stopped}))
            if(diagnostics) {
                val info=session.info
                Text(stringResource(Res.string.ui_local_display_diagnostics, info.optInt("displayId",-1), info.optInt("width"), info.optInt("height"), info.optInt("dpi"), info.optInt("rotation",-1)))
                LocalPerformance(session.performance)
            } else Text(stringResource(Res.string.ui_diagnostics_footer))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_local_system_bars))
                Switch(checked=layout.systemDecorations,onCheckedChange={save(layout.copy(systemDecorations=it))})
            }
            Text(stringResource(Res.string.ui_local_bars_note))
            TextButton(stringResource(if(session.restarting) Res.string.ui_restarting else Res.string.ui_restart_app),{session.restart()},
                Modifier.fillMaxWidth(),enabled=session.running&&!session.restarting)
            session.restartMessage?.let {Text(it)}
            session.error?.let {Text(it)}
            TextButton(stringResource(Res.string.ui_continue),{menu=false},Modifier.fillMaxWidth())
            TextButton(stringResource(Res.string.ui_return_connectorx),{menu=false;engine.showLibrary()},Modifier.fillMaxWidth())
            TextButton(stringResource(Res.string.ui_exit_local),{menu=false;engine.disconnect()},Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun LocalNavigationButton(icon:Int,label:String,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick,Modifier.width(64.dp).height(52.dp).semantics {if(!enabled)disabled()},enabled=enabled) {
        Icon(painterResource(icon),label,Modifier.size(22.dp),
            tint=MiuixTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else 0.38f))
    }
}
