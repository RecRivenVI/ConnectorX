package io.github.recrivenvi.connectorx.overlay

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.recrivenvi.connectorx.ConnectorApp
import io.github.recrivenvi.connectorx.MainActivity
import io.github.recrivenvi.connectorx.R
import io.github.recrivenvi.connectorx.ui.remote.LocalScrollControl
import io.github.recrivenvi.connectorx.ui.remote.LocalScrollLayout
import io.github.recrivenvi.connectorx.platform.PlatformStorage
import io.github.recrivenvi.connectorx.ui.theme.ConnectorTheme
import io.github.recrivenvi.connectorx.ui.theme.readThemeConfig
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

internal object OverlayLayout {
    val ActionTouchSize=48.dp
    val ActionWidth=LocalScrollLayout.TrackWidth
    val Gap=8.dp
    val ChromeHeight=ActionTouchSize*2+Gap*2
    val SnapDistance=32.dp
    val PreferredRailHeight=LocalScrollLayout.PreferredHeight
    val MinRailHeight=LocalScrollLayout.MinimumHeight
    val DefaultRightMargin=12.dp
}

object GlobalScrollState {
    var visible by mutableStateOf(false);internal set
    var ready by mutableStateOf(false);internal set
    var direction by mutableIntStateOf(0);internal set
    var error by mutableStateOf<String?>(null);internal set
}

