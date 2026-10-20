package io.github.recrivenvi.connectorx.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable validated payload of all accepted pointers for one same-device touch event.
 * DOWN/UP describe the changed pointer, including secondary pointer transitions; UP still
 * includes the lifted pointer. MOVE/CANCEL may use changedId = -1 for the whole snapshot.
 * Only CANCEL may have no pointers. Pointer order is preserved, but an id is not an index.
 * Indexed getters read without allocation; array getters return defensive copies on demand.
 *
 * <p>Both times are nanoseconds on the device's uptime clock (excluding deep sleep), not
 * wall time or elapsed-realtime. The caller owns origin admission and gesture ordering:
 * admit at DOWN, retain accepted pointers outside the surface, and release them on CANCEL.
 * This value validates one snapshot, not the history of a gesture. Coordinates must be
 * finite; no clipping or display bounds are applied here.
 *
 * <p>The byte format is private to local Binder transport, independent of {@link Wire}
 * and media protocol v3. All fields are big-endian: int magic/version {@code 0x4c544631}
 * ("LTF1"), int action, int changedId, int count, long eventTimeNanos, long downTimeNanos,
 * then count tuples of (int id, float x, float y). Exact size: 32 + 12 * count bytes.
 * Decoding rejects unknown versions, trailing bytes and malformed snapshots.
 */
public final class LocalTouchFrame {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int MOVE = 2;
    public static final int CANCEL = 3;
    public static final int MAX_POINTERS = 10;
    public static final int MAX_ENCODED_BYTES = 32 + 12 * MAX_POINTERS;

    private static final int MAGIC = 0x4c544631;
    private static final int HEADER_BYTES = 32;
    private static final int POINTER_BYTES = 12;

    public final int action;
    public final int changedId;
    public final long eventTimeNanos;
    public final long downTimeNanos;
    private final int[] pointerIds;
    private final float[] pointerXs;
    private final float[] pointerYs;

    /** Copies the arrays; invalid snapshots (including null arrays) throw IllegalArgumentException. */
    public LocalTouchFrame(int action, int changedId, long eventTimeNanos, long downTimeNanos,
                           int[] ids, float[] xs, float[] ys) {
        this(action, changedId, eventTimeNanos, downTimeNanos, ids, xs, ys, true);
    }

    /** Only decode may transfer arrays it exclusively owns; validation is never skipped. */
    private LocalTouchFrame(int action, int changedId, long eventTimeNanos, long downTimeNanos,
                            int[] ids, float[] xs, float[] ys, boolean copyArrays) {
        if (action < DOWN || action > CANCEL) {
            throw new IllegalArgumentException("Unknown local touch action");
        }
        if (eventTimeNanos < 0 || downTimeNanos < 0 || downTimeNanos > eventTimeNanos) {
            throw new IllegalArgumentException("Invalid uptime timestamps");
        }
        if (ids == null || xs == null || ys == null || ids.length > MAX_POINTERS
                || ids.length != xs.length || ids.length != ys.length
                || (ids.length == 0 && action != CANCEL)) {
            throw new IllegalArgumentException("Invalid pointer arrays");
        }
        pointerIds = copyArrays ? ids.clone() : ids;
        pointerXs = copyArrays ? xs.clone() : xs;
        pointerYs = copyArrays ? ys.clone() : ys;
        int seen = 0;
        boolean containsChanged = false;
        for (int i = 0; i < pointerIds.length; i++) {
            int id = pointerIds[i];
            if (id < 0 || id > 31 || (seen & (1 << id)) != 0) {
                throw new IllegalArgumentException("Pointer ids must be unique and in 0..31");
            }
            if (!Float.isFinite(pointerXs[i]) || !Float.isFinite(pointerYs[i])) {
                throw new IllegalArgumentException("Coordinates must be finite");
            }
            seen |= 1 << id;
            containsChanged |= id == changedId;
        }
        if (!containsChanged && !(changedId == -1 && (action == MOVE || action == CANCEL))) {
            throw new IllegalArgumentException("Changed pointer must be present, or -1 for MOVE/CANCEL");
        }
        this.action = action;
        this.changedId = changedId;
        this.eventTimeNanos = eventTimeNanos;
        this.downTimeNanos = downTimeNanos;
    }

    public int getAction() { return action; }
    public int getChangedId() { return changedId; }
    public long getEventTimeNanos() { return eventTimeNanos; }
    public long getDownTimeNanos() { return downTimeNanos; }
    public int getPointerCount() { return pointerIds.length; }
    public int[] getIds() { return pointerIds.clone(); }
    public float[] getXs() { return pointerXs.clone(); }
    public float[] getYs() { return pointerYs.clone(); }
    public int getId(int index) { return pointerIds[index]; }
    public float getX(int index) { return pointerXs[index]; }
    public float getY(int index) { return pointerYs[index]; }

    /** Returns a fresh local Binder payload; never send this through Wire.writeControl(). */
    public byte[] encode() {
        ByteBuffer bytes = ByteBuffer.allocate(HEADER_BYTES + POINTER_BYTES * pointerIds.length)
                .order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(MAGIC).putInt(action).putInt(changedId).putInt(pointerIds.length)
                .putLong(eventTimeNanos).putLong(downTimeNanos);
        for (int i = 0; i < pointerIds.length; i++) {
            bytes.putInt(pointerIds[i]).putFloat(pointerXs[i]).putFloat(pointerYs[i]);
        }
        return bytes.array();
    }

    /** Validates size before allocating pointer arrays; all invalid payloads throw IllegalArgumentException. */
    public static LocalTouchFrame decode(byte[] payload) {
        if (payload == null || payload.length < HEADER_BYTES || payload.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid local touch payload size");
        }
        ByteBuffer bytes = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        if (bytes.getInt() != MAGIC) {
            throw new IllegalArgumentException("Unknown local touch format");
        }
        int action = bytes.getInt();
        int changedId = bytes.getInt();
        int count = bytes.getInt();
        if (count < 0 || count > MAX_POINTERS || payload.length != HEADER_BYTES + POINTER_BYTES * count) {
            throw new IllegalArgumentException("Invalid local touch pointer count or length");
        }
        long eventTimeNanos = bytes.getLong();
        long downTimeNanos = bytes.getLong();
        int[] ids = new int[count];
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            ids[i] = bytes.getInt();
            xs[i] = bytes.getFloat();
            ys[i] = bytes.getFloat();
        }
        return new LocalTouchFrame(action, changedId, eventTimeNanos, downTimeNanos, ids, xs, ys, false);
    }
}
