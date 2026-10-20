package io.github.recrivenvi.connectorx.overlay

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.recrivenvi.connectorx.IRuntime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Bounded latest-choice mailbox; the runtime owns wheel scheduling, death cleanup and expiry. */
internal class GlobalScrollClient(
    private val runtime:IRuntime,
    private val ready:()->Unit,
    private val changed:(Int)->Unit,
    private val failed:(String)->Unit,
) : AutoCloseable {
    private data class Choice(val version:Long,val direction:Int)
    private val token=Binder()
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadScheduledExecutor {Thread(it,"global-scroll-client")}
    private val closed=AtomicBoolean()
    private val draining=AtomicBoolean()
    private val version=AtomicLong()
    private val appliedVersion=AtomicLong()
    private val pending=AtomicReference<Choice?>()
    private val death=IBinder.DeathRecipient {fail("Shizuku 已断开，全局滚动已停止")}

    fun start() {
        try {runtime.asBinder().linkToDeath(death,0)} catch(e:Exception) {fail(e.message?:"权限服务不可用");return}
        worker.execute {
            try {
                runtime.setGlobalScroll(token,0)
                main.post {if(!closed.get())ready()}
                if(!closed.get())worker.scheduleWithFixedDelay({
                    if(!closed.get())try {
                        val requestVersion=version.get()
                        val actual=runtime.keepGlobalScrollAlive(token)
                        val applied=appliedVersion.get()
                        main.post {if(!closed.get()&&requestVersion==version.get()&&requestVersion==applied)changed(actual)}
                    } catch(e:Exception) {fail(e.message?:"全局滚动连接失效")}
                },500,500,TimeUnit.MILLISECONDS)
            } catch(e:Exception) {fail(e.message?:"无法启动全局滚动")}
        }
    }
    fun select(direction:Int) {
        require(direction in -1..1)
        if(closed.get())return
        pending.set(Choice(version.incrementAndGet(),direction))
        drain()
    }
    private fun drain() {
        if(closed.get()||!draining.compareAndSet(false,true))return
        try {worker.execute {
            try {
                while(!closed.get()) {
                    val next=pending.getAndSet(null)?:break
                    val actual=runtime.setGlobalScroll(token,next.direction)
                    appliedVersion.set(next.version)
                    main.post {if(!closed.get()&&next.version==version.get())changed(actual)}
                }
            } catch(e:Exception) {fail(e.message?:"滚动注入失败")}
            finally {draining.set(false);if(pending.get()!=null&&!closed.get())drain()}
        }} catch(_:RejectedExecutionException) {draining.set(false)}
    }
    private fun fail(message:String) {main.post {if(!closed.get()) {failed(message);close()}}}
    override fun close() {
        if(!closed.compareAndSet(false,true))return
        pending.set(null)
        runCatching {runtime.asBinder().unlinkToDeath(death,0)}
        worker.execute {runCatching {runtime.closeGlobalScroll(token)}}
        worker.shutdown() // A missing/blocked close is bounded by the runtime's two-second lease.
    }
}
