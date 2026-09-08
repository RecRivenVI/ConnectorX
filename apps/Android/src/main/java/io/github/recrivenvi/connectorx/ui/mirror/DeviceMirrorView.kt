package io.github.recrivenvi.connectorx.ui.mirror

import io.github.recrivenvi.connectorx.display.MirrorGeometry
import io.github.recrivenvi.connectorx.input.RemoteTouchListener
import io.github.recrivenvi.connectorx.session.RemoteSession

import android.content.Context
import android.graphics.*
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Composited TextureView permits actual non-rectangular clipping; SurfaceView is kept for P1/P2. */
class DeviceMirrorView(context: Context, private val session: RemoteSession, thickness: Float) : FrameLayout(context) {
    private val border = thickness.roundToInt().coerceAtLeast(1)
    private val texture = TextureView(context)
    private var surface: Surface? = null
    var geometry: MirrorGeometry? = null
        set(value) { field = value; invalidate() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(135, 139, 145); style = Paint.Style.STROKE; strokeWidth = 2f * border; strokeJoin = Paint.Join.ROUND }
    init {
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(border, border, border, border) })
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(texture).also { session.start(it) }
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                surface?.let { session.detachSurface(it); it.release() }; surface = null; return true
            }
        }
        texture.setOnTouchListener(RemoteTouchListener(session,
            ready = { geometry?.let { !session.geometryStale && it.matchesVideo(session.width, session.height) } == true },
            inside = { x,y -> geometry?.let { it.contains(x*it.logicalWidth,y*it.logicalHeight) } == true }))
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun dispatchDraw(canvas: Canvas) {
        val w = (width - 2 * border).toFloat(); val h = (height - 2 * border).toFloat()
        if (w <= 0 || h <= 0) return
        val g = geometry?.takeIf { !session.geometryStale && (session.width == 0 || it.matchesVideo(session.width, session.height)) }
        val matrix = g?.matrix(w, h)
        val outline = if (g == null) Path().apply { addRect(0f, 0f, w, h, Path.Direction.CW) } else Path(g.outline).apply { transform(matrix!!) }
        var saved = canvas.save()
        canvas.translate(border.toFloat(), border.toFloat()); canvas.clipPath(outline)
        canvas.translate(-border.toFloat(), -border.toFloat()); super.dispatchDraw(canvas); canvas.restoreToCount(saved)
        saved = canvas.save(); canvas.translate(border.toFloat(), border.toFloat())
        // Only the outer half of the 2t stroke survives. The inner edge is exactly the video clipping edge.
        canvas.clipOutPath(outline); canvas.drawPath(outline, framePaint); canvas.restoreToCount(saved)
        // Camera/cutout overlays are deliberately disabled for the G2 corner preview.
    }
}
