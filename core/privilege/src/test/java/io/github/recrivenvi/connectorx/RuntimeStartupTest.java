package io.github.recrivenvi.connectorx;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeStartupTest {
    @Test public void deadChildFailsBeforePolling() {
        try {
            RuntimeStartup.await(() -> false, () -> 0, 10_000, timeout -> { fail("Must not wait on a dead child"); return null; });
            fail("Must report child exit");
        } catch (Exception expected) { assertTrue(expected.getMessage().contains("已退出")); }
    }
    @Test public void missingSecondChannelUsesRemainingSharedDeadline() throws Exception {
        AtomicLong clock = new AtomicLong(0);
        String first = RuntimeStartup.await(() -> true, clock::get, 10_000, timeout -> { clock.set(9_950); return "video"; });
        assertEquals("video", first);
        AtomicInteger waits = new AtomicInteger();
        try {
            RuntimeStartup.await(() -> true, clock::get, 10_000, timeout -> {
                assertEquals(50, timeout); waits.incrementAndGet(); clock.addAndGet(timeout); return null;
            });
            fail("Must time out without resetting the deadline");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("超时")); }
        assertEquals(1, waits.get());
    }
    @Test public void pollFailurePropagatesAndNextAttemptCanSucceed() throws Exception {
        IOException failure = new IOException("socket closed");
        try {
            RuntimeStartup.await(() -> true, () -> 0, 10_000, timeout -> { throw failure; });
            fail();
        } catch (IOException expected) { assertSame(failure, expected); }
        assertEquals("control", RuntimeStartup.await(() -> true, () -> 0, 10_000, timeout -> "control"));
    }
}
