package io.github.recrivenvi.connectorx.session

import android.view.Surface
import io.github.recrivenvi.connectorx.protocol.LocalTouchFrame

interface SurfaceControlSession {
    val width:Int
    val height:Int
    val running:Boolean
    val inputBlocked:Boolean
    val inputOverloaded:Boolean
    val geometryStale:Boolean
    val localControl:Boolean
    val supportsTouchFrames:Boolean get()=false
    fun start(surface:Surface?)
    fun detachSurface(surface:Surface?=null)
    fun touch(action:Int,id:Long,x:Int,y:Int)
    fun touchFrame(frame:LocalTouchFrame) { error("Batched touch is not supported by this session") }
    fun cancelTouches()
}
