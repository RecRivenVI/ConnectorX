package io.github.recrivenvi.connectorx;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import android.os.IBinder;
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
    String startLocalApp(int sessionId, String packageName, int width, int height, int dpi, float refreshRate, boolean decorations, in Surface surface) = 10;
    void setLocalSurface(int sessionId, in Surface surface) = 11;
    void clearLocalSurface(int sessionId) = 12;
    void injectLocal(int sessionId, in byte[] controlMessage) = 13;
    String restartLocalApp(int sessionId) = 14;
    void stopLocalApp(int sessionId) = 15;
    void injectLocalTouch(int sessionId, in byte[] frame) = 16;
    void setLocalScroll(int sessionId, int direction, float unitsPerSecond) = 17;
    void updateLocalRefreshRate(int sessionId, float rate) = 18;
    String localPerformance(int sessionId) = 19;
    // True: dismissal confirmed; false: already hidden. Failure must not fall through to Back.
    boolean hideLocalIme(int sessionId) = 20;
    // Global main-display wheel is owned by this app-process token, independent of local VD sessions.
    int setGlobalScroll(IBinder ownerToken, int direction) = 21;
    int keepGlobalScrollAlive(IBinder ownerToken) = 22;
    void closeGlobalScroll(IBinder ownerToken) = 23;
    void destroy() = 16777114;
}
