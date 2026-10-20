package io.github.recrivenvi.connectorx.protocol;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class WireTest {
    @Test public void resetVideoIsAOneByteControlFrame() throws Exception {
        byte[] reset={17}; ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Wire.writeControl(new DataOutputStream(bytes),reset);
        assertArrayEquals(reset,Wire.readControl(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        try { Wire.validate(new byte[]{17,0}); fail(); } catch(IOException expected) {}
    }
    @Test public void dragOutsidePreservesSignedCoordinatesAcrossWire() throws Exception {
        for (int action : new int[]{1,2}) {
            byte[] b=Wire.touch(action,9,-250,3500,1080,2400);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            Wire.writeControl(new DataOutputStream(bytes),b);
            assertArrayEquals(b,Wire.readControl(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        try { Wire.validate(Wire.touch(0,9,-250,3500,1080,2400)); fail(); } catch(IOException expected) {}
    }
    @Test public void touchRoundTripAndCoordinates() throws Exception {
        byte[] b = Wire.touch(0, 7, 99, 201, 1080, 2400);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Wire.writeControl(new DataOutputStream(bytes), b);
        assertArrayEquals(b, Wire.readControl(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test public void rejectUnsupportedScrcpyOperations() {
        for (int type : new int[]{3, 5, 8, 10, 12, 16, 17, 255}) {
            try { Wire.validate(new byte[]{(byte)type, 0}); fail("Accepted " + type); } catch (IOException expected) {}
        }
    }
    @Test public void rejectOversizedBeforeAllocation() throws Exception {
        byte[] header = {(byte)0x7f, (byte)0xff, (byte)0xff, (byte)0xff};
        try { Wire.readControl(new DataInputStream(new ByteArrayInputStream(header))); fail(); } catch (IOException expected) {}
    }
    @Test public void rejectOutOfBoundsAndMalformedText() {
        for (byte[] b : new byte[][]{Wire.touch(0, 0, 1080, 0, 1080, 2400), {1, 0, 0, 1, 0}}) {
            try { Wire.validate(b); fail(); } catch (IOException expected) {}
        }
    }
    @Test public void comparisonBindsBothIdentitiesAndIgnoresRole() {
        assertEquals(Wire.comparison("a", "b"), Wire.comparison("b", "a"));
        assertNotEquals(Wire.comparison("a", "b"), Wire.comparison("a", "c"));
        assertEquals(24, Wire.comparison("a", "b").length());
    }
}
