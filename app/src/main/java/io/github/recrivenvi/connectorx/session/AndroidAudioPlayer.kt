package io.github.recrivenvi.connectorx.session

import android.content.Context
import android.media.*
import io.github.recrivenvi.connectorx.protocol.MediaOptions
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

data class AudioMetrics(val kbps:Float,val packetsPerSecond:Float,val underruns:Int,val format:String)

/** Audio has its own transport/lifecycle. Failure is reported without closing the video session. */
class AndroidAudioPlayer(
    private val context:Context,
    private val input:DataInputStream,
    private val options:MediaOptions,
    private val report:(String)->Unit,
    private val metrics:(AudioMetrics)->Unit,
):AutoCloseable {
    private val closed=AtomicBoolean(false)
    @Volatile private var decoder:MediaCodec?=null
    @Volatile private var track:AudioTrack?=null
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({change->runCatching {track?.setVolume(if(change==AudioManager.AUDIOFOCUS_GAIN) 1f else 0f)}},android.os.Handler(android.os.Looper.getMainLooper())).build()
    private var playbackStarted=false
    private var initialBytes=0
    private var targetBytes=0
    private var activeFormat="等待音频格式"

    private fun output(rate:Int=48000,channels:Int=2):AudioTrack {
        val channel=if(channels==1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minimum=AudioTrack.getMinBufferSize(rate,channel,AudioFormat.ENCODING_PCM_16BIT)
        check(minimum>0)
        targetBytes=rate*channels*2*options.audioBufferMs/1000
        val outputBytes=rate*channels*2*options.audioOutputBufferMs/1000
        playbackStarted=false;initialBytes=0;activeFormat="${rate} Hz · ${channels}ch · 16-bit PCM"
        return AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(channel).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum,targetBytes+outputBytes)).setTransferMode(AudioTrack.MODE_STREAM).build()
    }

    private fun ensurePlayback(written:Int) {
        if(playbackStarted) return
        initialBytes+=written
        if(initialBytes>=targetBytes) {track!!.play();playbackStarted=true}
    }

    private fun writePcm(bytes:ByteArray,offset:Int,size:Int) {
        var position=offset
        while(position<offset+size&&!closed.get()) {
            val mode=if(playbackStarted) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
            val n=track!!.write(bytes,position,offset+size-position,mode)
            check(n>=0) {"音频输出失败 $n"}
            if(n==0) {if(!playbackStarted) {track!!.play();playbackStarted=true};Thread.yield()} else {position+=n;ensurePlayback(n)}
        }
    }

    private fun writePcm(buffer:ByteBuffer) {
        while(buffer.hasRemaining()&&!closed.get()) {
            val mode=if(playbackStarted) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
            val n=track!!.write(buffer,buffer.remaining(),mode)
            check(n>=0) {"音频输出失败 $n"}
            if(n==0) {if(!playbackStarted) {track!!.play();playbackStarted=true};Thread.yield()} else ensurePlayback(n)
        }
    }

    fun play() {
        Thread({try {
            val id=input.readInt()
            if(id==0||id==1) {report(if(id==0) "被控端无法捕获音频，视频保持" else "音频编码配置失败，视频保持");return@Thread}
            val raw=id==0x00726177
            val mime=when(id) {0x6f707573->"audio/opus";0x00616163->"audio/mp4a-latm";0x666c6163->"audio/flac";0x00726177->"audio/raw";else->error("未知音频编码")}
            check(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {"音频焦点不可用"}
            if(raw) track=output()
            var playing=false
            var sampleStart=android.os.SystemClock.elapsedRealtime()
            var packets=0
            var bytesReceived=0L
            var packet=ByteArray(64*1024)
            while(!closed.get()) {
                val header=input.readLong();val size=input.readInt();check(size in 1..1024*1024)
                if(packet.size<size)packet=ByteArray(Integer.highestOneBit(size-1).shl(1))
                input.readFully(packet,0,size);packets++;bytesReceived+=size
                if(raw) {
                    writePcm(packet,0,size)
                    if(!playing) {report("音频播放中 · PCM 48 kHz");playing=true}
                } else if(header and (1L shl 62)!=0L) {
                    runCatching {decoder?.stop()};decoder?.release()
                    val format=MediaFormat.createAudioFormat(mime,48000,2).apply {
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,1024*1024)
                        val payload=packet.copyOf(size)
                        val csd=if(id==0x666c6163) byteArrayOf(102,76,97,67,0x80.toByte(),0,0,34)+payload else payload
                        setByteBuffer("csd-0",ByteBuffer.wrap(csd))
                        if(id==0x6f707573) {
                            check(payload.size>=19)
                            val skip=ByteBuffer.wrap(payload,10,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
                            setByteBuffer("csd-1",ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(skip*1_000_000_000L/48000).apply {flip()})
                            setByteBuffer("csd-2",ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(80_000_000L).apply {flip()})
                        }
                    }
                    decoder=MediaCodec.createDecoderByType(mime).apply {configure(format,null,null,0);start()}
                } else {
                    val codec=decoder ?: error("音频缺少编码配置")
                    var submitted=false;val info=MediaCodec.BufferInfo()
                    while(!closed.get()&&!submitted) {
                        val slot=codec.dequeueInputBuffer(1000)
                        if(slot>=0) {codec.getInputBuffer(slot)!!.apply {clear();put(packet,0,size)};codec.queueInputBuffer(slot,0,size,header and ((1L shl 61)-1),0);submitted=true}
                        drain(codec,info)
                    }
                    drain(codec,info)
                    if(!playing&&track!=null) {report("音频播放中 · ${options.audioCodec}");playing=true}
                }
                val now=android.os.SystemClock.elapsedRealtime()
                if(now-sampleStart>=1000) {
                    val elapsed=now-sampleStart
                    metrics(AudioMetrics(bytesReceived*8f/elapsed,packets*1000f/elapsed,track?.underrunCount ?: 0,activeFormat))
                    sampleStart=now;packets=0;bytesReceived=0
                }
            }
        } catch(e:Exception) {if(!closed.get()) report("音频已停止：${e.message}；视频保持")}
        finally {close()}},"android-audio").start()
    }

    private fun drain(codec:MediaCodec,info:MediaCodec.BufferInfo) {
        while(!closed.get()) {
            val index=codec.dequeueOutputBuffer(info,1000)
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track?.release();val f=codec.outputFormat
                track=output(f.getInteger(MediaFormat.KEY_SAMPLE_RATE),f.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            } else if(index>=0) {
                try {
                    val buffer=codec.getOutputBuffer(index)!!
                    buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    if(track==null) track=output()
                    writePcm(buffer)
                } finally {codec.releaseOutputBuffer(index,false)}
            } else break
        }
    }

    override fun close() {
        if(!closed.compareAndSet(false,true)) return
        runCatching {input.close()};runCatching {track?.pause()};runCatching {track?.flush()};runCatching {track?.release()};track=null
        runCatching {decoder?.stop()};runCatching {decoder?.release()};decoder=null
        runCatching {manager.abandonAudioFocusRequest(focus)}
    }
}
