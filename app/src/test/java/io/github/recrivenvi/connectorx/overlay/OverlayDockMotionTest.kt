package io.github.recrivenvi.connectorx.overlay

import android.app.Application
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class OverlayDockMotionTest {
    private val bounds=OverlayPlacement(4,124,1076,2356,48,316,1080,2400,32,12)
    private fun advance(millis:Long) {
        // A paused vsync is emitted when the clock advances; step frame by frame rather than
        // jumping straight to the final timestamp and starting the first frame there.
        var remaining=millis
        while(remaining>0) {
            val step=minOf(16L,remaining)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            remaining-=step
        }
    }
    @Before fun useExplicitFrameClock() {
        // Robolectric otherwise auto-advances time whenever a vsync is posted, completing a
        // whole animation inside idleFor. Pause vsync to observe actual intermediate frames.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @Test fun draggingAwayFromOriginImmediatelyFollowsFingerWithoutRecapture() {
        val anchor=bounds.point(OverlayDock.RIGHT_UPPER)
        var displayed=anchor
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,anchor,32f)
        for(distance in listOf(8,16,24,40,60)) {
            val finger=anchor.copy(x=anchor.x-distance)
            motion.move(finger.x,finger.y)
            advance(300)
            assertEquals(finger,displayed)
        }
        motion.close()
    }

    @Test fun approachingAnotherDockHasLimitedPullAndKeepsFollowing() {
        val anchor=bounds.point(OverlayDock.LEFT_CENTER)
        var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
        val finger=anchor.copy(x=anchor.x+24)
        motion.move(finger.x,finger.y)
        assertEquals(finger,displayed)
        advance(200)
        assertTrue(displayed.x<finger.x&&displayed.x>anchor.x)
        assertTrue(finger.x-displayed.x<=12)
        val previous=displayed
        motion.move(finger.x+8,finger.y)
        assertEquals(8,displayed.x-previous.x)
        // Leaving the field resumes tracking immediately with at most a small residual offset.
        val far=anchor.copy(x=anchor.x+100)
        motion.move(far.x,far.y)
        assertTrue(far.x-displayed.x in 0..12)
        advance(120)
        assertEquals(far,displayed)
        motion.close()
    }

    @Test fun originalDockBecomesEligibleAfterLeavingAndReentering() {
        val anchor=bounds.point(OverlayDock.RIGHT_UPPER)
        var displayed=anchor
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,anchor,32f)
        motion.move(anchor.x-80,anchor.y)
        motion.move(anchor.x-24,anchor.y)
        advance(200)
        assertTrue(displayed.x>anchor.x-24&&displayed.x<anchor.x)
        motion.close()
    }

    @Test fun fastPassDoesNotCaptureAndSlowingEnablesAttraction() {
        val anchor=bounds.point(OverlayDock.LEFT_CENTER)
        val finger=anchor.copy(x=anchor.x+24)
        var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
        motion.move(finger.x,finger.y,1200f,0f)
        advance(200)
        assertEquals(finger,displayed)
        motion.move(finger.x,finger.y,0f,0f)
        advance(200)
        assertTrue(displayed.x<finger.x&&displayed.x>anchor.x)
        motion.close()
    }

    @Test fun attractionUsesScreenDensityForBothSpeedAndPull() {
        val scaled=OverlayPlacement(8,248,2152,4712,96,632,2160,4800,64,24)
        val anchor=scaled.point(OverlayDock.LEFT_CENTER)
        val finger=anchor.copy(x=anchor.x+48)
        var displayed=scaled.point(OverlayDock.RIGHT_UPPER)
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(scaled,OverlayDock.RIGHT_UPPER,displayed,64f)
        motion.move(finger.x,finger.y,1000f,0f) // 500dp/s still has weak attraction.
        advance(200)
        assertTrue(displayed.x<finger.x&&displayed.x>anchor.x)
        assertTrue(finger.x-displayed.x<=24)
        motion.close()
    }

    @Test fun releaseAnimatesToExactlyOneDock() {
        var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
        var completed=false
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
        motion.move(100,1800)
        val from=displayed
        val dock=motion.settle {completed=true}
        assertEquals(OverlayDock.LEFT_LOWER,dock)
        assertEquals(from,displayed)
        assertFalse(completed)
        advance(90)
        assertNotEquals(from,displayed)
        assertNotEquals(bounds.point(dock),displayed)
        advance(800)
        assertTrue(completed)
        assertEquals(bounds.point(dock),displayed)
        motion.close()
    }

    @Test fun closingCancelsPendingFramesAndCompletion() {
        var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
        var writes=0
        var completed=false
        val motion=OverlayDockMotion {displayed=it;writes++}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
        motion.move(100,1800)
        motion.settle {completed=true}
        advance(60)
        motion.close()
        val count=writes
        advance(500)
        assertEquals(count,writes)
        assertFalse(completed)
    }

    @Test fun newCenterDocksKeepLimitedAttractionAndExactSettle() {
        for(center in listOf(OverlayDock.LEFT_CENTER,OverlayDock.RIGHT_CENTER)) {
            var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
            var completed=false
            val motion=OverlayDockMotion {displayed=it}
            motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
            val anchor=bounds.point(center)
            motion.move(anchor.x+(if(center==OverlayDock.LEFT_CENTER)24 else -24),anchor.y)
            advance(300)
            assertNotEquals(anchor,displayed)
            assertEquals(center,motion.settle {completed=true})
            advance(800)
            assertTrue(completed)
            assertEquals(anchor,displayed)
            motion.close()
        }
    }

    @Test fun temporaryAttractionDoesNotReplaceGestureOriginForFinalTie() {
        var displayed=bounds.point(OverlayDock.RIGHT_UPPER)
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,displayed,32f)
        val temporary=bounds.point(OverlayDock.LEFT_UPPER)
        motion.move(temporary.x,temporary.y)
        advance(300)
        assertEquals(temporary,displayed)
        val left=bounds.point(OverlayDock.LEFT_CENTER)
        val right=bounds.point(OverlayDock.RIGHT_CENTER)
        motion.move((left.x+right.x)/2,left.y)
        assertEquals(OverlayDock.RIGHT_CENTER,motion.settle {})
        advance(800)
        assertEquals(right,displayed)
        motion.close()
    }

    @Test fun releaseVelocityChangesMotionWithoutChangingNearestDockOrOvershooting() {
        val finger=bounds.point(OverlayDock.LEFT_CENTER).copy(x=200)
        var slow=finger
        var fast=finger
        val still=OverlayDockMotion {slow=it}
        val moving=OverlayDockMotion {fast=it}
        val origin=bounds.point(OverlayDock.RIGHT_UPPER)
        still.begin(bounds,OverlayDock.RIGHT_UPPER,origin,32f)
        moving.begin(bounds,OverlayDock.RIGHT_UPPER,origin,32f)
        still.move(finger.x,finger.y)
        moving.move(finger.x,finger.y,-1000f,0f)
        val dock=still.settle {}
        assertEquals(dock,moving.settle {})
        advance(64)
        assertTrue(fast.x<slow.x)
        val anchor=bounds.point(dock)
        repeat(45) {advance(16);assertTrue(fast.x>=anchor.x)}
        assertEquals(anchor,slow)
        assertEquals(anchor,fast)
        still.close();moving.close()
    }

    @Test fun cancellationDoesNotInheritFlingVelocity() {
        val origin=bounds.point(OverlayDock.RIGHT_UPPER)
        var stopped=origin
        var cancelled=origin
        val still=OverlayDockMotion {stopped=it}
        val moving=OverlayDockMotion {cancelled=it}
        still.begin(bounds,OverlayDock.RIGHT_UPPER,origin,32f)
        moving.begin(bounds,OverlayDock.RIGHT_UPPER,origin,32f)
        still.move(200,1800)
        moving.move(200,1800,-1500f,500f)
        still.settle {}
        moving.settle(cancelled=true) {}
        advance(80)
        assertEquals(stopped,cancelled)
        still.close();moving.close()
    }

    @Test fun regrabbingDuringSpringCancelsOldCompletionAndPreservesVisiblePosition() {
        val anchor=bounds.point(OverlayDock.RIGHT_UPPER)
        var displayed=anchor
        var oldCompleted=false
        val motion=OverlayDockMotion {displayed=it}
        motion.begin(bounds,OverlayDock.RIGHT_UPPER,anchor,32f)
        motion.move(100,1800)
        val dock=motion.settle {oldCompleted=true}
        advance(60)
        val grabbed=displayed
        motion.begin(bounds,dock,grabbed,32f)
        advance(800)
        assertFalse(oldCompleted)
        assertEquals(grabbed,displayed)
        motion.move(grabbed.x+8,grabbed.y)
        assertEquals(grabbed.x+8,displayed.x)
        motion.close()
    }
}
