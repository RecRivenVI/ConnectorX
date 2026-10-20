package io.github.recrivenvi.connectorx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlobalScrollLeaseTest {
    @Test public void expiryAndLateHeartbeatNeverResumeScrolling() {
        GlobalScrollLease lease=new GlobalScrollLease();
        assertEquals(1,lease.set(1,0,true));
        assertEquals(1,lease.renew(1_000_000_000L,true));
        assertEquals(0,lease.renew(3_000_000_000L,true));
        assertEquals(0,lease.renew(3_500_000_000L,true));
        assertEquals(-1,lease.set(-1,3_500_000_000L,true));
    }
    @Test public void screenLockStopsAndUnlockDoesNotResume() {
        GlobalScrollLease lease=new GlobalScrollLease();
        lease.set(1,0,true);
        assertEquals(0,lease.current(50,false));
        assertEquals(0,lease.renew(100,true));
        assertEquals(0,lease.set(-1,200,false));
    }
    @Test public void stopAndDirectionChangeAreImmediate() {
        GlobalScrollLease lease=new GlobalScrollLease();
        lease.set(1,0,true);
        assertEquals(-1,lease.set(-1,1,true));
        assertEquals(0,lease.set(0,2,true));
        assertEquals(0,lease.current(3,true));
        assertThrows(IllegalArgumentException.class,()->lease.set(2,4,true));
    }
}
