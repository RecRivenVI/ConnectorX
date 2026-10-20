package io.github.recrivenvi.connectorx.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class WheelTimelineTest {
    @Test public void firstTickWaitsOnePeriodAndDoesNotJumpAfterDelayedStart() {
        WheelTimeline wheel=new WheelTimeline();
        wheel.start(5_000_000_000L,100);
        assertNull(wheel.poll(5_009_999_999L));
        WheelTimeline.Tick tick=wheel.poll(5_500_000_000L);
        assertEquals(10_000_000L,tick.elapsedNanos);
        assertEquals(49,tick.skippedTicks);
        assertEquals(1.6f,tick.units(160),0.00001f);
        assertNull(wheel.poll(5_500_000_000L));
    }

    @Test public void absoluteDeadlinesDoNotAccumulateHandlerMillisecondRounding() {
        WheelTimeline wheel=new WheelTimeline();
        long start=7_000_000_000L;
        wheel.start(start,120);
        long period=wheel.periodNanos();
        for(int i=1;i<=10_000;i++) {
            long deadline=start+i*period;
            assertEquals(deadline,wheel.nextDeadlineNanos());
            long roundedUpMillis=(deadline+999_999L)/1_000_000L;
            WheelTimeline.Tick tick=wheel.poll(roundedUpMillis*1_000_000L);
            assertNotNull(tick);
            assertEquals(0,tick.skippedTicks);
        }
        assertEquals(start+10_001*period,wheel.nextDeadlineNanos());
    }

    @Test public void stalledHandlerSkipsBacklogAndCapsIntegrationAtTwoPeriods() {
        WheelTimeline wheel=new WheelTimeline();wheel.start(0,100);
        assertNotNull(wheel.poll(10_000_000L));
        WheelTimeline.Tick late=wheel.poll(1_003_000_000L);
        assertEquals(20_000_000L,late.elapsedNanos);
        assertEquals(983_000_000L,late.latenessNanos);
        assertEquals(98,late.skippedTicks);
        assertEquals(3.2f,late.units(160),0.00001f);
        assertEquals(1_010_000_000L,wheel.nextDeadlineNanos());
        assertNull(wheel.poll(1_003_000_000L));
        WheelTimeline.Tick next=wheel.poll(1_010_000_000L);
        assertEquals(7_000_000L,next.elapsedNanos);
    }

    @Test public void unchangedSpeedIntegratesSameDistanceAcrossRefreshRates() {
        for(float speed:new float[]{20,40,80,160,320})for(float rate:new float[]{24,60,90,120,144,240}) {
            WheelTimeline wheel=new WheelTimeline();wheel.start(0,rate);
            float units=0;
            int samples=(int)rate;
            for(int i=0;i<samples;i++)units+=wheel.poll(wheel.nextDeadlineNanos()).units(speed);
            assertEquals("rate="+rate+", speed="+speed,speed,units,0.001f);
        }
    }

    @Test public void refreshChangeRebasesWithoutReplayingOldDeadlines() {
        WheelTimeline wheel=new WheelTimeline();wheel.start(0,60);
        wheel.poll(wheel.nextDeadlineNanos());
        long changedAt=5_000_000_000L;
        wheel.start(changedAt,240);
        assertNull(wheel.poll(changedAt));
        WheelTimeline.Tick first=wheel.poll(wheel.nextDeadlineNanos());
        assertEquals(wheel.periodNanos(),first.elapsedNanos);
        assertEquals(0,first.skippedTicks);
    }

    @Test public void clampsRefreshAndFallsBackOnlyForUnknownRate() {
        WheelTimeline wheel=new WheelTimeline();
        wheel.start(0,1);assertEquals(Math.round(1_000_000_000d/24),wheel.periodNanos());
        wheel.start(0,1000);assertEquals(Math.round(1_000_000_000d/240),wheel.periodNanos());
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}) {
            wheel.start(0,invalid);assertEquals(Math.round(1_000_000_000d/60),wheel.periodNanos());
        }
    }
}
