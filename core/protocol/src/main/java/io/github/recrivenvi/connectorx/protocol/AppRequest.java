package io.github.recrivenvi.connectorx.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Platform-independent request for a remote application's real virtual display. */
public final class AppRequest {
    public final String packageName;
    public final int width, height, dpi;
    public final float refreshRate;
    public final boolean systemDecorations;
    public AppRequest(String packageName, int width, int height, int dpi, float refreshRate) {
        this(packageName,width,height,dpi,refreshRate,false);
    }
    public AppRequest(String packageName, int width, int height, int dpi, float refreshRate, boolean systemDecorations) {
        if (packageName == null || packageName.length() > 255 || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
            throw new IllegalArgumentException("无效的应用包名");
        }
        validateSize(width, height);
        if (dpi < 72 || dpi > 960 || !Float.isFinite(refreshRate) || refreshRate < 24 || refreshRate > 240) {
            throw new IllegalArgumentException("显示密度或刷新率超出原型范围");
        }
        this.packageName = packageName; this.width = width; this.height = height; this.dpi = dpi; this.refreshRate = refreshRate;
        this.systemDecorations = systemDecorations;
    }
    public static void validateSize(int width, int height) {
        if (width < 128 || height < 128 || width > 4096 || height > 4096) throw new IllegalArgumentException("原型显示尺寸范围为 128–4096 像素");
    }
    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(packageName); out.writeInt(width); out.writeInt(height); out.writeInt(dpi); out.writeFloat(refreshRate);
    }
    public static AppRequest read(DataInputStream in) throws IOException {
        return new AppRequest(in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readFloat());
    }
    public void writeOptions(DataOutputStream out) throws IOException { write(out); out.writeBoolean(systemDecorations); }
    public static AppRequest readOptions(DataInputStream in) throws IOException {
        AppRequest a=read(in);
        return new AppRequest(a.packageName,a.width,a.height,a.dpi,a.refreshRate,in.readBoolean());
    }
}
