package io.github.recrivenvi.connectorx.protocol;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceProfileTest {
    @Test public void roundTripRoles() throws Exception {
        for (DeviceProfile p : new DeviceProfile[]{DeviceProfile.ANDROID, DeviceProfile.WINDOWS}) {
            var b = new ByteArrayOutputStream(); p.write(new DataOutputStream(b));
            assertEquals(p, DeviceProfile.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray()))));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void windowsCannotAdvertiseHost() {
        new DeviceProfile("windows", true, true);
    }
    @Test(expected=IOException.class) public void rejectUnknownAuthenticatedRole() throws Exception {
        var b = new ByteArrayOutputStream(); var out = new DataOutputStream(b);
        out.writeUTF("unknown"); out.writeBoolean(true); out.writeBoolean(true);
        DeviceProfile.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray())));
    }
}
