package io.github.recrivenvi.connectorx.input

import io.github.recrivenvi.connectorx.session.RemoteSession

import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.recrivenvi.connectorx.protocol.TouchOrigins

internal interface RemoteTouchSink {
    val width: Int
    val height: Int
    val ready: Boolean
    fun touch(action: Int, id: Long, x: Int, y: Int)
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
    private val origins=TouchOrigins()
    private fun outsideSystemBars(view: View, x: Float, y: Float): Boolean {
        val root=view.rootView
        val bars=ViewCompat.getRootWindowInsets(root)?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()) ?: return false
        val location=IntArray(2); val rootLocation=IntArray(2)
        view.getLocationOnScreen(location); root.getLocationOnScreen(rootLocation)
        val wx=x+location[0]-rootLocation[0]; val wy=y+location[1]-rootLocation[1]
        return wx>=bars.left && wx<root.width-bars.right && wy>=bars.top && wy<root.height-bars.bottom
    }
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) { origins.clear(); sink.cancel() }
        if(event.actionMasked==MotionEvent.ACTION_CANCEL || !sink.ready) {
            origins.clear(); sink.cancel(); view.parent?.requestDisallowInterceptTouchEvent(false); return true
        }
        if(view.width<=0 || view.height<=0 || sink.width<=0 || sink.height<=0) return true
        fun send(index: Int, action: Int) {
            val id=event.getPointerId(index)
            val nx=event.getX(index)/view.width; val ny=event.getY(index)/view.height
            val accepted=when(action) {
                0 -> origins.down(id, inside(nx,ny) && outsideSystemBars(view,event.getX(index),event.getY(index)))
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
}
