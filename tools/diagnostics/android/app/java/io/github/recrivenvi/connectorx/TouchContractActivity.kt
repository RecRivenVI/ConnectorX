package io.github.recrivenvi.connectorx

import io.github.recrivenvi.connectorx.input.RemoteTouchListener
import io.github.recrivenvi.connectorx.input.RemoteTouchSink

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Local dispatch harness; records a sink, never injects remote input. */
class TouchContractActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { MiuixTheme { AndroidView(modifier=Modifier.fillMaxSize(),factory={ context ->
            FrameLayout(context).apply {
                val records=mutableListOf<String>()
                val child=View(context).apply { setBackgroundColor(android.graphics.Color.DKGRAY) }
                addView(child,FrameLayout.LayoutParams(400,800).apply { leftMargin=300; topMargin=400 })
                setOnTouchListener { _,_ -> true }
                child.setOnTouchListener(RemoteTouchListener(object: RemoteTouchSink {
                    override val width=400
                    override val height=800
                    override val ready=true
                    override fun cancel() {}
                    override fun touch(action:Int,id:Long,x:Int,y:Int) { records.add("$action:$x:$y") }
                }))
                postDelayed({
                    fun gesture(points: List<Pair<Float,Float>>) {
                        val down=SystemClock.uptimeMillis()
                        points.forEachIndexed { index,p ->
                            val action=if(index==0) 0 else if(index==points.lastIndex) 1 else 2
                            val event=MotionEvent.obtain(down,down+index*16,action,p.first,p.second,0)
                            dispatchTouchEvent(event); event.recycle()
                        }
                    }
                    records.clear()
                    gesture(listOf(200f to 600f,500f to 600f,500f to 600f))
                    android.util.Log.i("CX_TOUCH_TEST","outside-start=${records.isEmpty()} $records")
                    records.clear()
                    gesture(listOf(500f to 600f,100f to 1300f,100f to 1300f))
                    android.util.Log.i("CX_TOUCH_TEST","inside-exit=${records==listOf("0:200:200","2:-200:900","1:-200:900")} $records")
                    child.layoutParams=FrameLayout.LayoutParams(-1,-1)
                    postDelayed({
                        records.clear()
                        gesture(listOf(500f to (height-1f),500f to (height/2f),500f to (height/2f)))
                        val nav=androidx.core.view.ViewCompat.getRootWindowInsets(rootView)?.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                        android.util.Log.i("CX_TOUCH_TEST","bar-start=${records.isEmpty()} nav=$nav $records")
                    },500)
                },1500)
            }
        }) } }
    }
}
