package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.awt.Canvas
import java.awt.event.KeyEvent
import java.nio.ByteBuffer
import java.nio.file.*

class DesktopParityTest {
    @Test fun overflowReleasesEveryTransmittedPointerAndKey() {
        val queue=ControlQueue(2)
        queue.offer(Wire.touch(0,1,50,50,100,200));queue.take()
        queue.offer(Wire.touch(0,2,60,50,100,200));queue.take()
        queue.offer(Wire.key(0,113));queue.take()
        queue.offer(Wire.touch(2,1,100,300,100,200));queue.offer(Wire.touch(2,2,100,300,100,200))
        assertFalse(queue.offer(Wire.key(0,29)))
        val releases=(0..2).map {queue.take()!!}
        assertEquals(setOf(1L,2L),releases.filter {it[0].toInt()==2}.map {assertEquals(1,it[1].toInt());ByteBuffer.wrap(it,2,8).long}.toSet())
        assertTrue(releases.any {it[0].toInt()==0&&it[1].toInt()==1&&ByteBuffer.wrap(it,2,4).int==113})
        queue.close();assertNull(queue.take())
    }
    @Test fun perPackageDisplayOptionsPersistIndependently() {
        val root=Path.of("D:/Workspaces/Scratches/ConnectorX/WindowsTests");Files.createDirectories(root)
        val dir=Files.createTempDirectory(root,"display-")
        val store=AppOptionsStore(dir);val a=AppOptions(800,1200,320,120f,true,false,false)
        val b=AppOptions(1280,720,160,60f,false,true,true)
        store.save("com.example.first",a);store.save("com.example.second",b)
        assertEquals(a,AppOptionsStore(dir).load("com.example.first"));assertEquals(b,AppOptionsStore(dir).load("com.example.second"))
        assertTrue(a.request("com.example.first").systemDecorations)
    }
    @Test fun g2OutlineRotatesAndExcludesOnlyPhysicalCorners() {
        for(rotation in 0..3) {
            val g=DesktopGeometry(JSONObject("""{"naturalWidth":1080,"naturalHeight":2400,"rotation":$rotation,"radii":[100,100,100,100]}"""))
            assertTrue(g.matches(g.width,g.height));val shape=g.shape(0.0,0.0,g.width.toDouble(),g.height.toDouble())
            assertFalse(shape.contains(1.0,1.0));assertTrue(shape.contains(g.width/2.0,1.0));assertTrue(shape.contains(g.width/2.0,g.height/2.0))
            assertFalse(shape.contains(-1.0,100.0))
        }
    }
    @Test fun modifierChordsDoNotBecomeDuplicateText() {
        val e=KeyEvent(Canvas(),KeyEvent.KEY_PRESSED,1,KeyEvent.CTRL_DOWN_MASK,KeyEvent.VK_A,'a')
        assertEquals(29,DesktopKeys.code(e));assertTrue(DesktopKeys.shouldSend(e));assertEquals(4096,DesktopKeys.meta(e))
        val plain=KeyEvent(Canvas(),KeyEvent.KEY_PRESSED,1,0,KeyEvent.VK_A,'a')
        assertFalse(DesktopKeys.shouldSend(plain))
    }
    @Test fun singleInstanceAuthenticatedLoopbackHandoff() {
        val root=Path.of("D:/Workspaces/Scratches/ConnectorX/WindowsTests");Files.createDirectories(root)
        val dir=Files.createTempDirectory(root,"instance-")
        val result=java.util.concurrent.CompletableFuture<String>()
        DesktopInstance(dir).use {first->
            assertTrue(first.primary);first.listen {result.complete(it)}
            DesktopInstance(dir).use {second->assertFalse(second.primary);second.forward("51b4c9c5-6d43-4859-b9fd-0adf3a387c2e")}
            assertEquals("51b4c9c5-6d43-4859-b9fd-0adf3a387c2e",result.get(3,java.util.concurrent.TimeUnit.SECONDS))
        }
        assertFalse(Files.exists(dir.resolve("instance.dpapi")))
    }
}
