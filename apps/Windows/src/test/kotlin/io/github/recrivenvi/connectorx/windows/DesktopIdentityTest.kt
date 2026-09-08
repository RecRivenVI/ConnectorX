package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.DeviceProfile
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.*
import javax.net.ssl.*

class DesktopIdentityTest {
    private fun identity():DesktopIdentity {
        val root=Path.of("D:/Workspaces/Scratches/ConnectorX/WindowsTests")
        Files.createDirectories(root)
        return DesktopIdentity(Files.createTempDirectory(root,"identity-"))
    }
    @Test fun dpapiIdentityAndPeerSurviveReload() {
        val original=identity(); val peer=identity()
        original.save(DesktopPeer(peer.id,"Android","127.0.0.1",DeviceProfile.ANDROID))
        val reloaded=DesktopIdentity(original.directory)
        assertEquals(original.id,reloaded.id); assertTrue(reloaded.paired(peer.id))
        assertEquals(DeviceProfile.ANDROID,reloaded.peers().single().profile)
        reloaded.remove(peer.id); assertFalse(DesktopIdentity(original.directory).paired(peer.id))
    }
    @Test fun pinnedMutualTlsAndWrongIdentityRejected() {
        val a=identity(); val b=identity()
        fun handshake(expected:String):Boolean {
            val listener=a.tls { it==b.id }.serverSocketFactory.createServerSocket(0) as SSLServerSocket
            listener.needClientAuth=true; listener.enabledProtocols=arrayOf("TLSv1.3")
            val worker=Executors.newSingleThreadExecutor()
            val server=worker.submit<Boolean> { try {
                (listener.accept() as SSLSocket).use { it.soTimeout=4000; it.startHandshake(); it.outputStream.write(7); true }
            } catch(_:Exception) { false } }
            return try {
                val ok=runCatching { (b.tls { it==expected }.socketFactory.createSocket("127.0.0.1",listener.localPort) as SSLSocket).use {
                    it.soTimeout=4000; it.startHandshake(); it.inputStream.read()==7
                } }.getOrDefault(false)
                server.get(5,TimeUnit.SECONDS); ok
            } finally { listener.close(); worker.shutdownNow() }
        }
        assertTrue(handshake(a.id)); assertFalse(handshake("0".repeat(64)))
    }
}
