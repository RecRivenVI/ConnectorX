package io.github.recrivenvi.connectorx.windows

import java.awt.image.BufferedImage
import io.github.recrivenvi.connectorx.protocol.MediaFraming
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

/** Disposable decoder output. Failure never owns/terminates the authenticated remote session. */
class FfmpegDecoder(private val frame:(BufferedImage)->Unit,private val failure:(String)->Unit,private val codecId:Int=0x68323634,width:Int=0,height:Int=0):AutoCloseable {
    private val closed=AtomicBoolean(false)
    private val ivf=codecId==0x00767038||codecId==0x00767039
    private val demux=when(codecId) {0x68323634->"h264";0x68323635->"hevc";0x00617631->"obu";0x00767038,0x00767039->"ivf";else->error("未知视频编码")}
    private val process=ProcessBuilder(System.getenv("CONNECTORX_FFMPEG") ?: "ffmpeg.exe",
        "-hide_banner","-loglevel","error","-probesize","32","-analyzeduration","0","-flags","low_delay",
        "-threads","1","-f",demux,"-i","pipe:0","-an","-fps_mode","passthrough","-c:v","bmp","-pix_fmt","bgr24",
        "-f","image2pipe","-flush_packets","1","pipe:1").start()
    private val stream=BufferedOutputStream(process.outputStream,64*1024)
    init {
        if(ivf) {stream.write(MediaFraming.ivfHeader(codecId,width,height));stream.flush()}
        Thread({ process.errorStream.bufferedReader().useLines { lines->lines.forEach { if(!closed.get()) failure(it.take(250)) } } },"decoder-errors").apply {isDaemon=true;start()}
        Thread({ try {
            val input=DataInputStream(BufferedInputStream(process.inputStream,1024*1024))
            while(!closed.get()) {
                val header=ByteArray(6);input.readFully(header)
                check(header[0]==66.toByte()&&header[1]==77.toByte()) {"Invalid BMP frame"}
                val size=ByteBuffer.wrap(header,2,4).order(ByteOrder.LITTLE_ENDIAN).int
                check(size in 54..128*1024*1024)
                val bytes=ByteArray(size);header.copyInto(bytes);input.readFully(bytes,6,size-6)
                val image=ImageIO.read(ByteArrayInputStream(bytes)) ?: error("Invalid decoded frame")
                if(!closed.get()) frame(image)
            }
        } catch(e:Exception) {if(!closed.get()) failure(e.message ?: "Decoder stopped")} },"decoder-output").apply {isDaemon=true;start()}
    }
    fun feed(bytes:ByteArray,pts:Long=0,config:Boolean=false) {check(!closed.get());if(ivf&&config) return;stream.write(if(ivf) MediaFraming.ivfPacket(bytes,pts) else bytes);stream.flush()}
    override fun close() {if(closed.compareAndSet(false,true)) process.destroy()}
}
