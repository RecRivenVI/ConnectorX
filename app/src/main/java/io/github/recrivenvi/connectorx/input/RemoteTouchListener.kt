package io.github.recrivenvi.connectorx.input

import io.github.recrivenvi.connectorx.session.RemoteSession
import io.github.recrivenvi.connectorx.session.SurfaceControlSession

import android.view.MotionEvent
import android.view.View
import android.graphics.RectF
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.recrivenvi.connectorx.protocol.TouchOrigins
import io.github.recrivenvi.connectorx.protocol.LocalTouchFrame

internal interface RemoteTouchSink {
    val width: Int
    val height: Int
    val ready: Boolean
    val batched: Boolean get() = false
    fun touch(action: Int, id: Long, x: Int, y: Int)
    fun frame(frame: LocalTouchFrame) { error("Batched touch is not supported") }
    fun cancel()
}

/** Android retains the initial touch target outside its bounds; never re-hit-test accepted moves. */
internal class RemoteTouchListener(
    private val sink: RemoteTouchSink,
    private val inside: (Float,Float) -> Boolean = { x,y -> x>=0f && y>=0f && x<1f && y<1f },
) : View.OnTouchListener {
    constructor(session: RemoteSession, ready: () -> Boolean = { true },
                inside: (Float,Float) -> Boolean = { x,y -> x>=0f && y>=0f && x<1f && y<1f }) : this(
        object : RemoteTouchSink {
            override val width get() = session.width
            override val height get() = session.height
            override val ready get() = session.running && !session.inputBlocked && !session.inputOverloaded && ready()
            override fun touch(action: Int,id: Long,x: Int,y: Int) = session.touch(action,id,x,y)
            override fun cancel() = session.cancelTouches()
        }, inside)
    constructor(session: SurfaceControlSession, ready: () -> Boolean,
                inside: (Float,Float) -> Boolean) : this(
        object : RemoteTouchSink {
            override val width get()=session.width
            override val height get()=session.height
            override val ready get()=session.running&&!session.inputBlocked&&!session.inputOverloaded&&ready()
            override val batched get()=session.supportsTouchFrames
            override fun touch(action:Int,id:Long,x:Int,y:Int)=session.touch(action,id,x,y)
            override fun frame(frame:LocalTouchFrame)=session.touchFrame(frame)
            override fun cancel()=session.cancelTouches()
        },inside)
    private val origins=TouchOrigins()
    private var batchDownTimeNanos=0L
    private val batchIndices=IntArray(10)
    private val location=IntArray(2)
    private val rootLocation=IntArray(2)
    private val safeWindow=RectF()
    private fun updateSafeWindow(view:View) {
        val root=view.rootView
        val bars=ViewCompat.getRootWindowInsets(root)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        if(bars==null) {safeWindow.setEmpty();return}
        view.getLocationOnScreen(location); root.getLocationOnScreen(rootLocation)
        safeWindow.set(
            bars.left-location[0]+rootLocation[0].toFloat(),
            bars.top-location[1]+rootLocation[1].toFloat(),
            root.width-bars.right-location[0]+rootLocation[0].toFloat(),
            root.height-bars.bottom-location[1]+rootLocation[1].toFloat())
    }
    private fun outsideSystemBars(x:Float,y:Float):Boolean {
        return safeWindow.contains(x,y)
    }
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) { origins.clear(); sink.cancel();updateSafeWindow(view) }
        if(event.actionMasked==MotionEvent.ACTION_CANCEL || !sink.ready) {
            origins.clear(); sink.cancel(); view.parent?.requestDisallowInterceptTouchEvent(false); return true
        }
        if(view.width<=0 || view.height<=0 || sink.width<=0 || sink.height<=0) {
            origins.clear();sink.cancel();view.parent?.requestDisallowInterceptTouchEvent(false);return true
        }
        if(sink.batched) return batch(view,event)
        fun send(index: Int, action: Int) {
            val id=event.getPointerId(index)
            val nx=event.getX(index)/view.width; val ny=event.getY(index)/view.height
            val accepted=when(action) {
                0 -> origins.down(id, inside(nx,ny) && outsideSystemBars(event.getX(index),event.getY(index)))
                1 -> origins.up(id)
                else -> origins.move(id)
            }
            if(accepted) {
                if(action==0) view.parent?.requestDisallowInterceptTouchEvent(true)
                sink.touch(action,id.toLong(),(nx*sink.width).toInt(),(ny*sink.height).toInt())
            }
        }
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> send(event.actionIndex,0)
            MotionEvent.ACTION_MOVE -> for(index in 0 until event.pointerCount) send(index,2)
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> {
                send(event.actionIndex,1)
                if(event.actionMasked==MotionEvent.ACTION_UP) { origins.clear(); view.parent?.requestDisallowInterceptTouchEvent(false); view.performClick() }
            }
        }
        return true
    }

    private fun batch(view:View,event:MotionEvent):Boolean {
        val masked=event.actionMasked
        if(masked!=MotionEvent.ACTION_DOWN&&masked!=MotionEvent.ACTION_POINTER_DOWN&&masked!=MotionEvent.ACTION_MOVE&&
            masked!=MotionEvent.ACTION_UP&&masked!=MotionEvent.ACTION_POINTER_UP)return true
        val changed=event.getPointerId(event.actionIndex)
        val down=masked==MotionEvent.ACTION_DOWN||masked==MotionEvent.ACTION_POINTER_DOWN
        val up=masked==MotionEvent.ACTION_UP||masked==MotionEvent.ACTION_POINTER_UP
        if(down) {
            val x=event.getX(event.actionIndex);val y=event.getY(event.actionIndex)
            val count=(0 until event.pointerCount).count {origins.move(event.getPointerId(it))}
            if(origins.down(changed,count<10&&inside(x/view.width,y/view.height)&&outsideSystemBars(x,y)))
                view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        // Rejected fingers never enter the injected frame. Accepted ones keep their unrestricted coordinates.
        var count=0
        for(index in 0 until event.pointerCount)if(origins.move(event.getPointerId(index)))batchIndices[count++]=index
        if(count>0) {
            val changedAccepted=origins.move(changed)
            val action=when {down&&changedAccepted->0;up&&changedAccepted->1;else->2}
            val time=event.eventTime*1_000_000L
            if(action==0&&count==1)batchDownTimeNanos=time
            sink.frame(LocalTouchFrame(action,if(action==2)-1 else changed,time,batchDownTimeNanos,
                IntArray(count){event.getPointerId(batchIndices[it])},
                FloatArray(count){event.getX(batchIndices[it])/view.width*sink.width},
                FloatArray(count){event.getY(batchIndices[it])/view.height*sink.height}))
        }
        if(up) origins.up(changed)
        if(masked==MotionEvent.ACTION_UP) {
            origins.clear();view.parent?.requestDisallowInterceptTouchEvent(false);view.performClick()
        }
        return true
    }
}
