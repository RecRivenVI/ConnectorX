package io.github.recrivenvi.connectorx.runtime;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.*;
import android.view.*;
import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Workarounds;
import java.lang.reflect.*;
import java.util.*;

/** Shell-only, bounded diagnostic. Optional frames stream to stdout, never device files. No physical-display changes. */
@android.annotation.TargetApi(33)
public final class DisplaySemanticsProbe {
    static VirtualDisplay vd;
    static ImageReader reader;
    static Handler handler;
    static boolean alphaExperiment;
    static boolean directExperiment;
    static boolean touchContract;
    static android.graphics.Bitmap lastFrame;
    static long lastFrameTime;
    static final List<Runnable> cleanup = new ArrayList<>();
    static void log(String s) { System.out.println("CX_PROBE " + s); }
    static void app(int orientation, boolean fullscreen) {
        try {
            java.lang.Process p = new ProcessBuilder("am", "start", "--display", ""+vd.getDisplay().getDisplayId(),
                "-n", "io.github.recrivenvi.connectorx/."+(touchContract ? "TouchContractActivity" : "DisplaySemanticsActivity"), "--ei", "orientation", ""+orientation,
                "--ez", "fullscreen", ""+fullscreen, "--ez", "conceal", ""+(alphaExperiment && orientation == 1),
                "--ez", "direct", ""+directExperiment)
                .redirectErrorStream(true).start();
            log("app-request orientation="+orientation+" fullscreen="+fullscreen+" result="
                +new String(p.getInputStream().readAllBytes()));
        } catch(Exception e) { log("app-failed " + e); }
    }
    static void rotate(int r) {
        try { VirtualDisplay.class.getMethod("setRotation",int.class).invoke(vd,r); log("requested-rotation="+r); }
        catch(Throwable e) { log("rotation-failed " + e); }
    }
    static void capture(String label) {
        if(lastFrame == null) { log("capture-empty " + label); return; }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        lastFrame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        log("PNG " + label + " frameAgeMs="+(SystemClock.elapsedRealtime()-lastFrameTime)+" "
            + android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
    }
    static void policy(String label) {
        try {
            java.lang.Process p = new ProcessBuilder("dumpsys","window","displays").redirectErrorStream(true).start();
            String dump = new String(p.getInputStream().readAllBytes());
            int start = dump.indexOf("Display: mDisplayId="+vd.getDisplay().getDisplayId());
            int end = dump.indexOf("Display: mDisplayId=",start+1);
            if(start >= 0) for(String line:dump.substring(start,end < 0 ? dump.length() : end).split("\n")) {
                if(line.contains("Fullscreen") || line.contains("RemoteInsets") || line.contains("InsetsSource id=")) log(label+" "+line.trim());
            }
        } catch(Exception e) { log("policy-failed " + e); }
    }
    static void state() {
        android.util.DisplayMetrics m = new android.util.DisplayMetrics();
        vd.getDisplay().getRealMetrics(m);
        log("display=" + vd.getDisplay().getDisplayId() + " rotation=" + vd.getDisplay().getRotation()
            + " size=" + m.widthPixels + "x" + m.heightPixels + " dpi=" + m.densityDpi);
    }
    static void bar(Context base, int type, int insetType, int height, int gravity) throws Exception {
        Context ctx = base.createWindowContext(type, null);
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        View view = new View(ctx);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.setTitle("ConnectorXProbe-" + insetType);
        lp.setFitInsetsTypes(0);
        Class<?> pc = Class.forName("android.view.InsetsFrameProvider");
        Object provider = pc.getConstructor(Object.class, int.class, int.class).newInstance(new Binder(), 0, insetType);
        pc.getMethod("setInsetsSize", Insets.class).invoke(provider,
            gravity == Gravity.TOP ? Insets.of(0,height,0,0) : Insets.of(0,0,0,height));
        Object array = Array.newInstance(pc, 1); Array.set(array, 0, provider);
        WindowManager.LayoutParams.class.getField("providedInsets").set(lp, array);
        wm.addView(view, lp);
        cleanup.add(() -> wm.removeViewImmediate(view));
        log("provider-added type=" + insetType + " height=" + height);
    }
    public static void main(String[] args) throws Exception {
        if (Build.VERSION.SDK_INT < 33) throw new UnsupportedOperationException("This diagnostic requires Android 13+");
        if (android.os.Process.myUid() != 2000) throw new SecurityException("shell only");
        Looper.prepareMainLooper(); handler = new Handler(Looper.myLooper()); Workarounds.apply();
        log("uid=" + android.os.Process.myUid() + " sdk=" + Build.VERSION.SDK_INT);
        reader = ImageReader.newInstance(1440,3200,PixelFormat.RGBA_8888,2);
        directExperiment = args.length > 0 && args[0].equals("decorations-direct");
        touchContract = args.length > 0 && args[0].equals("touch-contract");
        alphaExperiment = directExperiment || (args.length > 0 && args[0].equals("decorations-alpha"));
        reader.setOnImageAvailableListener(r -> {
            try (android.media.Image i=r.acquireLatestImage()) {
                if(!alphaExperiment || i == null) return;
                android.media.Image.Plane p=i.getPlanes()[0];
                android.graphics.Bitmap padded=android.graphics.Bitmap.createBitmap(p.getRowStride()/p.getPixelStride(),i.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                padded.copyPixelsFromBuffer(p.getBuffer());
                android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(padded,0,0,i.getWidth(),i.getHeight());
                if(frame!=padded) padded.recycle();
                if(lastFrame!=null) lastFrame.recycle();
                lastFrame=frame; lastFrameTime=SystemClock.elapsedRealtime();
            }
        }, handler);
        int flags = 1|2|8|64|128|256|1024|2048|4096|8192|16384|32768;
        boolean decorations = touchContract || (args.length > 0 && args[0].startsWith("decorations"));
        if (decorations) flags |= 512;
        log("decorations=" + decorations);
        vd = com.genymobile.scrcpy.wrappers.ServiceManager.getDisplayManager().createNewVirtualDisplay(
            "ConnectorX-Semantics-Probe",1440,3200,560,reader.getSurface(),flags);
        state();
        try {
            Object wm = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
            Class<?> ctl = Class.forName("android.view.IDisplayWindowInsetsController");
            wm.getClass().getMethod("setDisplayWindowInsetsController", int.class, ctl).invoke(wm, vd.getDisplay().getDisplayId(), null);
            log("remote-controller-permission=allowed");
        } catch(Throwable e) { log("remote-controller-permission="+(e.getCause()==null?e:e.getCause())); }
        Context dc = FakeContext.get().createDisplayContext(vd.getDisplay());
        if (args.length == 0) {
            try { bar(dc,2000,WindowInsets.Type.statusBars(),168,Gravity.TOP); }
            catch(Throwable e) { log("status-provider-failed " + e); e.printStackTrace(); }
            try { bar(dc,2019,WindowInsets.Type.navigationBars(),56,Gravity.BOTTOM); }
            catch(Throwable e) { log("nav-provider-failed " + e); e.printStackTrace(); }
        }
        Runnable ticker = new Runnable() { public void run() { state(); handler.postDelayed(this,2000); } };
        handler.post(ticker);
        if(args.length>0 && args[0].equals("restart-contract")) {
            String fixture="io.github.recrivenvi.connectorx.restartfixture";
            handler.postDelayed(() -> log("fixture-first="+com.genymobile.scrcpy.device.Device.startApp(fixture,vd.getDisplay().getDisplayId(),false)),2000);
            handler.postDelayed(() -> log("fixture-restart="+com.genymobile.scrcpy.device.Device.startApp(fixture,vd.getDisplay().getDisplayId(),true)),8000);
        } else if(touchContract) {
            handler.postDelayed(() -> app(-1,false),2000);
        } else if(alphaExperiment) {
            handler.postDelayed(() -> app(-1,false),2000);
            handler.postDelayed(() -> capture("normal"),6000);
            handler.postDelayed(() -> app(1,false),10000);
            handler.postDelayed(() -> { capture("alpha-zero"); policy("alpha-zero"); },14000);
            handler.postDelayed(() -> capture("alpha-restored"),19000);
            handler.postDelayed(() -> app(-1,true),22000);
            handler.postDelayed(() -> { capture("fullscreen"); policy("fullscreen"); },26000);
            handler.postDelayed(() -> app(-1,false),30000);
            handler.postDelayed(() -> { capture("shown-again"); policy("shown-again"); },34000);
        } else {
        handler.postDelayed(() -> app(-1,false),2000);
        handler.postDelayed(() -> rotate(1),8000);
        handler.postDelayed(() -> app(1,false),14000);
        handler.postDelayed(() -> rotate(3),20000);
        handler.postDelayed(() -> app(0,false),26000);
        handler.postDelayed(() -> rotate(0),32000);
        handler.postDelayed(() -> app(-1,false),38000);
        handler.postDelayed(() -> rotate(3),44000);
        handler.postDelayed(() -> app(-1,true),50000);
        handler.postDelayed(() -> app(-1,false),56000);
        handler.postDelayed(() -> policy("normal"),4000);
        handler.postDelayed(() -> policy("fullscreen"),53000);
        handler.postDelayed(() -> policy("restored"),58000);
        }
        handler.postDelayed(() -> {
            for(Runnable r:cleanup) try { r.run(); } catch(Throwable e) { log("cleanup " + e); }
            vd.release(); reader.close(); log("released"); System.exit(0);
        },60000);
        Looper.loop();
    }
}
