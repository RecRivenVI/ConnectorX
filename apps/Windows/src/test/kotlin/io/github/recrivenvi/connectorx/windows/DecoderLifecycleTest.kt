package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.DeviceProfile
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import javax.net.ssl.*
import javax.swing.SwingUtilities

class DecoderLifecycleTest {
    @Test fun decoderResetKeepsSessionAndSignedOutsideDrag() {
        val proc=ProcessBuilder(System.getenv("CONNECTORX_FFMPEG") ?: "ffmpeg.exe","-hide_banner","-loglevel","error",
            "-f","lavfi","-i","testsrc2=size=128x128:rate=30","-frames:v","30","-c:v","libx264",
            "-preset","ultrafast","-tune","zerolatency","-f","h264","pipe:1").start()
        val video=proc.inputStream.readAllBytes();assertEquals(0,proc.waitFor())
        val source=PipedInputStream(1024*1024);val writer=DataOutputStream(PipedOutputStream(source));val controls=ByteArrayOutputStream()
        val socket=SSLSocketFactory.getDefault().createSocket() as SSLSocket
        val session=DesktopSession(DesktopPeer("a".repeat(64),"Fixture","127.0.0.1",DeviceProfile.ANDROID),null,socket,
            DataInputStream(source),DataOutputStream(controls),{},"{}")
        fun pump() {writer.writeLong(Long.MIN_VALUE or 128L);writer.writeInt(128);writer.writeLong(0);writer.writeInt(video.size);writer.write(video);writer.flush()}
        fun awaitFrames(after:Long) {
            val deadline=System.nanoTime()+10_000_000_000L
            while(System.nanoTime()<deadline) {SwingUtilities.invokeAndWait {};if(session.frames>after) return;Thread.sleep(50)}
            fail("No decoded frames: ${session.videoError}")
        }
        try {
            SwingUtilities.invokeAndWait {session.open()};writer.writeInt(0x68323634);pump();awaitFrames(0)
            val before=session.frames
            SwingUtilities.invokeAndWait {
                session.panel.setSize(256,256)
                session.panel.touch(0,0,-1,10) // Rejected origin.
                session.panel.touch(2,0,100,100)
                session.panel.touch(0,1,64,64);session.panel.touch(2,1,300,300);session.panel.touch(1,1,300,300)
                session.retryVideo()
            }
            pump();awaitFrames(before);assertTrue(session.running);assertFalse(socket.isClosed)
            val packets=mutableListOf<ByteArray>();val input=DataInputStream(ByteArrayInputStream(controls.toByteArray()))
            while(input.available()>0) {val n=input.readInt();packets+=ByteArray(n).also {input.readFully(it)}}
            val touches=packets.filter {it[0].toInt()==2}
            assertEquals(3,touches.size);assertEquals(150,java.nio.ByteBuffer.wrap(touches[1],10,4).int)
            assertTrue(packets.any {it.size==1&&it[0].toInt()==17})
        } finally {session.close();writer.close();source.close()}
    }
}
