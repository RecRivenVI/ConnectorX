package io.github.recrivenvi.connectorx.ui.mirror

import io.github.recrivenvi.connectorx.display.MirrorGeometry
import io.github.recrivenvi.connectorx.input.RemoteTouchListener
import io.github.recrivenvi.connectorx.session.LocalAppSession
import io.github.recrivenvi.connectorx.session.SurfaceControlSession

import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/** The decoder renders directly to a SurfaceView; opaque masks reproduce the physical outline without texture composition. */
internal class DeviceMirrorView(context: Context, private val session: SurfaceControlSession, thickness: Float) : FrameLayout(context) {
    private val border = thickness.roundToInt().coerceAtLeast(1)
    private val video = object:SurfaceView(context) {
        override fun performClick():Boolean {super.performClick();return true}
    }
    var geometry: MirrorGeometry? = null
        set(value) {
            // A video match can change while the geometry object stays the same.
            if (field === value && cachedGeometry === selectedGeometry(value)) return
            field = value
            invalidate()
        }
    var showGeometryOverlay: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(135, 139, 145); style = Paint.Style.STROKE; strokeWidth = 2f * border; strokeJoin = Paint.Join.ROUND }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.BLACK;style=Paint.Style.FILL}
    private val cutoutBoundsPaint = Paint().apply { color = Color.argb(128, 255, 160, 0); style = Paint.Style.FILL }
    private var cachedGeometry: MirrorGeometry? = null
    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private val geometryMatrix = Matrix()
    private val outline = Path()
    private val cutout = Path()
    private val contentBounds = RectF()
    private val cutoutBounds = RectF()
    private var hasCutout = false
    private var hasCutoutBounds = false
    private val displayManager = if (session.localControl) context.getSystemService(DisplayManager::class.java) else null
    private var displayListenerRegistered = false
    private var refreshSurface: Surface? = null
    private var lastRefreshRate = Float.NaN
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (isAttachedToWindow && display?.displayId == displayId) updateLocalRefreshRate()
        }
    }
    init {
        setWillNotDraw(false)
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(border, border, border, border) })
        // A local VirtualDisplay renders at the physical display resolution. Keep that buffer size and let
        // SurfaceFlinger scale the SurfaceView layer into this smaller device-shaped window.
        if(session.localControl&&session.width>0&&session.height>0) video.holder.setFixedSize(session.width,session.height)
        video.holder.addCallback(object:SurfaceHolder.Callback {
            override fun surfaceCreated(holder:SurfaceHolder) {
                refreshSurface = holder.surface
                session.start(holder.surface)
                // A recreated Surface needs its own hint even when the display rate is unchanged.
                updateLocalRefreshRate(force = true)
            }
            override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {}
            override fun surfaceDestroyed(holder:SurfaceHolder) {
                refreshSurface = null
                session.detachSurface(holder.surface)
            }
        })
        video.setOnTouchListener(RemoteTouchListener(session,
            ready = { session.localControl || geometry?.let { !session.geometryStale && it.matchesVideo(session.width, session.height) } == true },
            inside = { x,y -> geometry?.let { it.contains(x*it.logicalWidth,y*it.logicalHeight) } ?: session.localControl }))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!session.localControl) return
        if (!displayListenerRegistered) displayManager?.let {
            it.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            displayListenerRegistered = true
        }
        updateLocalRefreshRate()
    }
    override fun onDetachedFromWindow() {
        if (displayListenerRegistered) {
            displayManager?.unregisterDisplayListener(displayListener)
            displayListenerRegistered = false
        }
        super.onDetachedFromWindow()
    }
    private fun updateLocalRefreshRate(force: Boolean = false) {
        if (!session.localControl || !isAttachedToWindow) return
        val rate = display?.refreshRate ?: return
        if (!rate.isFinite() || rate <= 0f) return
        // Remember the observed rate before applying a hint so unchanged display events cannot feed back.
        if (!force && abs(rate - lastRefreshRate) < 0.1f) return
        lastRefreshRate = rate
        val surface = refreshSurface
        val hintApplied = if (surface != null && surface.isValid) {
            try {
                surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
                true
            } catch (_: Exception) {
                false
            }
        } else false
        // The session retains attach-time updates until start; a hint succeeding does not prove presentation cadence.
        (session as? LocalAppSession)?.updateRefreshRate(rate, hintApplied)
    }
    private fun selectedGeometry(value: MirrorGeometry?): MirrorGeometry? = value?.takeIf {
        session.localControl || (!session.geometryStale && (session.width == 0 || it.matchesVideo(session.width, session.height)))
    }
    private fun updateGeometryCache(w: Float, h: Float, g: MirrorGeometry?) {
        if (cachedWidth == w && cachedHeight == h && cachedGeometry === g) return
        cachedWidth = w
        cachedHeight = h
        cachedGeometry = g
        contentBounds.set(0f, 0f, w, h)
        outline.rewind()
        cutout.rewind()
        cutoutBounds.setEmpty()
        geometryMatrix.reset()
        hasCutout = false
        hasCutoutBounds = false
        if (g == null) {
            outline.addRect(contentBounds, Path.Direction.CW)
            return
        }
        geometryMatrix.set(g.matrix(w, h))
        outline.set(g.outline)
        outline.transform(geometryMatrix)
        g.preciseCutoutCircle?.let { circle ->
            cutout.addCircle(circle[0], circle[1], circle[2], Path.Direction.CW)
            cutout.transform(geometryMatrix)
            hasCutout = true
        }
        g.cutoutBoundsLogical.firstOrNull()?.let { rect ->
            cutoutBounds.set(rect.left * w / g.logicalWidth, rect.top * h / g.logicalHeight,
                rect.right * w / g.logicalWidth, rect.bottom * h / g.logicalHeight)
            hasCutoutBounds = true
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun dispatchDraw(canvas: Canvas) {
        val w = (width - 2 * border).toFloat(); val h = (height - 2 * border).toFloat()
        if (w <= 0 || h <= 0) return
        val g = selectedGeometry(geometry)
        updateGeometryCache(w, h, g)
        super.dispatchDraw(canvas)
        var saved = canvas.save(); canvas.translate(border.toFloat(), border.toFloat())
        canvas.clipRect(contentBounds);canvas.clipOutPath(outline);canvas.drawRect(contentBounds,maskPaint);canvas.restoreToCount(saved)
        saved = canvas.save(); canvas.translate(border.toFloat(), border.toFloat())
        // Only the outer half of the 2t stroke survives. The inner edge is exactly the video clipping edge.
        canvas.clipOutPath(outline); canvas.drawPath(outline, framePaint); canvas.restoreToCount(saved)
        if (hasCutout) {
            saved = canvas.save(); canvas.translate(border.toFloat(), border.toFloat()); canvas.clipPath(outline)
            canvas.drawPath(cutout, maskPaint); canvas.restoreToCount(saved)
        }
        if (showGeometryOverlay && hasCutoutBounds) {
            saved = canvas.save(); canvas.translate(border.toFloat(), border.toFloat()); canvas.clipPath(outline)
            canvas.drawRect(cutoutBounds, cutoutBoundsPaint)
            canvas.restoreToCount(saved)
        }
    }
}
