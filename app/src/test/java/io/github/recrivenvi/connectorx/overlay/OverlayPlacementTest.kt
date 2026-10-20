package io.github.recrivenvi.connectorx.overlay

import org.junit.Assert.*
import org.junit.Test

class OverlayPlacementTest {
    private val portrait=OverlayPlacement(4,124,1076,2356,48,316,1080,2400,32,12)

    @Test fun sixPointsUseBothThirdBoundariesAndCenterWithSymmetricMargins() {
        assertEquals(6,OverlayDock.entries.map(portrait::point).toSet().size)
        for(dock in OverlayDock.entries) {
            val p=portrait.point(dock)
            val lower=dock==OverlayDock.LEFT_LOWER||dock==OverlayDock.RIGHT_LOWER
            val center=dock==OverlayDock.LEFT_CENTER||dock==OverlayDock.RIGHT_CENTER
            val left=dock==OverlayDock.LEFT_UPPER||dock==OverlayDock.LEFT_CENTER||dock==OverlayDock.LEFT_LOWER
            assertEquals(if(center)1200 else if(lower)1600 else 800,p.y+portrait.height/2)
            if(left)assertEquals(12,p.x+(portrait.width-portrait.visibleWidth)/2)
            else assertEquals(12,portrait.screenWidth-(p.x+(portrait.width+portrait.visibleWidth)/2))
        }
    }

    @Test fun releasedPositionsInEachQuadrantChooseOnlyNearestDock() {
        assertEquals(OverlayDock.LEFT_UPPER,portrait.nearestDock(200,400))
        assertEquals(OverlayDock.LEFT_LOWER,portrait.nearestDock(200,1800))
        assertEquals(OverlayDock.RIGHT_UPPER,portrait.nearestDock(850,400))
        assertEquals(OverlayDock.RIGHT_LOWER,portrait.nearestDock(850,1800))
        assertEquals(OverlayDock.LEFT_CENTER,portrait.nearestDock(200,1042))
        assertEquals(OverlayDock.RIGHT_CENTER,portrait.nearestDock(850,1042))
        for(x in -500..1500 step 100)for(y in -500..3000 step 100) {
            val chosen=portrait.point(portrait.nearestDock(x,y))
            fun distance(p:OverlayPoint)=(x-p.x).toLong()*(x-p.x)+(y-p.y).toLong()*(y-p.y)
            assertTrue(OverlayDock.entries.all {distance(chosen)<=distance(portrait.point(it))})
        }
    }

    @Test fun exactTieKeepsCurrentDock() {
        val rows=listOf(OverlayDock.LEFT_UPPER to OverlayDock.RIGHT_UPPER,
            OverlayDock.LEFT_CENTER to OverlayDock.RIGHT_CENTER,OverlayDock.LEFT_LOWER to OverlayDock.RIGHT_LOWER)
        for((left,right) in rows) {
            val l=portrait.point(left);val r=portrait.point(right)
            for(dock in listOf(left,right))assertEquals(dock,portrait.nearestDock((l.x+r.x)/2,l.y,dock))
        }
    }

    @Test fun screenCenterTieUsesClosestCandidateToGestureOrigin() {
        val left=portrait.point(OverlayDock.LEFT_CENTER)
        val right=portrait.point(OverlayDock.RIGHT_CENTER)
        for(origin in OverlayDock.entries)assertEquals(
            if(origin.leftSide)OverlayDock.LEFT_CENTER else OverlayDock.RIGHT_CENTER,
            portrait.nearestDock((left.x+right.x)/2,left.y,origin))
    }

    @Test fun genuinelyNearestCandidateAlwaysWinsBeforeOriginOrSidePreference() {
        val left=portrait.point(OverlayDock.LEFT_CENTER)
        val right=portrait.point(OverlayDock.RIGHT_CENTER)
        val middle=(left.x+right.x)/2
        assertEquals(OverlayDock.LEFT_CENTER,portrait.nearestDock(middle-1,left.y,OverlayDock.RIGHT_UPPER))
        assertEquals(OverlayDock.RIGHT_CENTER,portrait.nearestDock(middle+1,left.y,OverlayDock.LEFT_LOWER))
    }

    @Test fun sameSideBreaksTieWhenCandidatesAreAlsoEquidistantFromOrigin() {
        val narrow=portrait.copy(right=52)
        val center=narrow.point(OverlayDock.LEFT_CENTER)
        assertEquals(center,narrow.point(OverlayDock.RIGHT_CENTER))
        assertEquals(OverlayDock.RIGHT_CENTER,narrow.nearestDock(center.x,center.y,OverlayDock.RIGHT_UPPER))
        assertEquals(OverlayDock.LEFT_CENTER,narrow.nearestDock(center.x,center.y,OverlayDock.LEFT_LOWER))
    }

    @Test fun savedDockStaysOnSameSideAndThirdAfterRotation() {
        val landscape=portrait.copy(screenWidth=2400,screenHeight=1200,left=124,top=4,right=2356,bottom=1156)
        for(dock in OverlayDock.entries) {
            val restored=landscape.restoreDock(dock.name,0.5f,0.5f,false)
            assertEquals(dock,restored)
            val p=landscape.point(restored)
            val lower=dock==OverlayDock.LEFT_LOWER||dock==OverlayDock.RIGHT_LOWER
            val center=dock==OverlayDock.LEFT_CENTER||dock==OverlayDock.RIGHT_CENTER
            assertEquals(if(center)600 else if(lower)800 else 400,p.y+landscape.height/2)
            assertEquals(if(dock==OverlayDock.LEFT_UPPER||dock==OverlayDock.LEFT_CENTER||dock==OverlayDock.LEFT_LOWER)124 else 2356-48,p.x)
        }
    }

    @Test fun legacyFreePositionMigratesToNearestSlotAndOldDefaultStaysRightUpper() {
        assertEquals(OverlayDock.LEFT_UPPER,portrait.restoreDock(null,0f,0f,false))
        assertEquals(OverlayDock.RIGHT_LOWER,portrait.restoreDock(null,1f,1f,false))
        assertEquals(OverlayDock.RIGHT_UPPER,portrait.restoreDock(null,1f,0.5f,true))
        assertEquals(OverlayDock.LEFT_LOWER,portrait.restoreDock("obsolete-slot",0f,1f,false))
        assertEquals(OverlayDock.LEFT_LOWER,portrait.restoreDock("LEFT_LOWER",1f,0f,true))
        assertEquals(OverlayDock.RIGHT_CENTER,portrait.restoreDock("RIGHT_CENTER",0f,0f,true))
    }

    @Test fun unequalInsetsAndTinyDisplayRemainSafe() {
        val changed=portrait.copy(top=44,bottom=2276)
        for(dock in OverlayDock.entries)assertEquals(portrait.point(dock),changed.point(dock))
        val tiny=OverlayPlacement(10,10,40,40,48,316,50,50,32,12)
        for(dock in OverlayDock.entries)assertEquals(OverlayPoint(10,10),tiny.point(dock))
        assertEquals(OverlayDock.LEFT_LOWER,tiny.nearestDock(900,900,OverlayDock.LEFT_LOWER))
    }
}
