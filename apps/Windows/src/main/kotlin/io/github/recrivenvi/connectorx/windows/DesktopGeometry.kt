package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.G2Corners
import io.github.recrivenvi.connectorx.protocol.ScreenMath
import org.json.JSONObject
import java.awt.Shape
import java.awt.geom.Path2D
import java.awt.geom.AffineTransform
import kotlin.math.abs

class DesktopGeometry(raw:JSONObject) {
    val naturalWidth=raw.getInt("naturalWidth"); val naturalHeight=raw.getInt("naturalHeight")
    val rotation=raw.getInt("rotation")
    val width=if(rotation%2==0) naturalWidth else naturalHeight
    val height=if(rotation%2==0) naturalHeight else naturalWidth
    init { require(naturalWidth in 128..8192 && naturalHeight in 128..8192 && rotation in 0..3) }
    private val radii=G2Corners.fitRadii(naturalWidth.toDouble(),naturalHeight.toDouble(),raw.getJSONArray("radii").let {
        require(it.length()==4);DoubleArray(4) { i->it.getDouble(i) }
    })
    private val outline=Path2D.Double().apply {
        fun point(x:Double,y:Double):DoubleArray {
            val p=ScreenMath.toLogical(x.toFloat(),y.toFloat(),naturalWidth.toFloat(),naturalHeight.toFloat(),rotation)
            return doubleArrayOf(p[0].toDouble(),p[1].toDouble())
        }
        fun line(x:Double,y:Double) { val p=point(x,y);lineTo(p[0],p[1]) }
        fun corner(pos:Int) {
            val p=G2Corners.controls(radii[pos])
            fun at(i:Int):DoubleArray {
                val x=p[i*2];val y=p[i*2+1]
                return when(pos) {0->point(x,y);1->point(naturalWidth-y,x);2->point(naturalWidth-x,naturalHeight-y);else->point(y,naturalHeight-x)}
            }
            for(i in listOf(1,4)) { val a=at(i);val b=at(i+1);val c=at(i+2);curveTo(a[0],a[1],b[0],b[1],c[0],c[1]) }
        }
        val s=radii.map {it*G2Corners.SPAN_FACTOR};val start=point(s[0],0.0);moveTo(start[0],start[1])
        line(naturalWidth-s[1],0.0);corner(1);line(naturalWidth.toDouble(),naturalHeight-s[2]);corner(2)
        line(s[3],naturalHeight.toDouble());corner(3);line(0.0,s[0]);corner(0);closePath()
    }
    fun matches(w:Int,h:Int)=w>0&&h>0&&abs(w.toDouble()/h/(width.toDouble()/height)-1)<0.01
    fun shape(x:Double,y:Double,w:Double,h:Double):Shape = AffineTransform().apply {
        translate(x,y);scale(w/width,h/height)
    }.createTransformedShape(outline)
}
