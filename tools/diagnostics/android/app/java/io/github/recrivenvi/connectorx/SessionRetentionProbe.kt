package io.github.recrivenvi.connectorx

import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.session.RemoteSession

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import java.io.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Local media/stream harness only: no network connection, peer pairing, input injection or UI. */
object SessionRetentionProbe {
    @JvmStatic fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        com.genymobile.scrcpy.Workarounds.apply()
        val handler=Handler(Looper.getMainLooper())
        val pipe=PipedInputStream(1024*1024); val producer=PipedOutputStream(pipe)
        val socket=SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        val session=RemoteSession(Peer("probe","probe","127.0.0.1"),socket,DataInputStream(pipe),
            DataOutputStream(ByteArrayOutputStream()),{ println("CX_RETENTION $it") })
        val output=android.media.ImageReader.newInstance(128,128,android.graphics.ImageFormat.PRIVATE,2,
            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        output.setOnImageAvailableListener({ it.acquireLatestImage()?.close() },handler)
        session.start()
        Thread({
            val encoder=MediaCodec.createEncoderByType("video/avc")
            try {
                val format=MediaFormat.createVideoFormat("video/avc",128,128).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE,300000); setInteger(MediaFormat.KEY_FRAME_RATE,20)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                }
                encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
                val data=DataOutputStream(producer)
                data.writeInt(0x68323634); data.writeLong(Long.MIN_VALUE or 128L); data.writeInt(128); data.flush()
                val info=MediaCodec.BufferInfo()
                var index=0; val end=android.os.SystemClock.elapsedRealtime()+9000
                while(android.os.SystemClock.elapsedRealtime()<end) {
                    val inputIndex=encoder.dequeueInputBuffer(10000)
                    if(inputIndex>=0) {
                        encoder.getInputBuffer(inputIndex)!!.apply { clear(); put(ByteArray(128*128*3/2){ (90+index%50).toByte() }) }
                        encoder.queueInputBuffer(inputIndex,0,128*128*3/2,index++*50000L,0)
                    }
                    var slot=encoder.dequeueOutputBuffer(info,10000)
                    while(slot>=0) {
                        if(info.size>0) {
                            val bytes=ByteArray(info.size)
                            encoder.getOutputBuffer(slot)!!.apply { position(info.offset); limit(info.offset+info.size); get(bytes) }
                            val pts=info.presentationTimeUs or (if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG!=0) (1L shl 62) else 0L)
                            data.writeLong(pts); data.writeInt(bytes.size); data.write(bytes); data.flush()
                        }
                        encoder.releaseOutputBuffer(slot,false); slot=encoder.dequeueOutputBuffer(info,0)
                    }
                    Thread.sleep(40)
                }
                println("CX_RETENTION producer-idle-with-stream-open")
                // Deliberately keep the producer alive: PipedInputStream otherwise reports dead writer.
                Thread.sleep(30000)
            } catch(e:Throwable) { e.printStackTrace() }
            finally { runCatching { encoder.stop() }; encoder.release() }
        },"fixture-encoder").start()
        fun state(label:String) { println("CX_RETENTION $label running=${session.running} closed=${socket.isClosed} size=${session.width}x${session.height} fps=${session.renderedFps} error=${session.videoError}") }
        handler.postDelayed({ state("before-attach"); session.start(output.surface) },2500)
        handler.postDelayed({ state("attached"); session.detachSurface(output.surface) },5000)
        handler.postDelayed({ state("detached"); session.start(output.surface) },7500)
        handler.postDelayed({ state("reattached") },10000)
        handler.postDelayed({
            state("idle-over-20-seconds")
            session.close(); output.close(); producer.close(); pipe.close(); println("CX_RETENTION explicit-test-cleanup")
            System.exit(0)
        },32000)
        Looper.loop()
    }
}
