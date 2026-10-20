package io.github.recrivenvi.connectorx.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchOriginsTest {
    @Test public void outsideStartCannotEnterLater() {
        TouchOrigins t=new TouchOrigins();
        assertFalse(t.down(3,false)); assertFalse(t.move(3)); assertFalse(t.up(3));
    }
    @Test public void acceptedPointerStaysAcceptedUntilUp() {
        TouchOrigins t=new TouchOrigins();
        assertTrue(t.down(1,true)); assertTrue(t.move(1)); assertTrue(t.move(1));
        assertTrue(t.up(1)); assertFalse(t.move(1)); assertFalse(t.up(1));
    }
    @Test public void fingersHaveIndependentOriginsAndCancellationClearsAll() {
        TouchOrigins t=new TouchOrigins();
        t.down(1,true); t.down(2,false); t.down(3,true);
        assertTrue(t.move(1)); assertFalse(t.move(2)); assertTrue(t.move(3));
        t.clear(); assertFalse(t.move(1)); assertFalse(t.up(3));
        assertTrue(t.down(2,true)); // Reused pointer id starts a genuinely new touch.
    }
}