/** A small, non-focusable window; it never captures or creates a display. */
class GlobalScrollService : Service() {
    companion object {const val ACTION_STOP="io.github.recrivenvi.connectorx.GLOBAL_SCROLL_STOP";private const val CHANNEL="global-scroll"}
    private val main=Handler(Looper.getMainLooper())
    private var client:GlobalScrollClient?=null
    private var window:FrameLayout?=null
    private var compose:ComposeView?=null
    private var manager:WindowManager?=null
    private var params:WindowManager.LayoutParams?=null
    private var bounds:OverlayPlacement?=null
    private var owner:OverlayOwner?=null
    private var registered=false
    private var moving by mutableStateOf(false)
    private var railHeight by mutableStateOf(OverlayLayout.PreferredRailHeight)
    private var theme by mutableStateOf<io.github.recrivenvi.connectorx.ui.theme.ThemeConfig?>(null)
    private var dragX=0f
    private var dragY=0f
    private var rawX=0f
    private var rawY=0f
    private var dragRawX=0f
    private var dragRawY=0f
    private var dragVelocity:VelocityTracker?=null
    private var dock=OverlayDock.RIGHT_UPPER
    private val motion=OverlayDockMotion {point->
        params?.let {p->if(p.x!=point.x||p.y!=point.y) {p.x=point.x;p.y=point.y;updateWindow()}}
    }
    private val preferences by lazy {getSharedPreferences("global_scroll_overlay",MODE_PRIVATE)}
    private val screenReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            if(intent.action==Intent.ACTION_SCREEN_OFF)select(0)
            // Keep the same window and position across lock/unlock. Never resume automatically.
        }
    }
    private val permissionCheck=object:Runnable {
        override fun run() {
            if(!Settings.canDrawOverlays(this@GlobalScrollService)) {fail("悬浮窗权限已关闭");return}
            main.postDelayed(this,1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        GlobalScrollState.error=null
        try {
            if(!Settings.canDrawOverlays(this))throw IllegalStateException("请先授予悬浮窗权限")
            val runtime=(application as ConnectorApp).engine.runtime.service?:throw IllegalStateException("请先连接 Shizuku")
            startNotification()
            createWindow()
            registerReceiver(screenReceiver,IntentFilter(Intent.ACTION_SCREEN_OFF),RECEIVER_NOT_EXPORTED)
            registered=true
            main.post(permissionCheck)
            client=GlobalScrollClient(runtime,{GlobalScrollState.ready=true},
                {actual->GlobalScrollState.direction=actual},{message->fail(message)}).also {it.start()}
        } catch(e:Exception) {fail(e.message?:"无法显示悬浮窗")}
    }
    private fun startNotification() {
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"全局滚动",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,20,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,21,Intent(this,GlobalScrollService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE)
        startForeground(2,Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_connector)
            .setContentTitle("全局滚动悬浮窗已开启").setContentText("点击上滚、停止、下滚；锁屏停止滚动")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"停止滚动",stop).build()).build())
    }
    private fun createWindow() {
        val display=getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            ?:error("主屏不可用")
        val context=createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
        manager=context.getSystemService(WindowManager::class.java)
        theme=readThemeConfig(PlatformStorage(this))
        owner=OverlayOwner()
        val root=object:FrameLayout(context) {
            override fun dispatchTouchEvent(event:MotionEvent):Boolean {
                rawX=event.rawX;rawY=event.rawY
                if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                    if(dragVelocity==null)dragVelocity=VelocityTracker.obtain()
                    dragVelocity?.clear()
                }
                // The window itself moves, so velocity must use screen coordinates as well.
                val dx=event.rawX-event.x;val dy=event.rawY-event.y
                event.offsetLocation(dx,dy)
                try {dragVelocity?.addMovement(event)} finally {event.offsetLocation(-dx,-dy)}
                return super.dispatchTouchEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val view=ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                theme?.let {config->ConnectorTheme(config) {
                    OverlayControls(GlobalScrollState.direction,GlobalScrollState.ready&&!moving,railHeight,::select,
                        {startDrag()},{_,_->moveToPointer()},{finishDrag()},{stopSelf()},
                        onDragCancel={finishDrag(cancelled=true)})
                }}
            }
        }
        root.addView(view,FrameLayout.LayoutParams(-1,-1))
        window=root;compose=view
        params=WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            alpha=1f
            title="ConnectorX 全局滚动"
        }
        place(restoring=true)
        manager!!.addView(root,params)
        owner!!.resume()
        GlobalScrollState.visible=true
    }
    private fun place(restoring:Boolean) {
        val wm=manager?:return
        val p=params?:return
        val metrics=wm.maximumWindowMetrics
        val inset=metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        val rect=metrics.bounds
        val scale=resources.displayMetrics.density*(theme?.densityScale?:1f)
        val heightDp=(rect.height()-inset.top-inset.bottom)/scale-8
        railHeight=(heightDp-OverlayLayout.ChromeHeight.value).coerceIn(OverlayLayout.MinRailHeight.value,OverlayLayout.PreferredRailHeight.value).dp
        p.width=(OverlayLayout.ActionTouchSize.value*scale).roundToInt()
        p.height=((railHeight.value+OverlayLayout.ChromeHeight.value)*scale).roundToInt()
        val gap=(4*scale).roundToInt()
        bounds=OverlayPlacement(inset.left+gap,inset.top+gap,rect.width()-inset.right-gap,rect.height()-inset.bottom-gap,p.width,p.height,
            screenWidth=rect.width(),screenHeight=rect.height(),visibleWidth=(OverlayLayout.ActionWidth.value*scale).roundToInt(),
            rightMargin=(OverlayLayout.DefaultRightMargin.value*scale).roundToInt())
        bounds!!.let {b->
            if(restoring) {
                val legacyX=preferences.getFloat("x",if(preferences.getBoolean("left",false))0f else 1f)
                val legacyY=preferences.getFloat("y",0.5f)
                dock=b.restoreDock(preferences.getString("dock_slot",null),legacyX,legacyY,
                    preferences.getBoolean("at_default",legacyX==1f&&legacyY==0.5f))
                preferences.edit().putString("dock_slot",dock.name).apply()
            }
        }
        applyDock()
    }
    private fun updateWindow() {
        val root=window?:return
        if(root.isAttachedToWindow)runCatching {manager?.updateViewLayout(root,params)}.onFailure {fail("悬浮窗已不可用")}
    }
    private fun startDrag() {
        val p=params?:return;val b=bounds?:return
        moving=true;select(0)
        dragX=p.x.toFloat();dragY=p.y.toFloat();dragRawX=rawX;dragRawY=rawY
        val radius=OverlayLayout.SnapDistance.value*resources.displayMetrics.density*(theme?.densityScale?:1f)
        motion.begin(b,dock,OverlayPoint(p.x,p.y),radius)
    }
    private fun moveToPointer() {
        if(!moving)return
        val p=params?:return;val b=bounds?:return
        // Raw screen coordinates avoid feedback when moving the window changes local touch coordinates.
        val x=b.x((dragX+rawX-dragRawX).roundToInt())
        val y=b.y((dragY+rawY-dragRawY).roundToInt())
        dragVelocity?.computeCurrentVelocity(1000)
        motion.move(x,y,dragVelocity?.xVelocity?:0f,dragVelocity?.yVelocity?:0f)
    }
    private fun applyDock() {
        val p=params?:return;val b=bounds?:return
        val point=b.point(dock);p.x=point.x;p.y=point.y
    }
    private fun finishDrag(cancelled:Boolean=false) {
        if(!moving)return
        if(!cancelled)moveToPointer()
        dock=motion.settle(cancelled) {moving=false}
        preferences.edit().putString("dock_slot",dock.name).apply()
    }
    private fun select(direction:Int) {
        if(direction!=0&&!GlobalScrollState.ready)return
        GlobalScrollState.direction=direction;client?.select(direction)
    }
    private fun fail(message:String) {GlobalScrollState.error=message;GlobalScrollState.ready=false;GlobalScrollState.direction=0;stopSelf()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP)select(0)
        return START_NOT_STICKY
    }
    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        select(0);motion.close();moving=false;theme=readThemeConfig(PlatformStorage(this));place(restoring=false);updateWindow()
    }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        motion.close()
        dragVelocity?.recycle();dragVelocity=null
        client?.close();client=null
        if(registered)unregisterReceiver(screenReceiver)
        window?.let {if(it.isAttachedToWindow)runCatching {manager?.removeViewImmediate(it)}}
        compose?.disposeComposition();owner?.close();window=null
        GlobalScrollState.visible=false;GlobalScrollState.ready=false;GlobalScrollState.direction=0
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}

