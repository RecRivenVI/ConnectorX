package io.github.recrivenvi.connectorx.ui.remote

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.recrivenvi.connectorx.R
import io.github.recrivenvi.connectorx.connection.ConnectorEngine
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import io.github.recrivenvi.connectorx.session.LocalScrollSpeed
import io.github.recrivenvi.connectorx.session.RemoteSession
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.devices.ExitSessionDialog
import io.github.recrivenvi.connectorx.ui.mirror.DeviceMirrorView
import io.github.recrivenvi.connectorx.ui.screen.settings.LocalSessionDiagnostics
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun NetworkSessionPage(engine: ConnectorEngine, session: RemoteSession) {
    var menu by remember(session) { mutableStateOf(false) }
    var exiting by remember(session) { mutableStateOf(false) }
    var scrollDirection by remember(session) { mutableIntStateOf(0) }
    var geometryOverlay by remember(session) { mutableStateOf(false) }
    val diagnostics = LocalSessionDiagnostics.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val packageName = session.app?.packageName
    var layout by remember(packageName) { mutableStateOf(packageName?.let { AppLayoutPreferences.load(context, it) } ?: AppLayoutPreferences()) }
    val activeLayout = remember(session) { layout }
    val lifecycle = (LocalActivity.current as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) scrollDirection = 0 }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    DisposableEffect(session, menu, exiting) {
        session.localUiActive = menu || exiting
        onDispose { session.localUiActive = false }
    }
    LaunchedEffect(session, menu, exiting, session.running, session.inputBlocked, session.inputOverloaded, session.restarting) {
        if (menu || exiting || !session.running || session.inputBlocked || session.inputOverloaded || session.restarting) scrollDirection = 0
    }
    LaunchedEffect(session, scrollDirection) {
        if (scrollDirection == 0) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (session.running && scrollDirection != 0) {
            val now = withFrameNanos { it }
            // Existing wheel packets hold at most 16 units. Do not catch up a suspended UI frame.
            val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
            session.scroll(scrollDirection * LocalScrollSpeed.UNITS_PER_SECOND * seconds)
            previous = now
        }
    }
    LaunchedEffect(session, session.videoEpoch) {
        if (session.deviceMirror) while (session.running) { engine.refreshGeometry(session); delay(2000) }
    }
    LaunchedEffect(session, menu, diagnostics) {
        if (session.app != null) while (session.running && menu && diagnostics) { engine.refreshSessionInfo(session); delay(2000) }
    }
    BackHandler(enabled = !menu && !exiting && engine.prompt == null && engine.controlError == null) {
        if (session.running) session.key(KeyEvent.KEYCODE_BACK) else engine.disconnect()
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var reserved = WindowInsets(0, 0, 0, 0)
    if (!activeLayout.underStatus) reserved = reserved.union(WindowInsets.statusBarsIgnoringVisibility)
    if (!activeLayout.underNavigation) reserved = reserved.union(WindowInsets.navigationBarsIgnoringVisibility)
    reserved = reserved.union(WindowInsets.captionBar)
    if (!landscape && !activeLayout.underStatus) reserved = reserved.union(WindowInsets.displayCutout)
    BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        // Keep the display request based on the full controller content area. Preview fitting
        // must not shrink the remote Display to the size of its on-screen thumbnail.
        val viewport = IntSize((with(density) { maxWidth.roundToPx() } - reserved.getLeft(density, layoutDirection) - reserved.getRight(density, layoutDirection)).coerceAtLeast(0),
            (with(density) { maxHeight.roundToPx() } - reserved.getTop(density) - reserved.getBottom(density)).coerceAtLeast(0))
        LaunchedEffect(session, viewport) {
            if (session.app != null && viewport.width >= 128 && viewport.height >= 128) {
                delay(250); session.requestResize(viewport.width, viewport.height)
            }
        }
        val border = with(density) { 4.dp.toPx() }
        val status = WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat()
        val navigation = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toFloat()
        val margin = maxOf(status + with(density) { 56.dp.toPx() }, navigation + with(density) { 64.dp.toPx() }) + with(density) { 8.dp.toPx() }
        val geometry = session.geometry
        val useGeometry = geometry != null && !session.geometryStale && (session.width == 0 || geometry.matchesVideo(session.width, session.height))
        val sourceWidth = if (useGeometry) geometry.logicalWidth else session.width.takeIf { it > 0 } ?: session.app?.width ?: 1
        val sourceHeight = if (useGeometry) geometry.logicalHeight else session.height.takeIf { it > 0 } ?: session.app?.height ?: 1
        val fitted = ScreenMath.fit((with(density) { maxWidth.toPx() } - 2 * with(density) { LocalScrollLayout.MirrorSideSpace.toPx() }).coerceAtLeast(0f),
            with(density) { maxHeight.toPx() }, margin, if (session.deviceMirror) border else 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
        val frameBorder = if (session.deviceMirror) border else 0f
        val frameWidth = with(density) { (fitted[0] + 2 * frameBorder).toDp() }
        val frameHeight = with(density) { (fitted[1] + 2 * frameBorder).toDp() }
        if (fitted[0] > 0 && fitted[1] > 0) {
            val modifier = Modifier.align(Alignment.Center).width(frameWidth).height(frameHeight)
            if (session.deviceMirror) AndroidView(modifier = modifier,
                factory = { DeviceMirrorView(it, session, border) }, update = {
                    it.geometry = if (session.geometryStale) null else geometry
                    it.showGeometryOverlay = diagnostics && geometryOverlay
                })
            else RemoteVideo(session, modifier)
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton("ConnectorX", { scrollDirection = 0; engine.showLibrary() }, insideMargin = PaddingValues(horizontal = 8.dp))
            Text(stringResource(Res.string.ui_app_device_title, session.appTitle ?: stringResource(if (session.deviceMirror) Res.string.ui_device_mirror else Res.string.ui_session_type_control), session.peer.name),
                Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(stringResource(Res.string.ui_functions), { menu = true }, insideMargin = PaddingValues(horizontal = 8.dp))
            TextButton(stringResource(Res.string.ui_exit), { exiting = true }, insideMargin = PaddingValues(horizontal = 8.dp))
        }
        val railHeight = minOf(LocalScrollLayout.PreferredHeight, (maxHeight - with(density) { (2 * margin).toDp() }).coerceAtLeast(0.dp))
        if (railHeight >= LocalScrollLayout.MinimumHeight) LocalScrollControl(scrollDirection,
            session.running && !session.inputBlocked && !session.inputOverloaded && !session.restarting,
            { scrollDirection = it }, ((maxWidth - frameWidth) / 2).coerceAtLeast(0.dp), Modifier.align(AbsoluteAlignment.CenterRight).height(railHeight))
        fun navigate(key: Int) { scrollDirection = 0; session.cancelTouches(); session.key(key) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 34.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            LocalNavigationButton(R.drawable.ic_aosp_recent, stringResource(Res.string.ui_recents), session.running && !session.inputBlocked) { navigate(KeyEvent.KEYCODE_APP_SWITCH) }
            LocalNavigationButton(R.drawable.ic_aosp_home, stringResource(Res.string.ui_home), session.running && !session.inputBlocked) { navigate(KeyEvent.KEYCODE_HOME) }
            LocalNavigationButton(R.drawable.ic_aosp_back, stringResource(Res.string.ui_back), session.running) { navigate(KeyEvent.KEYCODE_BACK) }
        }
        if (!session.running) Card(Modifier.align(Alignment.Center).padding(24.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.ui_session_ended)); Text(stringResource(Res.string.ui_session_ended_reason))
            TextButton(stringResource(Res.string.ui_return_devices), { engine.disconnect() }, Modifier.fillMaxWidth())
        } }
    }
    NetworkFunctions(session, menu, { menu = false }, layout, { layout = it; packageName?.let { name -> it.save(context, name) } },
        geometryOverlay, { geometryOverlay = it }, { engine.restartRemoteApp(session) })
    ExitSessionDialog(exiting, { exiting = false }) { exiting = false; engine.disconnect() }
}
