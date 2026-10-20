package io.github.recrivenvi.connectorx.protocol;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class AppRequestTest {
    @org.junit.Test public void extendedOptionsRoundTripAndLegacyDefault() throws Exception {
        for(boolean decorations:new boolean[]{false,true}) {
            AppRequest request=new AppRequest("com.example.demo",1080,2200,420,120,decorations);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            request.writeOptions(new java.io.DataOutputStream(bytes));
            AppRequest decoded=AppRequest.readOptions(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            org.junit.Assert.assertEquals(decorations,decoded.systemDecorations);
            org.junit.Assert.assertEquals(request.packageName,decoded.packageName);
            org.junit.Assert.assertEquals(2200,decoded.height);
        }
        java.io.ByteArrayOutputStream legacy=new java.io.ByteArrayOutputStream();
        new AppRequest("com.example.demo",1080,2200,420,60).write(new java.io.DataOutputStream(legacy));
        org.junit.Assert.assertFalse(AppRequest.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(legacy.toByteArray()))).systemDecorations);
    }
    @Test public void keyboardPreservesModifiersAndRepeat() throws Exception {
        byte[] packet = Wire.key(0, 29, 2, 0x1000);
        Wire.validate(packet, true);
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(packet);
        bytes.position(6); assertEquals(2, bytes.getInt()); assertEquals(0x1000, bytes.getInt());
    }
    @Test public void requestRoundTrip() throws Exception {
        AppRequest request = new AppRequest("com.android.settings", 1200, 2608, 480, 120);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); request.write(new DataOutputStream(bytes));
        AppRequest result = AppRequest.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(request.packageName, result.packageName); assertEquals(2608, result.height); assertEquals(120, result.refreshRate, 0);
    }
    @Test public void rejectCommandsAndScrcpyNamePrefixes() {
        for (String name : new String[]{"+com.example.app", "?Settings", "com.app;reboot", "com.app\n", "", "a..b"}) {
            try { new AppRequest(name, 1000, 2000, 480, 60); fail(name); } catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void rejectInvalidDisplay() {
        for (float rate : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0, 241}) {
            try { new AppRequest("com.example.app", 1000, 2000, 480, rate); fail(); } catch (IllegalArgumentException expected) {}
        }
        try { new AppRequest("com.example.app", 0, 8192, 480, 60); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void resizeIsRestrictedToVirtualSessions() throws Exception {
        byte[] resize = Wire.resize(2608, 1200);
        Wire.validate(resize, true);
        try { Wire.validate(resize); fail(); } catch (IOException expected) {}
        try { Wire.validate(new byte[]{21, 0, 0, 0, 0}, true); fail(); } catch (IOException expected) {}
    }
    @Test public void metadataHandlesUtf8AndRejectsOversize() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Wire.writeMetadata(new DataOutputStream(bytes), "设置 📱");
        assertEquals("设置 📱", Wire.readMetadata(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        try { Wire.readMetadata(new DataInputStream(new ByteArrayInputStream(new byte[]{127, -1, -1, -1}))); fail(); } catch (IOException expected) {}
    }
}