internal class OverlayOwner : LifecycleOwner,ViewModelStoreOwner,SavedStateRegistryOwner,AutoCloseable {
    private val registry=LifecycleRegistry(this)
    private val state=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val viewModelStore=ViewModelStore()
    override val savedStateRegistry:SavedStateRegistry get()=state.savedStateRegistry
    init {state.performAttach();state.performRestore(null);registry.currentState=Lifecycle.State.CREATED}
    fun resume() {registry.currentState=Lifecycle.State.RESUMED}
    override fun close() {registry.currentState=Lifecycle.State.DESTROYED;viewModelStore.clear()}
}

@Composable
internal fun OverlayControls(direction:Int,enabled:Boolean,railHeight:androidx.compose.ui.unit.Dp,onSelect:(Int)->Unit,
    onDragStart:()->Unit,onDrag:(Float,Float)->Unit,onDragEnd:()->Unit,onClose:()->Unit,onDragCancel:()->Unit=onDragEnd) {
    val start by rememberUpdatedState(onDragStart);val move by rememberUpdatedState(onDrag);val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    val colors=MiuixTheme.colorScheme
    Column(Modifier.width(48.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(OverlayLayout.ActionTouchSize).semantics {contentDescription="拖动悬浮窗"}.pointerInput(Unit) {
            detectDragGestures(onDragStart={start()},onDragEnd={end()},onDragCancel={cancel()}) {change,amount->change.consume();move(amount.x,amount.y)}
        },contentAlignment=Alignment.Center) {
            Box(Modifier.size(OverlayLayout.ActionWidth).background(colors.surfaceContainer,CircleShape),contentAlignment=Alignment.Center) {
                Icon(MiuixIcons.More,null,Modifier.size(18.dp).graphicsLayer {rotationZ=90f},tint=colors.onSurfaceVariantSummary)
            }
        }
        Spacer(Modifier.height(OverlayLayout.Gap))
        LocalScrollControl(direction,enabled,onSelect,LocalScrollLayout.TouchWidth,Modifier.height(railHeight))
        Spacer(Modifier.height(OverlayLayout.Gap))
        IconButton(onClose,Modifier.size(OverlayLayout.ActionTouchSize).semantics {contentDescription="退出悬浮窗"}) {
            Box(Modifier.size(OverlayLayout.ActionWidth).background(colors.surfaceContainer,CircleShape),contentAlignment=Alignment.Center) {
                Icon(MiuixIcons.Close,null,Modifier.size(18.dp),tint=colors.onSurface)
            }
        }
    }
}
