package io.github.recrivenvi.connectorx.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.*
import io.github.recrivenvi.connectorx.IRuntime
import io.github.recrivenvi.connectorx.display.MirrorGeometry
import io.github.recrivenvi.connectorx.protocol.AppRequest
import io.github.recrivenvi.connectorx.protocol.LocalInputQueue
import io.github.recrivenvi.connectorx.protocol.LocalTouchFrame
import io.github.recrivenvi.connectorx.protocol.Wire
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Surface lifetime is independent of the task. Input has its own bounded, ordered worker. */
class LocalAppSession(
    private val runtime:IRuntime,
    val app:AppRequest,
    val appTitle:String,
    val geometry:MirrorGeometry?,
    private val report:(String)->Unit,
) : AutoCloseable,SurfaceControlSession {
    val sessionId=SecureRandom().nextInt(Int.MAX_VALUE)
    override val localControl=true
    override val supportsTouchFrames=true
    override val geometryStale=false
    override var width by mutableIntStateOf(app.width);private set
    override var height by mutableIntStateOf(app.height);private set
    override var running by mutableStateOf(true);private set
    override var inputBlocked by mutableStateOf(true);private set
    override var inputOverloaded by mutableStateOf(false);private set
    var info by mutableStateOf(JSONObject().put("state","waiting_surface"));private set
    var performance by mutableStateOf(JSONObject());private set
    var error by mutableStateOf<String?>(null);private set
    var restarting by mutableStateOf(false);private set
    var restartMessage by mutableStateOf<String?>(null);private set
    var scrollDirection by mutableIntStateOf(0);private set
    var imeBackError by mutableStateOf<String?>(null);private set
    private val imeBack=LocalImeBackState()
    private var imeTimeout:Runnable?=null
    private var pendingScrollDirection=0
    fun scrollWhenAttached(direction:Int) {
        require(direction in -1..1)
        pendingScrollDirection=direction
        if(direction!=0&&!inputBlocked&&attachedSurface!=null) {
            pendingScrollDirection=0
            if(scrollDirection!=direction)toggleScroll(direction)
        }
    }
    var localUiActive=false
        set(value) {
            if(field==value)return
            field=value
            if(value){cancelTouches();enqueue(Operation.ReleaseKeys)}
        }
    private val main=Handler(Looper.getMainLooper())
    private val lifecycle=Executors.newSingleThreadExecutor {r->Thread(r,"local-display-lifecycle")}
    private val input=Executors.newSingleThreadExecutor {r->Thread(r,"local-display-input")}
    private val diagnostics=Executors.newSingleThreadExecutor {r->Thread(r,"local-display-stats")}
    private val ime=Executors.newSingleThreadExecutor {r->Thread(r,"local-display-ime")}
    private val ready=AtomicBoolean(false)
    private val closed=AtomicBoolean(false)
    private val draining=AtomicBoolean(false)
    private val sampling=AtomicBoolean(false)
    private val imeBusy=AtomicBoolean(false)
    private val recovering=AtomicBoolean(false)
    private val inputGate=Any()
    private val generation=AtomicLong(0)
    @Volatile private var attachedSurface:Surface?=null
    @Volatile private var physicalRate=app.refreshRate
    @Volatile private var surfaceRateHint=false
    @Volatile private var resetFence:CompletableFuture<Unit>?=null
    private val acceptedIds=mutableSetOf<Int>() // UI thread only
    private val pressedKeys=mutableSetOf<Int>() // input worker only
    private val queue=LocalInputQueue<Command>(64)
    private val sampleCount=AtomicLong()
    private val queueTotalNanos=AtomicLong()
    private val queueMaxNanos=AtomicLong()
    private val binderTotalNanos=AtomicLong()
    private val binderMaxNanos=AtomicLong()
    private val failures=AtomicLong()
    private val resets=AtomicLong()
    private sealed interface Operation {
        data class Touch(val bytes:ByteArray):Operation
        data class Key(val action:Int,val code:Int,val repeat:Int,val meta:Int):Operation
        data class Wheel(val direction:Int):Operation
        data class Refresh(val rate:Float):Operation
        data object ReleaseKeys:Operation
        data class Reset(val completion:CompletableFuture<Unit>):Operation
    }
    private data class Command(val generation:Long,val enqueued:Long,val operation:Operation)

    override fun start(surface:Surface?) {
        if(surface==null||!surface.isValid||closed.get())return
        attachedSurface=surface
        lifecycle.execute {
            if(closed.get()||attachedSurface!==surface||!surface.isValid)return@execute
            try {
                resetFence?.get(15,TimeUnit.SECONDS)
                if(closed.get()||attachedSurface!==surface||!surface.isValid)return@execute
                val text=if(!ready.get()) {
                    runtime.startLocalApp(sessionId,app.packageName,app.width,app.height,app.dpi,app.refreshRate,app.systemDecorations,surface)
                        .also {ready.set(true)}
                } else {runtime.setLocalSurface(sessionId,surface);null}
                runtime.updateLocalRefreshRate(sessionId,physicalRate)
                main.post {
                    if(!closed.get()) {
                        text?.let {info=JSONObject(it);width=info.optInt("width",app.width);height=info.optInt("height",app.height)}
                        inputBlocked=attachedSurface!==surface||restarting;error=null
                        if(!inputBlocked&&pendingScrollDirection!=0)scrollWhenAttached(pendingScrollDirection)
                    }
                }
            } catch(e:Exception) {
                main.post {
                    if(!closed.get()&&attachedSurface===surface) {
                        error="本机显示启动失败：${e.message}";inputBlocked=true;running=false;report(error!!)
                    }
                }
            }
        }
    }

    override fun detachSurface(surface:Surface?) {
        if(surface!=null&&attachedSurface!==surface)return
        attachedSurface=null;pendingScrollDirection=0;inputBlocked=true;resetInput();resetImeBack()
        if(!closed.get())lifecycle.execute {if(ready.get())runCatching {runtime.clearLocalSurface(sessionId)}}
    }

    private fun enqueue(operation:Operation,move:Boolean=false) {
        if(closed.get()||!ready.get())return
        val accepted=synchronized(inputGate) {
            if(closed.get()||recovering.get())return
            queue.offer(Command(generation.get(),System.nanoTime(),operation),move)
        }
        if(!accepted) {
            resetInput(overload=true)
            return
        }
        drain()
    }

    private fun drain() {
        if(closed.get())return
        if(!draining.compareAndSet(false,true))return
        try { input.execute {
            try {
                while(!closed.get()) {
                    val command=queue.poll()?:break
                    if(command.generation!=generation.get())continue
                    val start=System.nanoTime()
                    val wait=(start-command.enqueued).coerceAtLeast(0)
                    try {
                        when(val op=command.operation) {
                            is Operation.Touch->runtime.injectLocalTouch(sessionId,op.bytes)
                            is Operation.Key->{
                                runtime.injectLocal(sessionId,Wire.key(op.action,op.code,op.repeat,op.meta))
                                if(op.action==0)pressedKeys.add(op.code)else pressedKeys.remove(op.code)
                            }
                            is Operation.Wheel->runtime.setLocalScroll(sessionId,op.direction,LocalScrollSpeed.units(op.direction))
                            is Operation.Refresh->runtime.updateLocalRefreshRate(sessionId,op.rate)
                            Operation.ReleaseKeys->releaseKeys()
                            is Operation.Reset->{
                                try {releaseInput();op.completion.complete(Unit)}
                                catch(e:Exception){op.completion.completeExceptionally(e);throw e}
                                main.post {if(command.generation==generation.get()&&!closed.get())inputOverloaded=false}
                            }
                        }
                    } catch(e:Exception) {
                        failures.incrementAndGet()
                        val failedGeneration=command.generation
                        var interruptedFence:CompletableFuture<Unit>?=null
                        val reset=synchronized(inputGate) {
                            if(generation.compareAndSet(failedGeneration,failedGeneration+1)) {
                                recovering.set(true);queue.clear();interruptedFence=resetFence;true
                            } else false
                        }
                        if(reset) {
                            runCatching {releaseInput()}
                            interruptedFence?.completeExceptionally(e)
                            main.post {
                                acceptedIds.clear();scrollDirection=0;inputOverloaded=false
                                if(!closed.get())error="本机输入失败：${e.message}"
                                recovering.set(false)
                            }
                        }
                    } finally {
                        val duration=System.nanoTime()-start
                        sampleCount.incrementAndGet();queueTotalNanos.addAndGet(wait);queueMaxNanos.accumulateAndGet(wait,::maxOf)
                        binderTotalNanos.addAndGet(duration);binderMaxNanos.accumulateAndGet(duration,::maxOf)
                    }
                }
            } finally {
                draining.set(false)
                if(!closed.get()&&queue.size()>0)drain()
            }
        } } catch(e:RejectedExecutionException) {draining.set(false);if(!closed.get())throw e}
    }

    private fun cancelFrame():ByteArray {
        val now=SystemClock.uptimeMillis()*1_000_000L
        return LocalTouchFrame(3,-1,now,now,intArrayOf(),floatArrayOf(),floatArrayOf()).encode()
    }
    private fun releaseInput() {
        var failure:Exception?=null
        fun attempt(action:()->Unit){try{action()}catch(e:Exception){if(failure==null)failure=e}}
        attempt {runtime.setLocalScroll(sessionId,0,0f)}
        attempt {runtime.injectLocalTouch(sessionId,cancelFrame())}
        attempt {releaseKeys()}
        failure?.let {throw it}
    }
    private fun releaseKeys() {
        var failure:Exception?=null
        for(code in pressedKeys.toList()) {
            try {runtime.injectLocal(sessionId,Wire.key(1,code));pressedKeys.remove(code)}
            catch(e:Exception) {if(failure==null)failure=e}
        }
        failure?.let {throw it}
    }
    private fun resetInput(overload:Boolean=false) {
        acceptedIds.clear();scrollDirection=0;resets.incrementAndGet()
        if(overload){inputOverloaded=true;error="输入繁忙，已取消当前手势；可重新触摸。"}
        synchronized(inputGate) {
            generation.incrementAndGet();queue.clear()
            val previous=resetFence
            val completion=CompletableFuture<Unit>()
            resetFence=completion
            // A superseded reset must wait for its replacement rather than leave an orphaned waiter.
            completion.whenComplete {_,failure->
                if(failure==null)previous?.complete(Unit)else previous?.completeExceptionally(failure)
            }
            if(ready.get()&&!closed.get())queue.offer(Command(generation.get(),System.nanoTime(),Operation.Reset(completion)),false)
            else completion.complete(Unit)
        }
        if(!closed.get()) {
            if(queue.size()>0)drain()
            // The lifecycle worker waits for the actual Reset, never an empty executor marker.
        }
    }

    fun resetImeBack() {
        imeBack.reset();imeTimeout?.let(main::removeCallbacks);imeTimeout=null
    }
    fun dismissImeBackError(){imeBackError=null}
    fun back() {
        if(!running||inputBlocked||inputOverloaded||recovering.get()||localUiActive||closed.get()||imeBusy.get())return
        if(imeBack.begin(SystemClock.uptimeMillis())!=LocalImeBackState.Action.CheckIme)return
        val request=imeBack.requestId
        val inputGeneration=generation.get()
        imeTimeout?.let(main::removeCallbacks)
        imeTimeout=Runnable {
            if(!closed.get()&&imeBack.failed(request))imeBackError="键盘尚未确认收起，请重试；本次返回没有发送给目标应用。"
        }.also {main.postDelayed(it,5000)}
        imeBusy.set(true)
        try {ime.execute {
            try {
                if(!closed.get()&&inputGeneration==generation.get()) {
                    val dismissed=runtime.hideLocalIme(sessionId)
                    main.post {
                        val action=imeBack.complete(request,dismissed,SystemClock.uptimeMillis())
                        if(!closed.get()&&inputGeneration==generation.get()&&!localUiActive&&
                            action==LocalImeBackState.Action.ForwardBack)key(android.view.KeyEvent.KEYCODE_BACK)
                    }
                }
            }
            catch(failure:Exception) {
                main.post {
                    if(!closed.get()&&imeBack.failed(request))imeBackError=failure.message?:"无法收起键盘，本次返回已取消。"
                }
            } finally {imeBusy.set(false)}
        }} catch(failure:RejectedExecutionException) {
            imeBusy.set(false)
            if(!closed.get()&&imeBack.failed(request))imeBackError="返回处理不可用，请重新打开会话。"
        }
    }
    fun key(code:Int){key(0,code,0,0);key(1,code,0,0)}
    fun key(action:Int,code:Int,repeat:Int,metaState:Int) {
        if(!running||inputBlocked||inputOverloaded||recovering.get())return
        enqueue(Operation.Key(action,code,repeat,metaState))
    }
    fun toggleScroll(direction:Int) {
        if(direction!=-1&&direction!=1)return
        selectScrollDirection(if(scrollDirection==direction)0 else direction)
    }
    fun selectScrollDirection(direction:Int) {
        require(direction in -1..1)
        if(!running||inputBlocked||inputOverloaded||recovering.get()||direction==scrollDirection)return
        scrollDirection=direction
        enqueue(Operation.Wheel(direction))
    }
    override fun touchFrame(frame:LocalTouchFrame) {
        if(!running||inputBlocked||inputOverloaded||recovering.get())return
        val count=frame.pointerCount
        when(frame.action) {
            0->{
                val index=(0 until count).firstOrNull {frame.getId(it)==frame.changedId}?:return
                if(frame.changedId in acceptedIds||count!=acceptedIds.size+1||
                    (0 until count).any {frame.getId(it)!=frame.changedId&&frame.getId(it) !in acceptedIds}||
                    frame.getX(index)<0||frame.getY(index)<0||frame.getX(index)>=width||frame.getY(index)>=height)return
                acceptedIds.add(frame.changedId)
            }
            1,2->if(acceptedIds.isEmpty()||count!=acceptedIds.size||(0 until count).any {frame.getId(it) !in acceptedIds})return
            3->acceptedIds.clear()
        }
        if(frame.action==1)acceptedIds.remove(frame.changedId)
        enqueue(Operation.Touch(frame.encode()),frame.action==2)
    }
    override fun touch(action:Int,id:Long,x:Int,y:Int) {
        error("Local touch requires a complete pointer frame")
    }
    override fun cancelTouches() {
        if(acceptedIds.isEmpty())return
        acceptedIds.clear();enqueue(Operation.Touch(cancelFrame()))
    }

    fun updateRefreshRate(rate:Float,hintApplied:Boolean) {
        if(!rate.isFinite()||rate !in 24f..240f||closed.get())return
        physicalRate=rate;surfaceRateHint=hintApplied
        if(ready.get()&&attachedSurface!=null)enqueue(Operation.Refresh(rate))
    }
    fun samplePerformance() {
        if(closed.get()||!ready.get()||!sampling.compareAndSet(false,true))return
        diagnostics.execute {
            try {
                val value=JSONObject(runtime.localPerformance(sessionId))
                val count=sampleCount.get().coerceAtLeast(1)
                value.put("queueDepth",queue.size()).put("queueCapacity",64).put("queueMerged",queue.getMergedCount())
                    .put("queueRejected",queue.getRejectedCount()).put("queueAverageMs",queueTotalNanos.get()/count/1e6)
                    .put("queueMaxMs",queueMaxNanos.get()/1e6).put("binderAverageMs",binderTotalNanos.get()/count/1e6)
                    .put("binderMaxMs",binderMaxNanos.get()/1e6).put("inputResets",resets.get()).put("binderFailures",failures.get())
                    .put("physicalRefreshRate",physicalRate).put("surfaceRateHintApplied",surfaceRateHint)
                main.post {if(!closed.get())performance=value}
            } catch(e:Exception) {
                main.post {if(!closed.get())performance=JSONObject().put("unavailable",e.message?:"统计不可用")}
            } finally {sampling.set(false)}
        }
    }

    fun restart() {
        if(restarting||!running||!ready.get()||closed.get())return
        restarting=true;inputBlocked=true;restartMessage=null;resetInput();resetImeBack()
        lifecycle.execute {
            var resetCompleted=false
            runCatching {
                resetFence?.get(15,TimeUnit.SECONDS);resetCompleted=true
                if(closed.get())throw IllegalStateException("本机会话已退出")
                runtime.restartLocalApp(sessionId)
            }
                .onSuccess {main.post {if(!closed.get()){info=JSONObject(it);restartMessage="已在当前本机虚拟显示中重启"}}}
                .onFailure {main.post {if(!closed.get())restartMessage="重启失败：${it.message}"}}
            main.post {restarting=false;if(!closed.get())inputBlocked=attachedSurface==null||!resetCompleted}
        }
    }
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        running=false;inputBlocked=true;scrollDirection=0;acceptedIds.clear();attachedSurface=null;resetImeBack()
        synchronized(inputGate){generation.incrementAndGet();queue.clear()}
        resetFence?.completeExceptionally(IllegalStateException("本机会话已退出"))
        input.shutdown();diagnostics.shutdown();ime.shutdown()
        lifecycle.execute {runCatching {runtime.stopLocalApp(sessionId)};ready.set(false);lifecycle.shutdown()}
    }
}
