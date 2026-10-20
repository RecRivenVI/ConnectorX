package io.github.recrivenvi.connectorx.protocol;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenMathTest {
    @Test public void rectangularCutoutMayStartMidwayAlongTopEdge() {
        assertTrue(ScreenMath.isRectangle(new float[]{600,0, 566,0, 566,144, 634,144, 634,0, 600,0}));
        assertTrue(ScreenMath.isRectangle(new float[]{720,0, 678,0, 678,168, 762,168, 762,0, 720,0}));
    }
    @Test public void circularAndConcaveMasksAreNotRectangularReservations() {
        assertFalse(ScreenMath.isRectangle(new float[]{0,2, 2,0, 4,2, 2,4, 0,2}));
        assertFalse(ScreenMath.isRectangle(new float[]{0,0, 4,0, 4,4, 2,4, 2,2, 0,2}));
    }
    @Test public void allRotationsRoundTripIncludingOffCenterHole() {
        for (int rotation = 0; rotation < 4; rotation++) {
            float[] p = ScreenMath.toLogical(720, 92, 1440, 3200, rotation);
            float[] restored = ScreenMath.toNatural(p[0], p[1], 1440, 3200, rotation);
            assertArrayEquals(new float[]{720, 92}, restored, 0.001f);
        }
    }
    @Test public void rotation90MovesTopHoleToLeft() {
        assertArrayEquals(new float[]{92, 720}, ScreenMath.toLogical(720, 92, 1440, 3200, 1), 0.001f);
    }
    @Test public void frameIsCenteredOutsideContentWithSymmetricMargin() {
        float[] layout = ScreenMath.fit(1200, 2608, 216, 12, 1440, 3200);
        assertEquals(216, layout[3], 0.001f);
        assertEquals(1440f / 3200, layout[0] / layout[1], 0.001f);
        assertEquals(1200, 2 * layout[2] + layout[0] + 24, 0.001f);
        assertEquals(2608, 2 * layout[3] + layout[1] + 24, 0.001f);
    }
    @Test public void wideScreensFitWithoutStretchingOrOverflow() {
        float[] layout = ScreenMath.fit(1200, 2608, 216, 12, 3200, 1440);
        assertEquals(1176, layout[0], 0.001f);
        assertEquals(3200f / 1440, layout[0] / layout[1], 0.001f);
        assertTrue(layout[3] > 216);
    }
    @Test public void tinyViewportDoesNotCreateNegativeContent() {
        float[] layout = ScreenMath.fit(100, 100, 70, 12, 1440, 3200);
        assertEquals(0, layout[0], 0); assertEquals(0, layout[1], 0);
    }
}
