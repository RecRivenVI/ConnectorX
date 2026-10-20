package io.github.recrivenvi.connectorx.overlay

import android.animation.ValueAnimator
import android.view.Choreographer
import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Limited drag attraction; only release commits a dock. One frame clock advances both axes. */
internal class OverlayDockMotion(private val apply:(OverlayPoint)->Unit):AutoCloseable {
    private var bounds:OverlayPlacement?=null
    private var originDock=OverlayDock.RIGHT_UPPER
    private var raw=OverlayPoint(0,0)
    private var rendered=raw
    private var radius=32f
    private var scale=1f
    private var magnet:OverlayDock?=null
    private var leftOrigin=false
    private var dragging=false
    private var x=0f
    private var y=0f
    private var velocityX=0f
    private var velocityY=0f
    private var targetX=0f
    private var targetY=0f
    private var fingerVelocityX=0f
    private var fingerVelocityY=0f
    private var lastFrameNanos=0L
    private var framePending=false
    private var finished:(()->Unit)?=null
    private val clock by lazy {Choreographer.getInstance()}
    private val attraction=FloatSpringSpec(dampingRatio=1f,stiffness=2400f)
    private val retreat=FloatSpringSpec(dampingRatio=1f,stiffness=6000f)
    private val docking=FloatSpringSpec(dampingRatio=1f,stiffness=750f)
    private val frame=Choreographer.FrameCallback {time->advance(time)}

    fun begin(placement:OverlayPlacement,dock:OverlayDock,position:OverlayPoint,attractionRadius:Float) {
        close()
        bounds=placement;originDock=dock;raw=position;rendered=position
        radius=attractionRadius;scale=attractionRadius/32f;dragging=true
    }

    fun move(x:Int,y:Int,velocityX:Float=0f,velocityY:Float=0f) {
        val b=bounds?:return
        if(!dragging)return
        raw=OverlayPoint(b.x(x),b.y(y))
        fingerVelocityX=velocityX;fingerVelocityY=velocityY
        updateMagnet()
        emitDrag()
        requestFrame()
    }

    private fun updateMagnet() {
        val b=bounds?:return
        if(!leftOrigin&&!near(b.point(originDock),radius*1.5f))leftOrigin=true
        val speed=hypot(fingerVelocityX,fingerVelocityY)/scale
        if(magnet?.let {!near(b.point(it),radius*1.5f)}==true||speed>=900f)magnet=null
        if(magnet==null&&speed<900f) {
            val nearest=b.nearestDock(raw.x,raw.y,originDock)
            if((nearest!=originDock||leftOrigin)&&near(b.point(nearest),radius))magnet=nearest
        }
        val anchor=magnet?.let {b.point(it)}
        if(anchor==null) {targetX=0f;targetY=0f;return}
        val dx=(anchor.x-raw.x).toFloat();val dy=(anchor.y-raw.y).toFloat()
        val distance=hypot(dx,dy)
        val proximity=smooth((1f-distance/(radius*1.5f)).coerceIn(0f,1f))
        val speedWeight=1f-smooth(((speed-240f)/660f).coerceIn(0f,1f))
        val weight=0.55f*proximity*speedWeight
        val limit=if(distance==0f)1f else minOf(1f,12f*scale/(distance*weight).coerceAtLeast(0.001f))
        targetX=dx*weight*limit;targetY=dy*weight*limit
    }

    private fun near(point:OverlayPoint,distance:Float):Boolean {
        val dx=(raw.x-point.x).toFloat();val dy=(raw.y-point.y).toFloat()
        return dx*dx+dy*dy<=distance*distance
    }

    /** Commit one of six slots; completion only re-enables controls, never changes the chosen slot. */
    fun settle(cancelled:Boolean=false,finished:()->Unit):OverlayDock {
        val b=bounds?:return originDock.also {finished()}
        val slot=b.nearestDock(raw.x,raw.y,originDock)
        val to=b.point(slot)
        // Keep the visible velocity, including the small spring correction. Velocity affects
        // only the path, never nearest-dock selection. Cap it to avoid overshooting the target.
        val releaseX=if(cancelled)0f else fingerVelocityX+velocityX
        val releaseY=if(cancelled)0f else fingerVelocityY+velocityY
        stopFrames();dragging=false;magnet=null
        x=rendered.x.toFloat();y=rendered.y.toFloat()
        targetX=to.x.toFloat();targetY=to.y.toFloat()
        velocityX=releaseVelocity(targetX-x,releaseX)
        velocityY=releaseVelocity(targetY-y,releaseY)
        originDock=slot;this.finished=finished
        requestFrame()
        return slot
    }

    private fun releaseVelocity(distance:Float,velocity:Float):Float {
        val limit=minOf(1600f*scale,sqrt(750f)*abs(distance))
        return velocity.coerceIn(-limit,limit)
    }

    private fun requestFrame() {
        if(bounds==null)return
        if(!ValueAnimator.areAnimatorsEnabled()||resting()) {
            stopFrames()
            x=targetX;y=targetY;velocityX=0f;velocityY=0f
            if(dragging)emitDrag() else complete()
        } else if(!framePending) {framePending=true;clock.postFrameCallback(frame)}
    }

    private fun advance(time:Long) {
        framePending=false
        if(bounds==null)return
        if(lastFrameNanos!=0L) {
            val elapsed=(time-lastFrameNanos).coerceAtLeast(0L)
            val spring=if(!dragging)docking else if(hypot(targetX,targetY)<hypot(x,y))retreat else attraction
            val nextX=spring.getValueFromNanos(elapsed,x,targetX,velocityX)
            val nextY=spring.getValueFromNanos(elapsed,y,targetY,velocityY)
            velocityX=spring.getVelocityFromNanos(elapsed,x,targetX,velocityX)
            velocityY=spring.getVelocityFromNanos(elapsed,y,targetY,velocityY)
            x=nextX;y=nextY
        }
        lastFrameNanos=time
        if(dragging)emitDrag() else emit(x,y)
        requestFrame()
        if(!framePending)lastFrameNanos=0L
    }

    private fun emitDrag() {
        val distance=hypot(x,y)
        val weight=if(distance==0f)1f else minOf(1f,12f*scale/distance)
        emit(raw.x+x*weight,raw.y+y*weight)
    }
    private fun emit(x:Float,y:Float) {
        val b=bounds?:return
        val point=OverlayPoint(b.x(x.roundToInt()),b.y(y.roundToInt()))
        if(point!=rendered) {rendered=point;apply(point)}
    }
    private fun resting()=abs(targetX-x)<=0.25f*scale&&abs(targetY-y)<=0.25f*scale&&
        abs(velocityX)<=10f*scale&&abs(velocityY)<=10f*scale
    private fun complete() {
        stopFrames();emit(targetX,targetY)
        val callback=finished;finished=null;callback?.invoke()
    }
    private fun stopFrames() {
        if(framePending)clock.removeFrameCallback(frame)
        framePending=false;lastFrameNanos=0L
    }
    private fun smooth(value:Float)=value*value*(3f-2f*value)
    override fun close() {
        stopFrames();bounds=null;magnet=null;finished=null;dragging=false;leftOrigin=false
        x=0f;y=0f;velocityX=0f;velocityY=0f;targetX=0f;targetY=0f
        fingerVelocityX=0f;fingerVelocityY=0f
    }
}
