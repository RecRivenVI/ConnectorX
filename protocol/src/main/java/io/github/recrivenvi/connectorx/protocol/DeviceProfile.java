package io.github.recrivenvi.connectorx.protocol;

import java.io.*;

/** Authenticated device role; discovery TXT is only a hint and never grants authority. */
public record DeviceProfile(String platform, boolean canHost, boolean canReceiveCast) {
    public static final DeviceProfile ANDROID = new DeviceProfile("android", true, false);
    public static final DeviceProfile WINDOWS = new DeviceProfile("windows", false, true);
    public DeviceProfile {
        if (!platform.equals("android") && !platform.equals("windows")) throw new IllegalArgumentException("Unsupported platform");
        if (platform.equals("windows") && canHost) throw new IllegalArgumentException("Windows is controller-only");
    }
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(platform); out.writeBoolean(canHost); out.writeBoolean(canReceiveCast);
    }
    public static DeviceProfile read(DataInputStream in) throws IOException {
        try { return new DeviceProfile(in.readUTF(), in.readBoolean(), in.readBoolean()); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
    }
}
