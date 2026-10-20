package io.github.recrivenvi.connectorx.protocol;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bounded, nonblocking FIFO for one local input stream. A MOVE may replace only an adjacent
 * MOVE at the tail. Non-MOVE transitions are never removed, crossed or reordered, even when
 * full. All operations and cumulative metrics are synchronized on this queue.
 *
 * <p>Use one queue per session/ordered stream; mark only interchangeable complete MOVE
 * snapshots as move=true. Key events, DOWN, UP, CANCEL and other barriers use move=false.
 * Relative wheel deltas must not be marked as moves: replacing them would lose distance.
 * The generic queue cannot detect pointer-set, target or gesture changes; callers must
 * preserve their transition barriers and must not mutate offered items.
 *
 * <p>A false offer leaves the queue unchanged. The caller must explicitly cancel/reset the
 * gesture through its session failure path, rather than silently dropping DOWN/UP. A full
 * queue cannot guarantee space for that CANCEL; merely offering CANCEL here is insufficient.
 * clear() removes pending input, but does not release injected pointers or reset metrics.
 */
public final class LocalInputQueue<T> {
    private final Object[] items;
    private final boolean[] moves;
    private int head;
    private int size;
    private long mergedCount;
    private long rejectedCount;

    public LocalInputQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        items = new Object[capacity];
        moves = new boolean[capacity];
    }

    /** Null is invalid (NullPointerException); rejectedCount counts only full-queue refusals. */
    public synchronized boolean offer(T item, boolean move) {
        Objects.requireNonNull(item, "item");
        if (move && size > 0) {
            int tail = index(size - 1);
            if (moves[tail]) {
                items[tail] = item;
                mergedCount++;
                return true;
            }
        }
        if (size == items.length) {
            rejectedCount++;
            return false;
        }
        int tail = index(size);
        items[tail] = item;
        moves[tail] = move;
        size++;
        return true;
    }

    /** Removes the oldest item, or returns null when empty. */
    @SuppressWarnings("unchecked")
    public synchronized T poll() {
        if (size == 0) return null;
        T item = (T) items[head];
        items[head] = null;
        moves[head] = false;
        head = head == items.length - 1 ? 0 : head + 1;
        size--;
        return item;
    }

    /** Drops queued items only; cumulative merged/rejected counters are retained. */
    public synchronized void clear() {
        Arrays.fill(items, null);
        Arrays.fill(moves, false);
        head = 0;
        size = 0;
    }

    public synchronized int size() { return size; }
    public synchronized long getMergedCount() { return mergedCount; }
    public synchronized long getRejectedCount() { return rejectedCount; }

    private int index(int offset) {
        int remaining = items.length - head;
        return offset < remaining ? head + offset : offset - remaining;
    }
}
