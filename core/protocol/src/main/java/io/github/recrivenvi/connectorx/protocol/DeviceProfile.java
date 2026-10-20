package io.github.recrivenvi.connectorx.protocol;

import java.io.*;

/** Authenticated device role; discovery TXT is only a hint and never grants authority. */
public record DeviceProfile(String platform, boolean canHost, boolean canReceiveCast) {
    public static final DeviceProfile ANDROID = new DeviceProfile("android", true, false);
    public DeviceProfile {
        if (!platform.equals("android") || !canHost || canReceiveCast) {
            throw new IllegalArgumentException("Only Android peers are supported");
        }
    }
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(platform); out.writeBoolean(canHost); out.writeBoolean(canReceiveCast);
    }
    public static DeviceProfile read(DataInputStream in) throws IOException {
        try { return new DeviceProfile(in.readUTF(), in.readBoolean(), in.readBoolean()); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
    }
}
