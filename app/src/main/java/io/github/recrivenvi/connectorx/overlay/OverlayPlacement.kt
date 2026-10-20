package io.github.recrivenvi.connectorx.overlay

internal enum class OverlayDock(val leftSide:Boolean) {
    LEFT_UPPER(true),LEFT_CENTER(true),LEFT_LOWER(true),
    RIGHT_UPPER(false),RIGHT_CENTER(false),RIGHT_LOWER(false)
}
internal data class OverlayPoint(val x:Int,val y:Int)

/** Six stable display-relative docks; insets only limit the final window bounds. */
internal data class OverlayPlacement(
    val left:Int,val top:Int,val right:Int,val bottom:Int,val width:Int,val height:Int,
    val screenWidth:Int,val screenHeight:Int,val visibleWidth:Int,val rightMargin:Int,
) {
    fun x(value:Int)=value.coerceIn(left,(right-width).coerceAtLeast(left))
    fun y(value:Int)=value.coerceIn(top,(bottom-height).coerceAtLeast(top))
    fun point(dock:OverlayDock):OverlayPoint {
        val center=dock==OverlayDock.LEFT_CENTER||dock==OverlayDock.RIGHT_CENTER
        val lower=dock==OverlayDock.LEFT_LOWER||dock==OverlayDock.RIGHT_LOWER
        val px=if(dock.leftSide)rightMargin-(width-visibleWidth)/2 else screenWidth-rightMargin-(width+visibleWidth)/2
        val centerY=if(center)screenHeight/2 else screenHeight*(if(lower)2 else 1)/3
        val py=centerY-height/2
        return OverlayPoint(x(px),y(py))
    }
    fun nearestDock(x:Int,y:Int,preferred:OverlayDock=OverlayDock.RIGHT_UPPER):OverlayDock {
        val origin=point(preferred)
        // Each later key applies only to exact ties in earlier keys. Side preference must
        // never beat a genuinely nearer point or a shorter distance from the gesture origin.
        return OverlayDock.entries.minWith(compareBy<OverlayDock> {distanceSquared(x,y,point(it))}
            .thenBy {if(it==preferred)0 else 1}
            .thenBy {distanceSquared(origin.x,origin.y,point(it))}
            .thenBy {if(it.leftSide==preferred.leftSide)0 else 1}
            .thenBy {it.ordinal})
    }
    private fun distanceSquared(x:Int,y:Int,p:OverlayPoint):Double {
        val dx=x.toDouble()-p.x;val dy=y.toDouble()-p.y
        return dx*dx+dy*dy
    }
    fun restoreDock(saved:String?,legacyX:Float,legacyY:Float,legacyDefault:Boolean):OverlayDock {
        return OverlayDock.entries.firstOrNull {it.name==saved}
            ?:if(legacyDefault)OverlayDock.RIGHT_UPPER else nearestDock(restoredX(legacyX),restoredY(legacyY))
    }
    fun restoredX(fraction:Float):Int=x(left+((right-left-width).coerceAtLeast(0)*fraction.coerceIn(0f,1f)).toInt())
    fun restoredY(fraction:Float):Int=y(top+((bottom-top-height).coerceAtLeast(0)*fraction.coerceIn(0f,1f)).toInt())
}
