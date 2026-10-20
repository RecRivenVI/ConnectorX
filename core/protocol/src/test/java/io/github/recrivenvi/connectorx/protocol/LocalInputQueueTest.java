package io.github.recrivenvi.connectorx.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalInputQueueTest {
    @Test public void capacityMustBePositiveAndNullCannotMasqueradeAsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new LocalInputQueue<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LocalInputQueue<>(-1));
        LocalInputQueue<String> queue = new LocalInputQueue<>(1);
        assertThrows(NullPointerException.class, () -> queue.offer(null, false));
        assertEquals(0, queue.size());
        assertEquals(0, queue.getRejectedCount());
        assertNull(queue.poll());
    }

    @Test public void adjacentMovesCoalesceWithoutCrossingMultitouchTransitions() {
        LocalInputQueue<String> queue = new LocalInputQueue<>(8);
        assertTrue(queue.offer("DOWN-0", false));
        assertTrue(queue.offer("MOVE-0-a", true));
        assertTrue(queue.offer("MOVE-0-b", true));
        assertTrue(queue.offer("DOWN-1", false));
        assertTrue(queue.offer("MOVE-01-a", true));
        assertTrue(queue.offer("MOVE-01-b", true));
        assertTrue(queue.offer("UP-0", false));
        assertTrue(queue.offer("MOVE-1", true));
        assertTrue(queue.offer("UP-1", false));
        assertTrue(queue.offer("CANCEL", false));
        assertEquals(8, queue.size());
        assertEquals(2, queue.getMergedCount());
        assertEquals(0, queue.getRejectedCount());
        assertEquals(Arrays.asList("DOWN-0", "MOVE-0-b", "DOWN-1", "MOVE-01-b",
                "UP-0", "MOVE-1", "UP-1", "CANCEL"), drain(queue));
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test public void fullTailMoveCanBeReplacedButCannotBeEvictedForUp() {
        LocalInputQueue<String> queue = new LocalInputQueue<>(2);
        assertTrue(queue.offer("DOWN", false));
        assertTrue(queue.offer("MOVE-a", true));
        assertTrue(queue.offer("MOVE-b", true));
        assertFalse(queue.offer("UP", false));
        assertFalse(queue.offer("CANCEL", false));
        assertEquals(2, queue.size());
        assertEquals(1, queue.getMergedCount());
        assertEquals(2, queue.getRejectedCount());
        assertEquals(Arrays.asList("DOWN", "MOVE-b"), drain(queue));
    }

    @Test public void fullQueueNeverDeletesOlderMoveAcrossAnUpOrDown() {
        LocalInputQueue<String> queue = new LocalInputQueue<>(3);
        assertTrue(queue.offer("DOWN", false));
        assertTrue(queue.offer("MOVE-old", true));
        assertTrue(queue.offer("UP", false));
        assertFalse(queue.offer("MOVE-new", true));
        assertFalse(queue.offer("DOWN-next", false));
        assertEquals(0, queue.getMergedCount());
        assertEquals(2, queue.getRejectedCount());
        assertEquals(Arrays.asList("DOWN", "MOVE-old", "UP"), drain(queue));
    }

    @Test public void capacityOneProtectsEveryTransitionAndSupportsTailReplacement() {
        LocalInputQueue<String> queue = new LocalInputQueue<>(1);
        assertTrue(queue.offer("DOWN", false));
        assertFalse(queue.offer("MOVE", true));
        assertFalse(queue.offer("UP", false));
        assertEquals("DOWN", queue.poll());
        assertTrue(queue.offer("MOVE-a", true));
        assertTrue(queue.offer("MOVE-b", true));
        assertEquals("MOVE-b", queue.poll());
        assertTrue(queue.offer("UP", false));
        assertFalse(queue.offer("DOWN-next", false));
        assertEquals("UP", queue.poll());
        assertEquals(1, queue.getMergedCount());
        assertEquals(3, queue.getRejectedCount());
    }

    @Test public void repeatedWraparoundPreservesTailAndFifoOrder() {
        LocalInputQueue<Integer> queue = new LocalInputQueue<>(3);
        for (int i = 0; i < 100; i++) {
            assertTrue(queue.offer(i * 10, false));
            assertTrue(queue.offer(i * 10 + 1, true));
            assertEquals(Integer.valueOf(i * 10), queue.poll());
            assertTrue(queue.offer(i * 10 + 2, true));
            assertTrue(queue.offer(i * 10 + 3, false));
            assertTrue(queue.offer(i * 10 + 4, true));
            assertTrue(queue.offer(i * 10 + 5, true));
            assertFalse(queue.offer(i * 10 + 6, false));
            assertEquals(Arrays.asList(i * 10 + 2, i * 10 + 3, i * 10 + 5), drain(queue));
        }
        assertEquals(200, queue.getMergedCount());
        assertEquals(100, queue.getRejectedCount());
    }

    @Test public void clearDropsPendingGestureAndRetainsCumulativeMetrics() {
        LocalInputQueue<String> queue = new LocalInputQueue<>(2);
        assertTrue(queue.offer("DOWN", false));
        assertTrue(queue.offer("MOVE-a", true));
        assertEquals("DOWN", queue.poll());
        assertTrue(queue.offer("MOVE-b", true));
        assertTrue(queue.offer("UP", false));
        assertFalse(queue.offer("DOWN-next", false));
        queue.clear();
        queue.clear();
        assertEquals(0, queue.size());
        assertNull(queue.poll());
        assertEquals(1, queue.getMergedCount());
        assertEquals(1, queue.getRejectedCount());
        assertTrue(queue.offer("CANCEL-after-explicit-reset", false));
        assertTrue(queue.offer("DOWN-new", false));
        assertEquals(Arrays.asList("CANCEL-after-explicit-reset", "DOWN-new"), drain(queue));
    }

    @Test public void deltasAndOtherNonMoveEventsAreNeverMerged() {
        LocalInputQueue<Integer> queue = new LocalInputQueue<>(4);
        for (int i = 0; i < 4; i++) assertTrue(queue.offer(1, false));
        assertFalse(queue.offer(1, false));
        assertEquals(Arrays.asList(1, 1, 1, 1), drain(queue));
        assertEquals(0, queue.getMergedCount());
        assertEquals(1, queue.getRejectedCount());
    }

    @Test public void concurrentOffersRespectCapacityAndAccountForEveryRejection() throws Exception {
        LocalInputQueue<Integer> queue = new LocalInputQueue<>(8);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Set<Integer>>> results = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int base = worker * 500;
                results.add(pool.submit(() -> {
                    start.await();
                    Set<Integer> accepted = new HashSet<>();
                    for (int i = 0; i < 500; i++) {
                        if (queue.offer(base + i, false)) accepted.add(base + i);
                    }
                    return accepted;
                }));
            }
            start.countDown();
            Set<Integer> accepted = new HashSet<>();
            for (Future<Set<Integer>> result : results) accepted.addAll(result.get(5, TimeUnit.SECONDS));
            assertEquals(8, accepted.size());
            assertEquals(8, queue.size());
            assertEquals(2000 - 8, queue.getRejectedCount());
            assertEquals(0, queue.getMergedCount());
            List<Integer> delivered = drain(queue);
            assertEquals(8, delivered.size());
            assertEquals(accepted, new HashSet<>(delivered));
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> List<T> drain(LocalInputQueue<T> queue) {
        List<T> items = new ArrayList<>();
        T next;
        while ((next = queue.poll()) != null) items.add(next);
        return items;
    }
}
