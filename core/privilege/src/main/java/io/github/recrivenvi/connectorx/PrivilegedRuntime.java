package io.github.recrivenvi.connectorx;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import android.view.Surface;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.genymobile.scrcpy.Workarounds;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.model.DeviceApp;
import io.github.recrivenvi.connectorx.protocol.AppRequest;
import io.github.recrivenvi.connectorx.protocol.Wire;
import io.github.recrivenvi.connectorx.protocol.MediaOptions;
import io.github.recrivenvi.connectorx.protocol.LocalTouchFrame;
import io.github.recrivenvi.connectorx.runtime.VirtualAppHooks;
import io.github.recrivenvi.connectorx.runtime.LocalDisplayController;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Shizuku owns this Binder; never expose arbitrary shell command execution. */
public final class PrivilegedRuntime extends IRuntime.Stub {
    private final String apk;
    private final int owner;
    private volatile Process child;
    // Session identity and controller are never read as separate mutable global fields by input.
    private final AtomicReference<LocalBinding> localBinding = new AtomicReference<>();
    private static final class LocalBinding {
        final int sessionId;
        private volatile LocalDisplayController controller;
        private volatile boolean closed;
        LocalBinding(int sessionId) {this.sessionId=sessionId;}
        void attach(LocalDisplayController value) {
            synchronized(this) {
                if(!closed) {controller=value;return;}
            }
            value.close();
            throw new IllegalStateException("本机应用会话已结束");
        }
        LocalDisplayController controller() {
            LocalDisplayController value=controller;
            if(closed||value==null)throw new IllegalStateException("本机应用会话已结束或尚未就绪");
            return value;
        }
        void close() {
            LocalDisplayController value;
            synchronized(this) {if(closed)return;closed=true;value=controller;}
            if(value!=null)value.close();
        }
    }
    private volatile String displayState = "{}";
    private int currentSessionId = -1;
    private AppRequest currentApp;
    private boolean appContextReady;
    private Handler frameworkHandler;
    private final Object frameworkLock = new Object();
    private final List<LocalSocket> channels = new ArrayList<>();
    private GlobalScrollController globalScroll;
    public PrivilegedRuntime(Context context) {
        apk = context.getApplicationInfo().sourceDir;
        owner = context.getApplicationInfo().uid;
    }
    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid != owner && uid != 0 && uid != 2000) throw new SecurityException("Wrong runtime caller");
    }
    @Override public String identity() { checkCaller(); return "UID " + Os.getuid() + " · scrcpy 4.1"; }
    @Override public synchronized ParcelFileDescriptor[] start(int sessionId) {
        return launch(sessionId, null);
    }
    @Override public synchronized ParcelFileDescriptor[] startApp(int sessionId, String packageName, int width, int height, int dpi, float rate) {
        return startAppOptions(sessionId,packageName,width,height,dpi,rate,false);
    }
    @Override public synchronized ParcelFileDescriptor[] startAppOptions(int sessionId, String packageName, int width, int height, int dpi, float rate, boolean decorations) {
        checkCaller();
        AppRequest request = new AppRequest(packageName, width, height, dpi, rate, decorations);
        if (onFrameworkThread(() -> Device.findByPackageName(packageName)) == null) throw new IllegalArgumentException("被控端未安装此应用");
        return launch(sessionId, request);
    }
    @Override public synchronized String restartApp(int sessionId) {
        checkCaller();
        if(sessionId != currentSessionId || currentApp == null || child == null || !child.isAlive())
            throw new IllegalStateException("独立应用会话已结束");
        String pkg=currentApp.packageName;
        if(pkg.equals("io.github.recrivenvi.connectorx")) throw new IllegalStateException("不能强制停止承载当前连接的 ConnectorX");
        try {
            int displayId=new JSONObject(displayState).getInt("displayId");
            if(displayId<=0) throw new IllegalStateException("虚拟显示不可用");
            return onFrameworkThread(() -> {
                int result=Device.startApp(pkg,displayId,true);
                if(result<0) throw new IllegalStateException("启动返回错误 " + result);
                android.app.ActivityManager am=(android.app.ActivityManager)com.genymobile.scrcpy.FakeContext.get().getSystemService(Context.ACTIVITY_SERVICE);
                long deadline=SystemClock.elapsedRealtime()+3000;
                do {
                    for(android.app.ActivityManager.RunningTaskInfo task:am.getRunningTasks(100)) {
                        if(task.topActivity!=null && pkg.equals(task.topActivity.getPackageName())
                            && task.getClass().getField("displayId").getInt(task)==displayId) return "已强制重启到 Display " + displayId;
                    }
                    SystemClock.sleep(100);
                } while(SystemClock.elapsedRealtime()<deadline);
                throw new IllegalStateException("已发起重启，但未确认应用回到当前虚拟显示");
            });
        } catch(Exception e) { throw new IllegalStateException("强制重启失败：" + e.getMessage(),e); }
    }
    @Override public synchronized String startLocalApp(int sessionId,String packageName,int width,int height,int dpi,float rate,boolean decorations,Surface surface) {
        checkCaller();
        if(sessionId<0||surface==null||!surface.isValid()) throw new IllegalArgumentException("本机显示 Surface 不可用");
        AppRequest request=new AppRequest(packageName,width,height,dpi,rate,decorations);
        if(packageName.equals("io.github.recrivenvi.connectorx")) throw new IllegalArgumentException("不能在本机模式中启动 ConnectorX");
        if(onFrameworkThread(() -> Device.findByPackageName(packageName))==null) throw new IllegalArgumentException("本机未安装此应用");
        if(child!=null&&child.isAlive()) throw new IllegalStateException("本机已有远程控制会话");
        closeSession();currentSessionId=sessionId;currentApp=request;
        LocalBinding binding=new LocalBinding(sessionId);
        localBinding.set(binding);
        try {
            return onFrameworkThread(() -> {
                LocalDisplayController controller=new LocalDisplayController(packageName,width,height,dpi,rate,decorations,surface);
                binding.attach(controller);
                String state=controller.start(false);
                if(localBinding.get()!=binding)throw new IllegalStateException("本机应用会话已结束");
                return state;
            });
        } catch(Exception e) {closeSession();throw new IllegalStateException("本机虚拟显示启动失败："+e.getMessage(),e);}
    }
    private LocalDisplayController localSession(int sessionId) {
        LocalBinding binding=localBinding.get();
        if(binding==null||binding.sessionId!=sessionId)throw new IllegalStateException("本机应用会话已结束");
        return binding.controller();
    }
    @Override public void setLocalSurface(int sessionId,Surface surface) {
        checkCaller();LocalDisplayController controller=localSession(sessionId);
        if(surface==null||!surface.isValid()) throw new IllegalArgumentException("本机显示 Surface 不可用");
        controller.setSurface(surface);
    }
    @Override public void clearLocalSurface(int sessionId) {
        checkCaller();localSession(sessionId).clearSurface();
    }
    @Override public void injectLocal(int sessionId,byte[] message) {
        checkCaller();LocalDisplayController controller=localSession(sessionId);
        try {Wire.validate(message,true);} catch(IOException e) {throw new IllegalArgumentException("本机输入消息无效",e);}
        controller.inject(message);
    }
    @Override public void injectLocalTouch(int sessionId,byte[] payload) {
        checkCaller();LocalDisplayController controller=localSession(sessionId);
        LocalTouchFrame frame=LocalTouchFrame.decode(payload);
        controller.injectTouch(frame.action,frame.changedId,frame.eventTimeNanos,frame.downTimeNanos,frame.getIds(),frame.getXs(),frame.getYs());
    }
    @Override public void setLocalScroll(int sessionId,int direction,float unitsPerSecond) {
        checkCaller();localSession(sessionId).setScroll(direction,unitsPerSecond);
    }
    @Override public void updateLocalRefreshRate(int sessionId,float rate) {
        checkCaller();localSession(sessionId).updateRefreshRate(rate);
    }
    @Override public synchronized int setGlobalScroll(android.os.IBinder token,int direction) {
        checkCaller();
        if(token==null||!token.isBinderAlive()||direction < -1||direction > 1)throw new IllegalArgumentException("悬浮滚动请求无效");
        if(globalScroll==null||!globalScroll.owns(token)) {
            if(globalScroll!=null)globalScroll.close();
            globalScroll=onFrameworkThread(() -> new GlobalScrollController(token));
        }
        return globalScroll.set(direction);
    }
    @Override public synchronized int keepGlobalScrollAlive(android.os.IBinder token) {
        checkCaller();
        if(globalScroll==null||!globalScroll.owns(token))throw new IllegalStateException("悬浮滚动未启动");
        return globalScroll.heartbeat();
    }
    @Override public synchronized void closeGlobalScroll(android.os.IBinder token) {
        checkCaller();
        if(globalScroll!=null&&globalScroll.owns(token)) {globalScroll.close();globalScroll=null;}
    }
    @Override public String localPerformance(int sessionId) {
        checkCaller();return localSession(sessionId).performance();
    }
    @Override public boolean hideLocalIme(int sessionId) {
        checkCaller();return localSession(sessionId).hideIme();
    }
    @Override public String restartLocalApp(int sessionId) {
        checkCaller();LocalDisplayController controller=localSession(sessionId);
        controller.prepareRestart();
        try {return onFrameworkThread(() -> controller.start(true));}
        finally {controller.abandonRestart();}
    }
    @Override public void stopLocalApp(int sessionId) {
        checkCaller();LocalBinding binding=localBinding.get();
        if(binding!=null&&binding.sessionId==sessionId&&localBinding.compareAndSet(binding,null))binding.close();
    }
    private <T> T onFrameworkThread(Callable<T> action) {
        Handler handler;
        synchronized(frameworkLock) {
            if (frameworkHandler == null) {
                HandlerThread thread = new HandlerThread("connectorx-framework");
                thread.start(); frameworkHandler = new Handler(thread.getLooper());
            }
            handler=frameworkHandler;
        }
        FutureTask<T> task = new FutureTask<>(() -> {
            // ActivityThread/Handler construction in scrcpy requires a prepared Looper.
            if (!appContextReady) { Workarounds.apply(); appContextReady = true; }
            return action.call();
        });
        handler.post(task);
        try { return task.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { task.cancel(true); throw new IllegalStateException("Android 应用查询失败：" + e.getMessage(), e); }
    }
    @Override public synchronized String listApps() {
        checkCaller();
        try {
            List<DeviceApp> apps = onFrameworkThread(Device::listApps);
            apps.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            JSONArray result = new JSONArray();
            for (DeviceApp app : apps) {
                String name = app.getName();
                result.put(new JSONObject().put("package", app.getPackageName())
                        .put("name", name.substring(0, Math.min(80, name.length()))).put("system", app.isSystem()));
            }
            String json = result.toString();
            if (json.getBytes(StandardCharsets.UTF_8).length > Wire.MAX_METADATA) throw new IllegalStateException("应用列表超出原型容量");
            return json;
        } catch (Exception e) { throw new IllegalStateException("读取远程应用列表失败：" + e.getMessage()); }
    }
    @Override public String sessionInfo() { checkCaller(); return displayState; }
    @Override public synchronized String screenGeometry() { checkCaller(); return onFrameworkThread(() -> ScreenGeometryCollector.collect(false)); }
    @Override public synchronized ParcelFileDescriptor[] startConfigured(int sessionId,String packageName,int width,int height,int dpi,float rate,boolean decorations,String mediaOptions) {
        checkCaller();
        AppRequest request=packageName.isEmpty()?null:new AppRequest(packageName,width,height,dpi,rate,decorations);
        if(request!=null && onFrameworkThread(() -> Device.findByPackageName(packageName))==null) throw new IllegalArgumentException("被控端未安装此应用");
        return launch(sessionId,request,MediaOptions.decode(mediaOptions));
    }
    private ParcelFileDescriptor[] launch(int sessionId, AppRequest request) {return launch(sessionId,request,MediaOptions.legacy(request!=null));}
    private ParcelFileDescriptor[] launch(int sessionId, AppRequest request, MediaOptions media) {
        checkCaller();
        if (sessionId < 0) throw new IllegalArgumentException("Invalid session");
        if (Build.VERSION.SDK_INT < 30) throw new IllegalStateException("当前原型被控端需要 Android 11 或更高版本");
        if(localBinding.get()!=null) throw new IllegalStateException("本机应用控制正在使用虚拟显示");
        closeSession();
        currentSessionId=sessionId; currentApp=request;
        displayState = "{\"state\":\"starting\"}";
        List<ParcelFileDescriptor> exported = new ArrayList<>();
        // Both listener and scrcpy live in shell. Only connected descriptors cross Binder.
        try (LocalServerSocket listener = new LocalServerSocket(String.format(Locale.ROOT, "scrcpy_%08x", sessionId))) {
            int flags = Os.fcntlInt(listener.getFileDescriptor(), OsConstants.F_GETFL, 0);
            Os.fcntlInt(listener.getFileDescriptor(), OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
            List<String> command = new ArrayList<>(java.util.Arrays.asList("/system/bin/app_process", "/", "com.genymobile.scrcpy.Server", "4.1",
                    "scid=" + Integer.toHexString(sessionId), "control=true", "tunnel_forward=false",
                    "send_device_meta=false", "send_dummy_byte=false", "cleanup=false", "clipboard_autosync=false",
                    "power_on=false"));
            if (request != null) {
                command.addAll(java.util.Arrays.asList("new_display=" + request.width + "x" + request.height + "/" + request.dpi,
                        "flex_display=true", "vd_system_decorations="+request.systemDecorations, "vd_destroy_content=true", "display_ime_policy=local",
                        "max_size="+media.maxSizeFor(true)));
            } else {
                command.add("max_size="+media.maxSizeFor(false));
            }
            command.addAll(media.serverArguments());
            ProcessBuilder b = new ProcessBuilder(command);
            b.environment().put("CLASSPATH", apk);
            b.environment().put("CONNECTORX_AUDIO_OPTIONAL",media.audio?"1":"0");
            b.environment().put("CONNECTORX_BRIDGE_UID", Integer.toString(Os.getuid()));
            b.environment().put("CONNECTORX_BRIDGE_PID", Integer.toString(Os.getpid()));
            if (request != null) {
                b.environment().put("CONNECTORX_APP_PACKAGE", request.packageName);
                b.environment().put("CONNECTORX_REFRESH_RATE", Float.toString(request.refreshRate));
            }
            b.redirectErrorStream(true);
            child = b.start();
            Process launched = child;
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(launched.getInputStream()))) {
                    String line; while ((line = r.readLine()) != null) {
                        Log.i("ConnectorXRuntime", line);
                        int prefix = line.indexOf(VirtualAppHooks.STATUS_PREFIX);
                        if (child == launched && prefix >= 0) {
                            try { displayState = new JSONObject(line.substring(prefix + VirtualAppHooks.STATUS_PREFIX.length())).toString(); }
                            catch (Exception malformed) { Log.w("ConnectorXRuntime", "Invalid display status", malformed); }
                        }
                    }
                } catch (Exception e) { Log.d("ConnectorXRuntime", "Runtime stream closed"); }
            }, "runtime-output").start();
            long deadline = SystemClock.elapsedRealtime() + 10_000;
            for (int i = 0; i < (media.audio?3:2); ++i) {
                LocalSocket channel = acceptBounded(listener, launched, deadline);
                channels.add(channel);
                if (channel.getPeerCredentials().getUid() != 2000 && channel.getPeerCredentials().getUid() != 0) {
                    throw new IOException("Unexpected scrcpy socket UID");
                }
                exported.add(ParcelFileDescriptor.dup(channel.getFileDescriptor()));
            }
            if (request != null) {
                long displayDeadline = SystemClock.elapsedRealtime() + 8_000;
                while (true) {
                    JSONObject info = new JSONObject(displayState);
                    if ("ready".equals(info.optString("state"))) break;
                    if ("failed".equals(info.optString("state"))) throw new IOException(info.optString("error"));
                    if (!launched.isAlive()) throw new IOException("scrcpy 创建虚拟显示时退出");
                    if (SystemClock.elapsedRealtime() >= displayDeadline) throw new IOException("等待虚拟显示和应用启动超时");
                    SystemClock.sleep(50);
                }
            }
            Log.i("ConnectorXRuntime", "Local bridge ready; exporting video/control descriptors over Binder");
            return exported.toArray(new ParcelFileDescriptor[0]);
        } catch (Exception e) {
            for (ParcelFileDescriptor descriptor : exported) { try { descriptor.close(); } catch (IOException ignored) {} }
            closeSession();
            Log.e("ConnectorXRuntime", "Runtime start failed", e);
            throw new IllegalStateException("scrcpy 启动失败：" + e.getMessage());
        }
    }
    private LocalSocket acceptBounded(LocalServerSocket listener, Process launched, long deadline) throws Exception {
        StructPollfd poll = new StructPollfd();
        poll.fd = listener.getFileDescriptor();
        poll.events = (short) OsConstants.POLLIN;
        return RuntimeStartup.await(launched::isAlive, SystemClock::elapsedRealtime, deadline, timeout -> {
            poll.revents = 0;
            if (Os.poll(new StructPollfd[]{poll}, timeout) == 0) return null;
            if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) {
                throw new IOException("本地监听 socket 已失效");
            }
            if ((poll.revents & OsConstants.POLLIN) != 0) return listener.accept();
            return null;
        });
    }
    @Override public synchronized void stop() {
        checkCaller();
        closeSession();
    }
    private void closeSession() {
        currentSessionId=-1; currentApp=null;
        LocalBinding binding=localBinding.getAndSet(null);
        if(binding!=null) {try {binding.close();} catch(Exception ignored) {}}
        for (LocalSocket channel : channels) {
            try { channel.shutdownInput(); } catch (IOException ignored) {}
            try { channel.shutdownOutput(); } catch (IOException ignored) {}
            try { channel.close(); } catch (IOException ignored) {}
        }
        channels.clear();
        if (child != null) { child.destroy(); child = null; }
    }
    @Override public synchronized void destroy() { stop();if(globalScroll!=null)globalScroll.close();System.exit(0); }
}
