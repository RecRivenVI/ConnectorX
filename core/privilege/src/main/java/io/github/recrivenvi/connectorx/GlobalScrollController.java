package io.github.recrivenvi.connectorx;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import io.github.recrivenvi.connectorx.runtime.WheelTimeline;

/** Main-display wheel only. Independent of all capture and virtual-display sessions. */
final class GlobalScrollController implements AutoCloseable {
    private final Object lock=new Object();
    private final HandlerThread thread=new HandlerThread("connectorx-global-wheel");
    private final Handler handler;
    private final WheelTimeline timeline=new WheelTimeline();
    private final GlobalScrollLease lease=new GlobalScrollLease();
    private final IBinder token;
    private final IBinder.DeathRecipient death=this::close;
    private final PowerManager power;
    private final KeyguardManager keyguard;
    private final DisplayManager displays;
    private final MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords()};
    private boolean closed;
    private boolean available;
    private long lastAvailabilityCheck;
    private float rate=60f;
    private int width,height,rotation;
    private final Runnable tick=this::tick;
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) {}
        @Override public void onDisplayRemoved(int id) {if(id==Display.DEFAULT_DISPLAY)close();}
        @Override public void onDisplayChanged(int id) {
            if(id!=Display.DEFAULT_DISPLAY)return;
            synchronized(lock) {
                if(closed)return;
                try {
                    int oldWidth=width,oldHeight=height,oldRotation=rotation;
                    float oldRate=rate;
                    updateDisplay();
                    long now=System.nanoTime();
                    if(width!=oldWidth||height!=oldHeight||rotation!=oldRotation||!available(now,true))stopLocked();
                    else if(rate!=oldRate&&lease.current(now,true)!=0) {
                        handler.removeCallbacks(tick);
                        timeline.start(now,rate);schedule();
                    }
                } catch(RuntimeException failure) {stopLocked();available=false;}
            }
        }
    };

    GlobalScrollController(IBinder token) throws RemoteException {
        this.token=token;
        Context context=FakeContext.get();
        power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        displays=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        properties[0].id=0;properties[0].toolType=MotionEvent.TOOL_TYPE_MOUSE;
        updateDisplay();
        thread.start();handler=new Handler(thread.getLooper());
        try {
            token.linkToDeath(death,0);
            displays.registerDisplayListener(displayListener,handler);
        } catch(RuntimeException|RemoteException failure) {close();throw failure;}
    }
    boolean owns(IBinder value) {return token.equals(value);}
    private void updateDisplay() {
        var info=ServiceManager.getDisplayManager().getDisplayInfo(Display.DEFAULT_DISPLAY);
        if(info==null)throw new IllegalStateException("主屏不可用");
        width=info.getSize().getWidth();height=info.getSize().getHeight();rotation=info.getRotation();
        Display display=displays.getDisplay(Display.DEFAULT_DISPLAY);
        rate=WheelTimeline.clampRate(display==null?60f:display.getRefreshRate());
    }
    private boolean available(long now,boolean force) {
        if(force||now-lastAvailabilityCheck>=250_000_000L) {
            lastAvailabilityCheck=now;
            available=power!=null&&power.isInteractive()&&keyguard!=null&&!keyguard.isKeyguardLocked()&&token.isBinderAlive();
        }
        return available;
    }
    int set(int direction) {
        synchronized(lock) {
            if(closed)throw new IllegalStateException("悬浮控制会话已结束");
            long now=System.nanoTime();
            handler.removeCallbacks(tick);
            int actual=lease.set(direction,now,available(now,true));
            if(actual!=0) {updateDisplay();timeline.start(now,rate);schedule();}
            return actual;
        }
    }
    int heartbeat() {
        synchronized(lock) {
            if(closed)throw new IllegalStateException("悬浮控制会话已结束");
            long now=System.nanoTime();
            int actual=lease.renew(now,available(now,true));
            if(actual==0)handler.removeCallbacks(tick);
            return actual;
        }
    }
    private void schedule() {handler.postAtTime(tick,(timeline.nextDeadlineNanos()+999_999L)/1_000_000L);}
    private void stopLocked() {lease.set(0,System.nanoTime(),false);handler.removeCallbacks(tick);}
    private void tick() {
        synchronized(lock) {
            if(closed)return;
            long now=System.nanoTime();
            try {
                int direction=lease.current(now,available(now,false));
                if(direction==0)return;
                WheelTimeline.Tick sample=timeline.poll(now);
                if(sample!=null) {
                    coords[0].clear();coords[0].x=width/2f;coords[0].y=height/2f;
                    coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL,direction*sample.units(WheelTimeline.DEFAULT_UNITS_PER_SECOND));
                    long millis=now/1_000_000L;
                    MotionEvent event=MotionEvent.obtain(millis,millis,MotionEvent.ACTION_SCROLL,1,properties,coords,
                            0,0,1f,1f,-1,0,InputDevice.SOURCE_MOUSE,0);
                    try {
                        if(!Device.injectEvent(event,Display.DEFAULT_DISPLAY,Device.INJECT_MODE_ASYNC)) {stopLocked();return;}
                    } finally {event.recycle();}
                }
                schedule();
            } catch(RuntimeException failure) {stopLocked();}
        }
    }
    @Override public void close() {
        synchronized(lock) {
            if(closed)return;
            closed=true;stopLocked();
            try {token.unlinkToDeath(death,0);} catch(RuntimeException ignored) {}
            try {displays.unregisterDisplayListener(displayListener);} catch(RuntimeException ignored) {}
            thread.quitSafely();
        }
    }
}
