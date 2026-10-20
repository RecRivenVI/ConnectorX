package io.github.recrivenvi.connectorx.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** ConnectorX v1 control frames. Network peers never forward arbitrary scrcpy commands. */
public final class Wire {
    public static final String MAGIC = "ConnectorX/1";
    public static final int PORT = 45873;
    public static final int MAX_CONTROL = 65550;
    public static final int MAX_METADATA = 524288;
    private Wire() {}
    public static byte[] touch(int action, long id, int x, int y, int width, int height) {
        return ByteBuffer.allocate(32).put((byte) 2).put((byte) action).putLong(id)
                .putInt(x).putInt(y).putShort((short) width).putShort((short) height)
                .putShort((short) (action == 1 ? 0 : 0xffff)).putInt(0).putInt(0).array();
    }
    public static byte[] key(int action, int key) {
        return key(action, key, 0, 0);
    }
    public static byte[] key(int action, int key, int repeat, int metaState) {
        return ByteBuffer.allocate(14).put((byte) 0).put((byte) action).putInt(key).putInt(repeat).putInt(metaState).array();
    }
    public static byte[] text(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 300) throw new IllegalArgumentException("Text is limited to 300 UTF-8 bytes");
        return ByteBuffer.allocate(5 + bytes.length).put((byte) 1).putInt(bytes.length).put(bytes).array();
    }
    /** Explicit committed text paste; not background clipboard synchronization. */
    public static byte[] paste(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        if (b.length > 65536) throw new IllegalArgumentException("Committed text exceeds 64 KiB");
        return ByteBuffer.allocate(14 + b.length).put((byte)9).putLong(0).put((byte)1).putInt(b.length).put(b).array();
    }
    public static byte[] scroll(int x, int y, int width, int height, float horizontal, float vertical) {
        if (!Float.isFinite(horizontal) || !Float.isFinite(vertical)) throw new IllegalArgumentException("Invalid scroll");
        return ByteBuffer.allocate(21).put((byte)3).putInt(x).putInt(y).putShort((short)width).putShort((short)height)
            .putShort((short)Math.round(Math.max(-16,Math.min(16,horizontal))*2047))
            .putShort((short)Math.round(Math.max(-16,Math.min(16,vertical))*2047)).putInt(0).array();
    }
    public static void validate(byte[] b) throws IOException {
        validate(b, false);
    }
    public static byte[] resize(int width, int height) {
        AppRequest.validateSize(width, height);
        return ByteBuffer.allocate(5).put((byte)21).putShort((short)width).putShort((short)height).array();
    }
    public static void validate(byte[] b, boolean virtualApp) throws IOException {
        if (b.length == 1 && b[0] == 17) return; // scrcpy RESET_VIDEO, preserves transport/display session
        if (b.length < 2 || b.length > MAX_CONTROL) throw new IOException("Invalid control length");
        ByteBuffer in = ByteBuffer.wrap(b);
        int type = in.get() & 255;
        if (type == 9 && b.length >= 14) {
            in.getLong(); int paste = in.get() & 255; int length = in.getInt();
            if (paste == 1 && length == b.length - 14 && length <= 65536) return;
            throw new IOException("Invalid committed text");
        }
        if (type == 3 && b.length == 21) {
            int x=in.getInt(), y=in.getInt(), w=in.getShort()&65535, h=in.getShort()&65535;
            if(w>0 && h>0 && x>=0 && y>=0 && x<w && y<h) return;
            throw new IOException("Scroll must start inside picture");
        }
        if (virtualApp && type == 21 && b.length == 5) {
            try { AppRequest.validateSize(in.getShort() & 65535, in.getShort() & 65535); return; }
            catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
        }
        if (type == 0 && b.length == 14 && (b[1] == 0 || b[1] == 1)) return;
        if (type == 1 && b.length >= 5 && b.length <= 305 && in.getInt() == b.length - 5) return;
        if (type == 2 && b.length == 32 && (b[1] == 0 || b[1] == 1 || b[1] == 2)) {
            in.position(10);
            int x = in.getInt(), y = in.getInt(), w = in.getShort() & 65535, h = in.getShort() & 65535;
            // DOWN must hit the picture. MOVE/UP preserve signed coordinates after an accepted drag leaves it.
            if (w > 0 && h > 0 && (b[1] != 0 || (x >= 0 && y >= 0 && x < w && y < h))) return;
        }
        throw new IOException("Unsupported or malformed control message");
    }
    public static byte[] readControl(DataInputStream in) throws IOException {
        return readControl(in, false);
    }
    public static byte[] readControl(DataInputStream in, boolean virtualApp) throws IOException {
        int n = in.readInt();
        if (n < 1 || n > MAX_CONTROL) throw new IOException("Control frame too large");
        byte[] b = new byte[n]; in.readFully(b); validate(b, virtualApp); return b;
    }
    public static void writeControl(DataOutputStream out, byte[] b) throws IOException {
        writeControl(out, b, false);
    }
    public static void writeControl(DataOutputStream out, byte[] b, boolean virtualApp) throws IOException {
        validate(b, virtualApp); out.writeInt(b.length); out.write(b); out.flush();
    }
    public static void writeMetadata(DataOutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_METADATA) throw new IOException("设备元数据超出原型容量");
        out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
    public static String readMetadata(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_METADATA) throw new IOException("设备元数据长度无效");
        byte[] bytes = new byte[size]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8);
    }
    public static String fingerprint(byte[] encoded) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(encoded);
            StringBuilder s = new StringBuilder();
            for (byte b : digest) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String comparison(String a, String b) {
        String joined = a.compareTo(b) < 0 ? a + b : b + a;
        return fingerprint((MAGIC + joined).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }
}
