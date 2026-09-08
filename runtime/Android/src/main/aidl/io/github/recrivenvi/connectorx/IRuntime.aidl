package io.github.recrivenvi.connectorx;
import android.os.ParcelFileDescriptor;
interface IRuntime {
    String identity() = 0;
    ParcelFileDescriptor[] start(int sessionId) = 1;
    void stop() = 2;
    String listApps() = 3;
    String sessionInfo() = 4;
    ParcelFileDescriptor[] startApp(int sessionId, String packageName, int width, int height, int dpi, float refreshRate) = 5;
    String screenGeometry() = 6;
    ParcelFileDescriptor[] startAppOptions(int sessionId, String packageName, int width, int height, int dpi, float refreshRate, boolean decorations) = 7;
    String restartApp(int sessionId) = 8;
    ParcelFileDescriptor[] startConfigured(int sessionId, String packageName, int width, int height, int dpi, float refreshRate, boolean decorations, String mediaOptions) = 9;
    void destroy() = 16777114;
}
