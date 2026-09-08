package io.github.recrivenvi.connectorx.windows

import org.junit.Test
import org.junit.Assert.*
import java.nio.*
import java.util.concurrent.*

class VideoCodecsTest {
    @Test fun supportedVideoFormatsDecodeSyntheticFrames() {
        val variants=listOf(Triple(0x68323634,"libx264","h264"),Triple(0x68323635,"libx265","hevc"),
            Triple(0x00617631,"libaom-av1","obu"),Triple(0x00767038,"libvpx","ivf"),Triple(0x00767039,"libvpx-vp9","ivf"))
        for((id,encoder,format) in variants) {
            val args=mutableListOf(System.getenv("CONNECTORX_FFMPEG") ?: "ffmpeg.exe","-hide_banner","-loglevel","error","-f","lavfi",
                "-i","testsrc2=size=128x128:rate=30","-frames:v","16","-c:v",encoder,"-threads","1")
            when(encoder) {
                "libx264"->args+=listOf("-preset","ultrafast","-tune","zerolatency")
                "libx265"->args+=listOf("-preset","ultrafast","-x265-params","log-level=error:bframes=0")
                "libaom-av1"->args+=listOf("-cpu-used","8","-lag-in-frames","0")
                else->args+=listOf("-deadline","realtime","-cpu-used","8","-lag-in-frames","0")
            }
            args+=listOf("-f",format,"pipe:1")
            val p=ProcessBuilder(args).start();val bytes=p.inputStream.readAllBytes();val errors=p.errorStream.readAllBytes().toString(Charsets.UTF_8)
            assertEquals("$encoder: $errors",0,p.waitFor())
            val frame=CompletableFuture<Int>();val failures=CopyOnWriteArrayList<String>()
            FfmpegDecoder({frame.complete(it.width)},{failures.add(it)},id,128,128).use {decoder->
                if(format=="ivf") {
                    var at=32;var index=0L
                    while(at<bytes.size) {val length=ByteBuffer.wrap(bytes,at,4).order(ByteOrder.LITTLE_ENDIAN).int
                        decoder.feed(bytes.copyOfRange(at+12,at+12+length),index++*33333);at+=12+length}
                } else decoder.feed(bytes)
                assertEquals("$encoder: $failures",128,frame.get(10,TimeUnit.SECONDS).toInt())
            }
        }
    }
}
