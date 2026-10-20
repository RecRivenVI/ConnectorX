package io.github.recrivenvi.connectorx.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalTouchFrameTest {
    @Test public void multitouchTransitionsRoundTripWithLiftedPointerAndStableUptime() {
        LocalTouchFrame[] gesture = {
                frame(LocalTouchFrame.DOWN, 31, 100, new int[]{31}),
                frame(LocalTouchFrame.DOWN, 7, 110, new int[]{31, 7}),
                frame(LocalTouchFrame.MOVE, -1, 120, new int[]{7, 31}),
                frame(LocalTouchFrame.UP, 31, 130, new int[]{7, 31}),
                frame(LocalTouchFrame.MOVE, 7, 140, new int[]{7}),
                frame(LocalTouchFrame.UP, 7, 150, new int[]{7})
        };
        for (LocalTouchFrame original : gesture) {
            LocalTouchFrame decoded = LocalTouchFrame.decode(original.encode());
            assertEquals(original.action, decoded.action);
            assertEquals(original.changedId, decoded.changedId);
            assertEquals(original.eventTimeNanos, decoded.eventTimeNanos);
            assertEquals(100, decoded.downTimeNanos);
            assertArrayEquals(original.getIds(), decoded.getIds());
            assertArrayEquals(original.getXs(), decoded.getXs(), 0f);
            assertArrayEquals(original.getYs(), decoded.getYs(), 0f);
            assertEquals(original.action, decoded.getAction());
            assertEquals(original.changedId, decoded.getChangedId());
            assertEquals(original.eventTimeNanos, decoded.getEventTimeNanos());
            assertEquals(100, decoded.getDownTimeNanos());
            assertEquals(original.getPointerCount(), decoded.getPointerCount());
        }
    }

    @Test public void fixedBinderFixtureDefinesEndiannessFieldOrderAndVersion() throws Exception {
        byte[] expected = HexFormat.of().parseHex(
                "4c54463100000002ffffffff00000001"
                + "00000000000000640000000000000032"
                + "0000001fbf80000040200000");
        LocalTouchFrame frame = new LocalTouchFrame(LocalTouchFrame.MOVE, -1, 100, 50,
                new int[]{31}, new float[]{-1f}, new float[]{2.5f});
        assertArrayEquals(expected, frame.encode());
        LocalTouchFrame decoded = LocalTouchFrame.decode(expected);
        assertEquals(31, decoded.getId(0));
        assertEquals(-1f, decoded.getX(0), 0f);
        assertEquals(2.5f, decoded.getY(0), 0f);
        assertEquals(100, decoded.getEventTimeNanos());
        assertEquals(50, decoded.getDownTimeNanos());
        assertThrows(IOException.class, () -> Wire.validate(expected));
    }

    @Test public void cancelSupportsActiveSnapshotAndEmptyReleaseAll() {
        for (int changedId : new int[]{-1, 5}) {
            LocalTouchFrame active = frame(LocalTouchFrame.CANCEL, changedId, 150, new int[]{5, 31});
            LocalTouchFrame decoded = LocalTouchFrame.decode(active.encode());
            assertEquals(LocalTouchFrame.CANCEL, decoded.action);
            assertEquals(changedId, decoded.changedId);
            assertArrayEquals(new int[]{5, 31}, decoded.getIds());
        }
        LocalTouchFrame empty = new LocalTouchFrame(LocalTouchFrame.CANCEL, -1, 0, 0,
                new int[0], new float[0], new float[0]);
        assertEquals(32, empty.encode().length);
        LocalTouchFrame decoded = LocalTouchFrame.decode(empty.encode());
        assertEquals(LocalTouchFrame.CANCEL, decoded.action);
        assertEquals(-1, decoded.changedId);
        assertEquals(0, decoded.getPointerCount());
    }

    @Test public void acceptsTenPointersAndFiniteOutsideCoordinatesWithoutClamping() {
        int[] ids = {31, 0, 2, 4, 6, 8, 10, 12, 14, 16};
        float[] xs = {-Float.MAX_VALUE, -0f, -10.25f, Float.MIN_VALUE, 5000, 1, 2, 3, 4, 5};
        float[] ys = {Float.MAX_VALUE, 0f, 30000.75f, -Float.MIN_VALUE, -5000, 5, 4, 3, 2, 1};
        LocalTouchFrame frame = new LocalTouchFrame(LocalTouchFrame.MOVE, -1,
                Long.MAX_VALUE, Long.MAX_VALUE, ids, xs, ys);
        assertEquals(LocalTouchFrame.MAX_ENCODED_BYTES, frame.encode().length);
        LocalTouchFrame decoded = LocalTouchFrame.decode(frame.encode());
        assertEquals(10, decoded.getPointerCount());
        assertArrayEquals(ids, decoded.getIds());
        assertArrayEquals(xs, decoded.getXs(), 0f);
        assertArrayEquals(ys, decoded.getYs(), 0f);
        assertEquals(Float.floatToRawIntBits(-0f), Float.floatToRawIntBits(decoded.getX(1)));
        assertEquals(Long.MAX_VALUE, decoded.eventTimeNanos);
    }

    @Test public void arrayAliasesCannotChangeValidatedPayloadOrEncoding() {
        int[] ids = {5};
        float[] xs = {2f}, ys = {3f};
        LocalTouchFrame frame = new LocalTouchFrame(LocalTouchFrame.DOWN, 5, 0, 0, ids, xs, ys);
        byte[] original = frame.encode();
        ids[0] = 31; xs[0] = Float.NaN; ys[0] = Float.POSITIVE_INFINITY;
        assertArrayEquals(new int[]{5}, frame.getIds());
        assertArrayEquals(new float[]{2f}, frame.getXs(), 0f);
        assertArrayEquals(new float[]{3f}, frame.getYs(), 0f);
        frame.getIds()[0] = 7; frame.getXs()[0] = 10; frame.getYs()[0] = 20;
        byte[] disposable = frame.encode();
        Arrays.fill(disposable, (byte) 0);
        assertArrayEquals(original, frame.encode());
        assertEquals(5, frame.getId(0));
        assertEquals(2f, frame.getX(0), 0f);
        assertEquals(3f, frame.getY(0), 0f);
        LocalTouchFrame decoded = LocalTouchFrame.decode(original);
        Arrays.fill(original, (byte) 0);
        assertEquals(5, decoded.getId(0));
        assertEquals(5, LocalTouchFrame.decode(decoded.encode()).getId(0));
    }

    @Test public void acceptedSubsetBatchesPreserveTransitionsAndUpIncludesLiftedPointer() {
        // Pure-Java producer model: platform arrays include a finger rejected at its DOWN.
        TouchOrigins origins = new TouchOrigins();
        LocalInputQueue<LocalTouchFrame> queue = new LocalInputQueue<>(6);
        assertFalse(origins.down(2, false));
        assertTrue(origins.down(31, true));
        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.DOWN, 31, 100,
                new int[]{2, 31}, new float[]{50, 20}, new float[]{50, 20}), false));
        assertTrue(origins.down(7, true));
        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.DOWN, 7, 110,
                new int[]{2, 31, 7}, new float[]{50, 20, 30}, new float[]{50, 20, 30}), false));

        // Rejected finger 2 has moved inside; accepted 31 has moved outside. Neither changes admission.
        assertFalse(origins.move(2));
        assertTrue(origins.move(31));
        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.MOVE, -1, 120,
                new int[]{2, 31, 7}, new float[]{5, -100, 40}, new float[]{5, 3000, 40}), true));
        // Pointer order can change; changedId is a pointer id, never a platform-array index.
        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.MOVE, -1, 130,
                new int[]{7, 2, 31}, new float[]{45, 5, -150}, new float[]{45, 5, 4000}), true));
        LocalTouchFrame up31 = acceptedSnapshot(origins, LocalTouchFrame.UP, 31, 140,
                new int[]{7, 2, 31}, new float[]{45, 5, -160}, new float[]{45, 5, 4100});
        assertTrue(queue.offer(up31, false));
        assertTrue(origins.up(31)); // Remove only AFTER the UP snapshot is built.

        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.MOVE, -1, 150,
                new int[]{2, 7}, new float[]{5, -20}, new float[]{5, 4200}), true));
        assertTrue(queue.offer(acceptedSnapshot(origins, LocalTouchFrame.UP, 7, 160,
                new int[]{2, 7}, new float[]{5, -25}, new float[]{5, 4300}), false));
        assertTrue(origins.up(7));
        assertFalse(origins.up(2));

        int[] actions = {0, 0, 2, 1, 2, 1};
        int[] changed = {31, 7, -1, 31, -1, 7};
        int[][] ids = {{31}, {31, 7}, {7, 31}, {7, 31}, {7}, {7}};
        long[] times = {100, 110, 130, 140, 150, 160};
        assertEquals(1, queue.getMergedCount());
        assertEquals(0, queue.getRejectedCount());
        for (int i = 0; i < actions.length; i++) {
            LocalTouchFrame decoded = LocalTouchFrame.decode(queue.poll().encode());
            assertEquals(actions[i], decoded.action);
            assertEquals(changed[i], decoded.changedId);
            assertArrayEquals(ids[i], decoded.getIds());
            assertEquals(times[i], decoded.eventTimeNanos);
            assertEquals(100, decoded.downTimeNanos);
            if (i == 2) {
                assertEquals(-150f, decoded.getX(1), 0f);
                assertEquals(4000f, decoded.getY(1), 0f);
            }
            if (i == 3) assertEquals(-160f, decoded.getX(1), 0f);
        }
        assertNull(queue.poll());
    }

    @Test public void overloadCanBeExplicitlyResetAndCancelledWithParentUptimeContract() {
        TouchOrigins origins = new TouchOrigins();
        LocalInputQueue<LocalTouchFrame> queue = new LocalInputQueue<>(1);
        assertTrue(origins.down(31, true));
        assertFalse(origins.down(2, false));
        assertTrue(queue.offer(frame(LocalTouchFrame.MOVE, -1, 110, new int[]{31}), true));
        assertFalse(queue.offer(frame(LocalTouchFrame.UP, 31, 120, new int[]{31}), false));
        // This models an explicit caller failure path, not automatic cancellation by the queue.
        queue.clear();
        origins.clear();
        long uptimeNanos = 123_456_789L * 1_000_000L;
        LocalTouchFrame cancel = new LocalTouchFrame(3, -1, uptimeNanos, uptimeNanos,
                new int[0], new float[0], new float[0]);
        assertTrue(queue.offer(cancel, false));
        LocalTouchFrame delivered = LocalTouchFrame.decode(queue.poll().encode());
        assertEquals(3, delivered.action);
        assertEquals(-1, delivered.changedId);
        assertEquals(uptimeNanos, delivered.eventTimeNanos);
        assertEquals(uptimeNanos, delivered.downTimeNanos);
        assertEquals(0, delivered.getPointerCount());
        assertEquals(0, delivered.getIds().length);
        assertEquals(0, delivered.getXs().length);
        assertEquals(0, delivered.getYs().length);
        assertEquals(1, queue.getRejectedCount());
        assertFalse(origins.move(31));
        assertFalse(origins.move(2));
        assertTrue(origins.down(2, true)); // Reuse is allowed only after a fresh accepted DOWN.
    }

    @Test public void rejectsInvalidActionsAndChangedPointers() {
        for (int action : new int[]{-1, 4, 5, 6, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> frame(action, 0, 100, new int[]{0}));
        }
        for (int action : new int[]{LocalTouchFrame.DOWN, LocalTouchFrame.UP}) {
            for (int changedId : new int[]{-1, 1, 32}) {
                assertThrows(IllegalArgumentException.class,
                        () -> frame(action, changedId, 100, new int[]{0}));
            }
        }
        for (int action : new int[]{LocalTouchFrame.MOVE, LocalTouchFrame.CANCEL}) {
            for (int changedId : new int[]{-2, 1, 32}) {
                assertThrows(IllegalArgumentException.class,
                        () -> frame(action, changedId, 100, new int[]{0}));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> frame(LocalTouchFrame.CANCEL, 0, 100, new int[0]));
    }

    @Test public void rejectsMissingDuplicateAndOutOfRangePointers() {
        for (int action : new int[]{LocalTouchFrame.DOWN, LocalTouchFrame.UP, LocalTouchFrame.MOVE}) {
            assertThrows(IllegalArgumentException.class, () -> frame(action, -1, 100, new int[0]));
        }
        for (int[] ids : new int[][]{{0, 0}, {31, 31}, {-1}, {32}, {Integer.MAX_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> frame(LocalTouchFrame.MOVE, -1, 100, ids));
        }
        int[] eleven = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThrows(IllegalArgumentException.class, () -> frame(LocalTouchFrame.MOVE, -1, 100, eleven));
    }

    @Test public void rejectsNullMismatchedArraysAndNonfiniteCoordinates() {
        assertInvalidArrays(null, new float[0], new float[0]);
        assertInvalidArrays(new int[]{0}, null, new float[]{1});
        assertInvalidArrays(new int[]{0}, new float[]{1}, null);
        assertInvalidArrays(new int[]{0}, new float[0], new float[]{1});
        assertInvalidArrays(new int[]{0}, new float[]{1}, new float[]{1, 2});
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertInvalidArrays(new int[]{0}, new float[]{bad}, new float[]{0});
            assertInvalidArrays(new int[]{0}, new float[]{0}, new float[]{bad});
        }
    }

    @Test public void rejectsNegativeAndReversedTimes() {
        for (long[] times : new long[][]{{-1, 0}, {0, -1}, {10, 11}, {Long.MAX_VALUE, Long.MIN_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> new LocalTouchFrame(LocalTouchFrame.DOWN,
                    0, times[0], times[1], new int[]{0}, new float[]{0}, new float[]{0}));
        }
    }

    @Test public void decodeRejectsTruncationTrailingDataOversizeAndHostileCounts() {
        byte[] valid = frame(LocalTouchFrame.UP, 31, 150, new int[]{0, 31}).encode();
        assertThrows(IllegalArgumentException.class, () -> LocalTouchFrame.decode(null));
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IllegalArgumentException.class, () -> LocalTouchFrame.decode(truncated));
        }
        assertThrows(IllegalArgumentException.class,
                () -> LocalTouchFrame.decode(Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> LocalTouchFrame.decode(new byte[LocalTouchFrame.MAX_ENCODED_BYTES + 1]));
        for (int count : new int[]{-1, 0, 1, 3, 11, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertBadInt(valid, 12, count);
        }
    }

    @Test public void decodeValidatesVersionActionIdsTimesAndCoordinates() {
        byte[] valid = frame(LocalTouchFrame.UP, 31, 150, new int[]{0, 31}).encode();
        assertBadInt(valid, 0, 0x4c544632);
        assertBadInt(valid, 4, 4);
        assertBadInt(valid, 8, -1);
        assertBadInt(valid, 8, 1);
        assertBadInt(valid, 32, 31); // Duplicate id 31.
        assertBadInt(valid, 32, 32);
        assertBadInt(valid, 32, -1);
        for (int offset : new int[]{36, 40, 48, 52}) {
            for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                assertBadInt(valid, offset, Float.floatToRawIntBits(value));
            }
        }
        for (long[] field : new long[][]{{16, -1}, {24, -1}, {24, 151}}) {
            byte[] bad = valid.clone();
            ByteBuffer.wrap(bad).putLong((int) field[0], field[1]);
            assertThrows(IllegalArgumentException.class, () -> LocalTouchFrame.decode(bad));
        }
        byte[] cancel = frame(LocalTouchFrame.CANCEL, -1, 100, new int[0]).encode();
        assertBadInt(cancel, 4, LocalTouchFrame.MOVE);
        assertBadInt(cancel, 8, 0);
    }

    private static LocalTouchFrame frame(int action, int changedId, long eventTime, int[] ids) {
        float[] xs = new float[ids.length], ys = new float[ids.length];
        for (int i = 0; i < ids.length; i++) {
            xs[i] = -100.25f - i;
            ys[i] = 30000.5f + i;
        }
        return new LocalTouchFrame(action, changedId, eventTime, 100, ids, xs, ys);
    }

    /** Test-only producer model filtering a platform snapshot through DOWN-origin admission. */
    private static LocalTouchFrame acceptedSnapshot(TouchOrigins origins, int action, int changedId,
                                                    long eventTime, int[] ids, float[] xs, float[] ys) {
        int count = 0;
        int[] acceptedIds = new int[ids.length];
        float[] acceptedXs = new float[ids.length], acceptedYs = new float[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (origins.move(ids[i])) {
                acceptedIds[count] = ids[i];
                acceptedXs[count] = xs[i];
                acceptedYs[count] = ys[i];
                count++;
            }
        }
        return new LocalTouchFrame(action, changedId, eventTime, 100,
                Arrays.copyOf(acceptedIds, count), Arrays.copyOf(acceptedXs, count), Arrays.copyOf(acceptedYs, count));
    }

    private static void assertInvalidArrays(int[] ids, float[] xs, float[] ys) {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalTouchFrame(LocalTouchFrame.MOVE, -1, 100, 100, ids, xs, ys));
    }

    private static void assertBadInt(byte[] valid, int offset, int value) {
        byte[] bad = valid.clone();
        ByteBuffer.wrap(bad).putInt(offset, value);
        assertThrows(IllegalArgumentException.class, () -> LocalTouchFrame.decode(bad));
    }
}
