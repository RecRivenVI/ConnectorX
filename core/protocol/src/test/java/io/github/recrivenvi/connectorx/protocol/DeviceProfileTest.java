package io.github.recrivenvi.connectorx.protocol;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceProfileTest {
    @Test public void roundTripAndroidRole() throws Exception {
        var b = new ByteArrayOutputStream(); DeviceProfile.ANDROID.write(new DataOutputStream(b));
        assertEquals(DeviceProfile.ANDROID, DeviceProfile.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray()))));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectWindowsRole() {
        new DeviceProfile("windows", false, true);
    }
    @Test(expected=IOException.class) public void rejectUnknownAuthenticatedRole() throws Exception {
        var b = new ByteArrayOutputStream(); var out = new DataOutputStream(b);
        out.writeUTF("unknown"); out.writeBoolean(true); out.writeBoolean(true);
        DeviceProfile.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray())));
    }
}
