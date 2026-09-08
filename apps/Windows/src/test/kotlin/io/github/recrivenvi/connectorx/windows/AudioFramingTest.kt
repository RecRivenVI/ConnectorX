package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.MediaFraming
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

/** Synthetic silence only; decoder output goes to memory, never to speakers or a microphone. */
class AudioFramingTest {
    private fun ffmpeg(args:List<String>,input:ByteArray?=null):ByteArray {
        val p=ProcessBuilder(listOf(System.getenv("CONNECTORX_FFMPEG") ?: "ffmpeg.exe","-hide_banner","-loglevel","error")+args).start()
        val writer=Thread {p.outputStream.use {if(input!=null) it.write(input)}}.apply {start()}
        val bytes=p.inputStream.readAllBytes();val error=p.errorStream.readAllBytes().toString(Charsets.UTF_8)
        assertEquals(error,0,p.waitFor());writer.join();return bytes
    }
    private fun generate(codec:String,format:String)=ffmpeg(listOf("-f","lavfi","-i","anullsrc=r=48000:cl=stereo","-t","0.25","-c:a",codec,"-f",format,"pipe:1"))
    private fun decode(format:String,bytes:ByteArray):ByteArray=ffmpeg(listOf("-f",format,"-i","pipe:0","-f","s16le","-ac","2","-ar","48000","pipe:1"),bytes)
    @Test fun opusScrcpyPacketsBecomePlayableOgg() {
        val ogg=generate("libopus","ogg");val packets=mutableListOf<ByteArray>();val packet=ByteArrayOutputStream();var at=0
        while(at<ogg.size) {
            assertEquals("OggS",String(ogg,at,4));val segments=ogg[at+26].toInt() and 255;var data=at+27+segments
            repeat(segments) {i->val n=ogg[at+27+i].toInt() and 255;packet.write(ogg,data,n);data+=n;if(n<255) {packets+=packet.toByteArray();packet.reset()}}
            at=data
        }
        val wrapper=MediaFraming.OggOpus();val rebuilt=ByteArrayOutputStream()
        rebuilt.write(wrapper.header(packets[0]));packets.drop(2).forEach {rebuilt.write(wrapper.packet(it))}
        assertTrue(decode("ogg",rebuilt.toByteArray()).size>30000)
    }
    @Test fun aacScrcpyPacketsBecomePlayableAdts() {
        val adts=generate("aac","adts");val rebuilt=ByteArrayOutputStream();var at=0
        while(at<adts.size) {
            val size=((adts[at+3].toInt() and 3) shl 11) or ((adts[at+4].toInt() and 255) shl 3) or ((adts[at+5].toInt() and 255) shr 5)
            rebuilt.write(MediaFraming.adts(adts.copyOfRange(at+7,at+size),byteArrayOf(0x11,0x90.toByte())));at+=size
        }
        assertTrue(decode("aac",rebuilt.toByteArray()).size>30000)
    }
    @Test fun flacStreamInfoReconstructionDecodes() {
        val flac=generate("flac","flac");val info=flac.copyOfRange(8,42);var at=4
        while(true) {val last=flac[at].toInt() and 128!=0;val length=((flac[at+1].toInt() and 255) shl 16) or ((flac[at+2].toInt() and 255) shl 8) or (flac[at+3].toInt() and 255);at+=4+length;if(last) break}
        assertTrue(decode("flac",MediaFraming.flacHeader(info)+flac.copyOfRange(at,flac.size)).size>30000)
    }
    @Test fun rawPcmUsesScrcpySampleLayout() {
        val pcm=generate("pcm_s16le","s16le")
        assertEquals(48000*4/4,pcm.size)
        assertTrue(pcm.all {it==0.toByte()})
    }
}
