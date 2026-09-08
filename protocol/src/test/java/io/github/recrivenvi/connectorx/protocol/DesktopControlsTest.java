package io.github.recrivenvi.connectorx.protocol;
import java.io.*;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;
public class DesktopControlsTest {
    @Test public void unicodePasteUsesScrcpyLayout() throws Exception {
        byte[] p=Wire.paste("中文🙂"); Wire.validate(p);
        ByteBuffer b=ByteBuffer.wrap(p); assertEquals(9,b.get());assertEquals(0,b.getLong());assertEquals(1,b.get());
        assertEquals(10,b.getInt());
    }
    @Test(expected=IllegalArgumentException.class) public void pasteBoundedBeforeAllocation() {Wire.paste("中".repeat(22000));}
    @Test(expected=IOException.class) public void lengthMismatchRejected() throws Exception {
        byte[] p=Wire.paste("x");p[13]=10;Wire.validate(p);
    }
    @Test public void scrollSignedAndBounded() throws Exception {
        byte[] p=Wire.scroll(2,3,1080,1920,-1,2);Wire.validate(p);
        assertEquals(-2047,ByteBuffer.wrap(p).getShort(13));assertEquals(4094,ByteBuffer.wrap(p).getShort(15));
    }
    @Test(expected=IOException.class) public void scrollOutsideRejected() throws Exception {Wire.validate(Wire.scroll(-1,0,1080,1920,0,1));}
    @Test(expected=IOException.class) public void largerEnvelopeDoesNotExpandTextInjection() throws Exception {
        Wire.validate(ByteBuffer.allocate(400).put((byte)1).putInt(395).array());
    }
}
