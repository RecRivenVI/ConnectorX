package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.protocol.*
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*

class WindowsAudioPlayer(private val input:DataInputStream,private val options:MediaOptions,private val report:(String)->Unit):AutoCloseable {
    private val closed=AtomicBoolean(false)
    @Volatile private var process:Process?=null
    @Volatile private var line:SourceDataLine?=null
    fun play() {
        Thread({try {
            val id=input.readInt()
            if(id==0||id==1) {report(if(id==0) "被控端未能捕获音频，视频保持" else "音频配置失败，视频保持");return@Thread}
            val format=when(id) {0x6f707573->"ogg";0x00616163->"aac";0x666c6163->"flac";0x00726177->"s16le";else->error("未知音频编码")}
            val command=mutableListOf(System.getenv("CONNECTORX_FFMPEG") ?: "ffmpeg.exe","-hide_banner","-loglevel","error","-probesize","32","-analyzeduration","0","-f",format)
            if(format=="s16le") command+=listOf("-ar","48000","-ac","2")
            command+=listOf("-i","pipe:0","-vn","-ar","48000","-ac","2","-f","s16le","pipe:1")
            val p=ProcessBuilder(command).start();process=p
            if(closed.get()) {p.destroy();return@Thread}
            Thread({p.errorStream.bufferedReader().useLines {it.forEach {message->if(!closed.get()) report("音频解码：${message.take(200)}")}}},"audio-errors").apply {isDaemon=true;start()}
            Thread({try {
                val audioFormat=AudioFormat(48000f,16,2,true,false)
                val output=AudioSystem.getSourceDataLine(audioFormat);line=output
                output.open(audioFormat,48000*4*options.audioBufferMs/1000);output.start()
                if(closed.get()) {output.close();return@Thread}
                val buffer=ByteArray(4096);var announced=false
                while(!closed.get()) {
                    val n=p.inputStream.readNBytes(buffer,0,buffer.size);if(n<=0) break
                    if(n>0) {output.write(buffer,0,n-n%4);if(!announced) {report("音频播放中 · ${options.audioCodec}");announced=true}}
                }
            } catch(e:Exception) {if(!closed.get()) report("音频输出失败：${e.message}")}
            finally {close()}},"audio-output").apply {isDaemon=true;start()}
            var config=byteArrayOf();val opus=MediaFraming.OggOpus();val out=BufferedOutputStream(p.outputStream,16384)
            while(!closed.get()) {
                val header=input.readLong();val length=input.readInt();check(length in 1..1024*1024)
                val bytes=ByteArray(length);input.readFully(bytes)
                val configuration=header and (1L shl 62)!=0L
                val framed=if(configuration) {
                    config=bytes
                    when(id) {0x6f707573->opus.header(bytes);0x666c6163->MediaFraming.flacHeader(bytes);else->byteArrayOf()}
                } else when(id) {0x6f707573->opus.packet(bytes);0x00616163->MediaFraming.adts(bytes,config);else->bytes}
                out.write(framed);out.flush()
            }
        } catch(e:Exception) {if(!closed.get()) report("音频已停止：${e.message}；视频保持")}
        finally {close()}},"windows-audio").apply {isDaemon=true;start()}
    }
    override fun close() {
        if(!closed.compareAndSet(false,true)) return
        runCatching {input.close()};process?.destroy();runCatching {line?.stop()};runCatching {line?.flush()};runCatching {line?.close()};line=null
    }
}
