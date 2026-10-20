package io.github.recrivenvi.connectorx.display

import android.graphics.*
import io.github.recrivenvi.connectorx.protocol.G2Corners
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import org.json.JSONObject
import kotlin.math.abs

class MirrorGeometry(val raw: JSONObject) {
    val naturalWidth = raw.getInt("naturalWidth")
    val naturalHeight = raw.getInt("naturalHeight")
    val rotation = raw.getInt("rotation")
    val logicalWidth = if (rotation % 2 == 0) naturalWidth else naturalHeight
    val logicalHeight = if (rotation % 2 == 0) naturalHeight else naturalWidth
    init {
        require(naturalWidth in 128..8192 && naturalHeight in 128..8192 && rotation in 0..3) { "屏幕几何范围无效" }
    }
    val requestedRadii = raw.getJSONArray("radii").let { array ->
        require(array.length() == 4)
        DoubleArray(4) { array.getDouble(it) }
    }
    val effectiveRadii = G2Corners.fitRadii(naturalWidth.toDouble(), naturalHeight.toDouble(), requestedRadii)
    val cutoutBoundsLogical: List<RectF> = raw.optJSONArray("cutoutBoundsLogical")?.let { bounds ->
        List(bounds.length()) { index ->
            val rect = bounds.getJSONArray(index)
            RectF(rect.getDouble(0).toFloat(), rect.getDouble(1).toFloat(), rect.getDouble(2).toFloat(), rect.getDouble(3).toFloat())
        }
    } ?: emptyList()
    val safeInsetsLogical = readInsets("safeInsetsLogical")
    val waterfallInsetsLogical = readInsets("waterfallInsetsLogical")
    val cutoutPathKind: String = raw.optString("cutoutPathKind", "none")
    val cutoutContours: List<FloatArray> = raw.optJSONArray("cutoutContours")?.let { contours ->
        List(contours.length()) { index ->
            val points = contours.getJSONArray(index)
            FloatArray(points.length()) { points.getDouble(it).toFloat() }
        }
    } ?: emptyList()
    val cutoutPath = buildCutoutPath()
    val preciseCutoutCircle: FloatArray? = raw.optJSONObject("preciseCutoutCircleNatural")?.let { circle ->
        floatArrayOf(circle.getDouble("centerX").toFloat(), circle.getDouble("centerY").toFloat(), circle.getDouble("radius").toFloat())
            .takeIf { it.all(Float::isFinite) && it[2] > 0f }
    }
    val preciseCutoutSource: String? = raw.optJSONObject("preciseCutoutCircleNatural")?.optString("source")?.takeIf { it.isNotBlank() }
    val outline = buildOutline()
    private val outlineRegion = Region().apply { setPath(outline, Region(0, 0, naturalWidth, naturalHeight)) }
    private fun buildOutline(): Path {
        val w = naturalWidth.toFloat(); val h = naturalHeight.toFloat()
        val spans = effectiveRadii.map { (it * G2Corners.SPAN_FACTOR).toFloat() }
        return Path().apply {
            fun corner(position: Int) {
                val p = G2Corners.controls(effectiveRadii[position])
                fun point(index: Int): Pair<Float, Float> {
                    val x = p[index * 2].toFloat(); val y = p[index * 2 + 1].toFloat()
                    return when(position) {
                        0 -> Pair(x, y)
                        1 -> Pair(w - y, x)
                        2 -> Pair(w - x, h - y)
                        else -> Pair(y, h - x)
                    }
                }
                val a=point(1); val b=point(2); val middle=point(3)
                val c=point(4); val d=point(5); val end=point(6)
                cubicTo(a.first,a.second,b.first,b.second,middle.first,middle.second)
                cubicTo(c.first,c.second,d.first,d.second,end.first,end.second)
            }
            moveTo(spans[0],0f)
            lineTo(w-spans[1],0f); corner(1)
            lineTo(w,h-spans[2]); corner(2)
            lineTo(spans[3],h); corner(3)
            lineTo(0f,spans[0]); corner(0)
            close()
        }
    }
    private fun readInsets(name: String): IntArray = raw.optJSONArray(name)?.let { values ->
        if (values.length() == 4) IntArray(4) { values.getInt(it) } else IntArray(4)
    } ?: IntArray(4)
    private fun buildCutoutPath(): Path = Path().apply {
        cutoutContours.forEach { contour ->
            if (contour.size < 2) return@forEach
            moveTo(contour[0], contour[1])
            var index = 2
            while (index + 1 < contour.size) { lineTo(contour[index], contour[index + 1]); index += 2 }
            close()
        }
    }
    fun matrix(width: Float, height: Float): Matrix {
        val w = naturalWidth.toFloat(); val h = naturalHeight.toFloat()
        val points = floatArrayOf(0f, 0f, w, 0f, 0f, h)
        val transformed = FloatArray(6)
        for (i in 0..2) {
            val p = ScreenMath.toLogical(points[i * 2], points[i * 2 + 1], w, h, rotation)
            transformed[i * 2] = p[0] * width / logicalWidth
            transformed[i * 2 + 1] = p[1] * height / logicalHeight
        }
        return Matrix().apply { setPolyToPoly(points, 0, transformed, 0, 3) }
    }
    fun matchesVideo(width: Int, height: Int): Boolean = width > 0 && height > 0 && abs(width.toFloat() / height / (logicalWidth.toFloat() / logicalHeight) - 1) < 0.01f
    fun contains(logicalX: Float, logicalY: Float): Boolean {
        if (logicalX < 0 || logicalY < 0 || logicalX >= logicalWidth || logicalY >= logicalHeight) return false
        val p = ScreenMath.toNatural(logicalX, logicalY, naturalWidth.toFloat(), naturalHeight.toFloat(), rotation)
        val x = p[0].toInt(); val y = p[1].toInt()
        if (!outlineRegion.contains(x, y)) return false
        preciseCutoutCircle?.let { circle ->
            val dx = p[0] - circle[0]; val dy = p[1] - circle[1]
            if (dx * dx + dy * dy <= circle[2] * circle[2]) return false
        }
        return true
    }
}
