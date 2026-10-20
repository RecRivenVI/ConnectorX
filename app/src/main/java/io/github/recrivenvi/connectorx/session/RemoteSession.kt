package io.github.recrivenvi.connectorx.session

import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.display.MirrorGeometry

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.*
import io.github.recrivenvi.connectorx.protocol.Wire
import io.github.recrivenvi.connectorx.protocol.AppRequest
import io.github.recrivenvi.connectorx.protocol.MediaOptions
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

class RemoteSession(val peer: Peer, private val socket: SSLSocket, private val input: DataInputStream,
                    private val output: DataOutputStream, private val report: (String) -> Unit,
                    val app: AppRequest? = null, val appTitle: String? = null, initialInfo: String = "{}",
                    val deviceMirror: Boolean = false,val media:MediaOptions=MediaOptions.legacy(app!=null),override val localControl:Boolean=false) : AutoCloseable,SurfaceControlSession {
    var audioStatus by mutableStateOf(if(media.audio) "等待音频" else "音频已关闭");private set
    @Volatile private var audioSocket:SSLSocket?=null
    @Volatile private var audioPlayer:AndroidAudioPlayer?=null
    @Synchronized fun attachAudio(socket:SSLSocket,input:DataInputStream,context:android.content.Context) {
        if(closed.get()) {socket.close();return}
        audioSocket=socket;audioPlayer=AndroidAudioPlayer(context,input,media,::audioFailed) { metrics ->
            main.post {
                audioKbps=metrics.kbps;audioPacketsPerSecond=metrics.packetsPerSecond
                audioUnderruns=metrics.underruns;audioFormat=metrics.format
            }
        }.also {it.play()}
    }
    fun audioFailed(message:String) {main.post {audioStatus=message};report(message)}
    override var width by mutableIntStateOf(0); private set
    override var height by mutableIntStateOf(0); private set
    override var running by mutableStateOf(true); private set
    var info by mutableStateOf(JSONObject(initialInfo)); private set
    var infoError by mutableStateOf<String?>(null)
    var localUiActive = false
    var restarting by mutableStateOf(false)
    var restartMessage by mutableStateOf<String?>(null)
    var renderedFps by mutableFloatStateOf(0f); private set
    var receivedFps by mutableFloatStateOf(0f); private set
    var videoMbps by mutableFloatStateOf(0f); private set
    var relativeVideoDelayMs by mutableFloatStateOf(0f); private set
    var keyframeFps by mutableFloatStateOf(0f); private set
    var averageFrameKiB by mutableFloatStateOf(0f); private set
    var decoderBacklogFrames by mutableIntStateOf(0); private set
    var decoderInputWaitMs by mutableFloatStateOf(0f); private set
    var videoDecoderName by mutableStateOf("等待解码器"); private set
    var videoDecoderHardware by mutableStateOf<Boolean?>(null); private set
    var qualityStatus by mutableStateOf("等待画面"); private set
    var audioKbps by mutableFloatStateOf(0f); private set
    var audioPacketsPerSecond by mutableFloatStateOf(0f); private set
    var audioUnderruns by mutableIntStateOf(0); private set
    var audioFormat by mutableStateOf("等待音频格式"); private set
    val sessionId = info.optInt("sessionId", -1)
    val infoFetching = AtomicBoolean(false)
    val geometryFetching = AtomicBoolean(false)
    var geometry by mutableStateOf(if ((deviceMirror||localControl) && info.optJSONObject("geometry")!=null) MirrorGeometry(info.getJSONObject("geometry")) else null); private set
    override var geometryStale by mutableStateOf(false); private set
    var geometryError by mutableStateOf<String?>(null)
    private var requestedWidth = app?.width ?: 0
    private var requestedHeight = app?.height ?: 0
    override var inputBlocked by mutableStateOf(false); private set
    var videoEpoch by mutableIntStateOf(0); private set
    private data class Touch(val x: Int, val y: Int, val w: Int, val h: Int)
    private val touches = mutableMapOf<Long, Touch>()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val codecThread = HandlerThread("video-codec").apply { start() }
    private val codecHandler = Handler(codecThread.looper)
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var displaySurface: Surface? = null
    private val frameLock = Any()
    private data class EncodedFrame(val bytes:ByteArray,val length:Int,val ptsUs:Long,val flags:Int,val queuedNs:Long)
    private val pendingFrames = java.util.ArrayDeque<EncodedFrame>()
    private var pendingFrameBytes = 0
    private val availableInputs = java.util.ArrayDeque<Int>()
    private val recycledPackets = java.util.ArrayDeque<ByteArray>()
    @Volatile private var decoderReady = false
    private var codecGeneration = 0
    @Volatile private var currentVideoWidth = 0
    @Volatile private var currentVideoHeight = 0
    private var lastDecoderReset = 0L
    private val receivedFrames = AtomicLong()
    private val receivedBytes = AtomicLong()
    private val decodedFrames = AtomicLong()
    private val keyFrames = AtomicLong()
    private val decoderWaitNs = AtomicLong()
    private val discardedFrameOffset = AtomicLong()
    private val relativeDelayUs = AtomicLong()
    private var firstVideoPtsUs = -1L
    private var firstVideoOutputNs = 0L
    var videoError by mutableStateOf<String?>(null); private set
    private val main = Handler(Looper.getMainLooper())
    private val controlLock = java.lang.Object()
    private val controls = java.util.ArrayDeque<ByteArray>()
    private val releases = java.util.ArrayDeque<ByteArray>()
    private val pendingMoves = mutableMapOf<Long,ByteArray>()
    private val sentTouches = mutableMapOf<Long,ByteArray>()
    private val sentKeys = mutableSetOf<Int>()
    private var inFlight: ByteArray? = null
    private var overloaded = false
    override var inputOverloaded by mutableStateOf(false); private set
    private val sender = Thread({
        try {
            while (!closed.get()) {
                val packet = synchronized(controlLock) {
                    while(controls.isEmpty() && releases.isEmpty() && !closed.get()) controlLock.wait()
                    if(closed.get()) return@Thread
                    (if(releases.isEmpty()) controls.removeFirst() else releases.removeFirst()).also { p ->
                        if(p[0].toInt()==2 && p[1].toInt()==2) {
                            val id=java.nio.ByteBuffer.wrap(p,2,8).long
                            if(pendingMoves[id]===p) pendingMoves.remove(id)
                        }
                        inFlight=p
                        if(p[0].toInt()==0 && p[1].toInt()==0) sentKeys.add(java.nio.ByteBuffer.wrap(p,2,4).int)
                        if(p[0].toInt()==2 && p[1].toInt()!=1) sentTouches[java.nio.ByteBuffer.wrap(p,2,8).long]=p
                    }
                }
                Wire.writeControl(output,packet,app!=null)
                synchronized(controlLock) {
                    if(packet[0].toInt()==0 && packet[1].toInt()==1) sentKeys.remove(java.nio.ByteBuffer.wrap(packet,2,4).int)
                    if(packet[0].toInt()==2 && packet[1].toInt()==1) sentTouches.remove(java.nio.ByteBuffer.wrap(packet,2,8).long)
                    if(packet[0].toInt()==21) sentTouches.clear()
                    inFlight=null
                    if(overloaded && releases.isEmpty()) { overloaded=false; main.post { inputOverloaded=false } }
                }
            }
        } catch (_: Exception) { close() } // A failed transport cannot be kept alive locally.
    }, "control-sender").apply { start() }
    fun send(packet: ByteArray) {
        synchronized(controlLock) {
            if(closed.get() || overloaded) return
            if(controls.size>=256) {
                val pointers=sentTouches.toMutableMap()
                val keys=sentKeys.toMutableSet()
                val pending=controls.toList()+listOfNotNull(inFlight)+packet
                for(p in pending) {
                    if(p[0].toInt()==0) keys.add(java.nio.ByteBuffer.wrap(p,2,4).int)
                    if(p[0].toInt()==2) pointers[java.nio.ByteBuffer.wrap(p,2,8).long]=p
                }
                controls.clear(); pendingMoves.clear(); overloaded=true
                pointers.values.forEach { p -> releases.add(p.copyOf().apply { this[1]=1; this[22]=0; this[23]=0 }) }
                keys.forEach { releases.add(Wire.key(1,it)) }
                if(releases.isEmpty()) overloaded=false
                main.post { touches.clear(); inputOverloaded=overloaded }
                report("输入积压，已取消当前输入并保留连接")
            } else {
                controls.addLast(packet)
            }
            controlLock.notifyAll()
        }
    }
    private fun sendTouch(action:Int,id:Long,x:Int,y:Int,w:Int,h:Int) {
        synchronized(controlLock) {
            if(closed.get()||overloaded) return
            if(action==2) {
                pendingMoves[id]?.let { pending ->
                    java.nio.ByteBuffer.wrap(pending).apply {
                        position(10);putInt(x);putInt(y);putShort(w.toShort());putShort(h.toShort())
                    }
                    return
                }
            }
        }
        val packet=Wire.touch(action,id,x,y,w,h)
        synchronized(controlLock) {
            if(action==2 && !closed.get() && !overloaded) pendingMoves[id]=packet
        }
        send(packet)
    }
    fun key(code: Int) { send(Wire.key(0, code)); send(Wire.key(1, code)) }
    fun scroll(vertical:Float) {
        if(inputBlocked||inputOverloaded||restarting||width<=0||height<=0||!running)return
        send(Wire.scroll(width/2,height/2,width,height,0f,vertical))
    }
    fun updateInfo(value: String) { info = JSONObject(value); infoError = null }
    fun updateGeometry(value: MirrorGeometry, epoch: Int) {
        if (epoch != videoEpoch) return
        if (width > 0 && !value.matchesVideo(width, height)) { geometryError = "显示与视频尺寸暂不一致，等待下一次几何快照"; return }
        geometry = value; geometryStale = false; geometryError = null
    }
    override fun touch(action: Int, id: Long, x: Int, y: Int) {
        if (inputBlocked || inputOverloaded || restarting || width <= 0 || height <= 0 || !running) return
        if (action != 0 && id !in touches) return // A resize cancelled this gesture; wait for a fresh DOWN.
        if (action == 0 && (x !in 0 until width || y !in 0 until height)) return
        val touch = Touch(x, y, width, height)
        if (action == 1) touches.remove(id) else touches[id] = touch
        sendTouch(action, id, touch.x, touch.y, touch.w, touch.h)
    }
    override fun cancelTouches() {
        touches.forEach { (id, t) -> send(Wire.touch(1, id, t.x, t.y, t.w, t.h)) }
        touches.clear()
    }
    fun requestResize(w: Int, h: Int) {
        if (app == null || !running || (w == requestedWidth && h == requestedHeight)) return
        runCatching { AppRequest.validateSize(w, h) }.onFailure { report(it.message ?: "尺寸不支持"); return }
        cancelTouches()
        requestedWidth = w; requestedHeight = h; inputBlocked = true
        val epoch = videoEpoch
        send(Wire.resize(w, h))
        main.postDelayed({
            if (videoEpoch == epoch && running) { inputBlocked = false; report("未收到显示尺寸变化，继续使用当前画面坐标") }
        }, 4000)
    }
    fun text(value: String) { runCatching { send(Wire.text(value)) }.onFailure { report(it.message ?: "文本过长") } }
    private fun requestVideoReset() {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastDecoderReset>1000) { lastDecoderReset=now; send(byteArrayOf(17)) }
    }
    private fun recycle(frame:EncodedFrame) {
        synchronized(frameLock) { if(recycledPackets.size<12) recycledPackets.addLast(frame.bytes) }
    }
    private fun clearPendingFrames() {
        synchronized(frameLock) {
            var dropped=0L
            while(pendingFrames.isNotEmpty()) pendingFrames.removeFirst().also { if(it.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0)dropped++;recycle(it) }
            pendingFrameBytes=0
            availableInputs.clear()
            if(dropped>0) discardedFrameOffset.addAndGet(dropped)
        }
    }
    private fun releaseDecoder(reason:String?=null,requestReset:Boolean=false) {
        decoderReady=false
        clearPendingFrames()
        codecGeneration++
        val old=codec;codec=null
        runCatching {old?.stop()};runCatching {old?.release()}
        discardedFrameOffset.set((receivedFrames.get()-decodedFrames.get()).coerceAtLeast(0))
        if(reason!=null) main.post {videoError=reason}
        if(requestReset && displaySurface?.isValid==true) requestVideoReset()
    }
    private fun scheduleDecoderRecovery(reason:String,generation:Int=codecGeneration) {
        codecHandler.post {if(generation==codecGeneration&&!closed.get())releaseDecoder(reason,true)}
    }
    private data class DecoderChoice(val codec:MediaCodec,val lowLatency:Boolean)
    private fun preferredDecoder(mime:String):DecoderChoice {
        val candidates=MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.mapNotNull { info ->
            if(info.isEncoder||info.name.endsWith(".secure",true)||info.supportedTypes.none {it.equals(mime,true)}) return@mapNotNull null
            val low=runCatching {info.getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)}.getOrDefault(false)
            Triple(info,low,(if(info.isHardwareAccelerated)100 else 0)+(if(info.isVendor)20 else 0)+(if(low)40 else 0)+(if(info.name.contains("low_latency",true))10 else 0))
        }.sortedByDescending {it.third}
        for((info,low) in candidates) runCatching {return DecoderChoice(MediaCodec.createByCodecName(info.name),low)}
        val fallback=MediaCodec.createDecoderByType(mime)
        val low=runCatching {fallback.codecInfo.getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)}.getOrDefault(false)
        return DecoderChoice(fallback,low)
    }
    private fun configureDecoder(mime:String,w:Int,h:Int) {
        releaseDecoder()
        val surface=displaySurface?.takeIf {it.isValid} ?: return
        var created:MediaCodec?=null
        try {
            val choice=preferredDecoder(mime)
            val active=choice.codec
            created=active
            val generation=codecGeneration
            active.setCallback(object:MediaCodec.Callback() {
                override fun onInputBufferAvailable(c:MediaCodec,index:Int) {
                    if(c!==codec||generation!=codecGeneration) return
                    synchronized(frameLock) {availableInputs.addLast(index)}
                    drainDecoderInputs(c,generation)
                }
                override fun onOutputBufferAvailable(c:MediaCodec,index:Int,info:MediaCodec.BufferInfo) {
                    if(c!==codec||generation!=codecGeneration) {runCatching {c.releaseOutputBuffer(index,false)};return}
                    try {
                        val nowNs=System.nanoTime()
                        if(info.presentationTimeUs>0) {
                            if(firstVideoPtsUs<0) {firstVideoPtsUs=info.presentationTimeUs;firstVideoOutputNs=nowNs}
                            relativeDelayUs.set(((nowNs-firstVideoOutputNs)/1000-(info.presentationTimeUs-firstVideoPtsUs)).coerceAtLeast(0))
                        }
                        val render=displaySurface?.isValid==true
                        val renderAtNs=if(render&&media.videoBufferMs>0&&info.presentationTimeUs>0)
                            firstVideoOutputNs+(info.presentationTimeUs-firstVideoPtsUs)*1000+media.videoBufferMs*1_000_000L else 0L
                        if(renderAtNs>0)c.releaseOutputBuffer(index,renderAtNs) else c.releaseOutputBuffer(index,render)
                        if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) decodedFrames.incrementAndGet()
                    } catch(e:Exception) {scheduleDecoderRecovery("视频呈现需要恢复：${e.message}",generation)}
                }
                override fun onOutputFormatChanged(c:MediaCodec,format:MediaFormat) {}
                override fun onError(c:MediaCodec,error:MediaCodec.CodecException) {
                    if(c===codec&&generation==codecGeneration) scheduleDecoderRecovery("视频解码需要恢复：${error.diagnosticInfo}",generation)
                }
            },codecHandler)
            val maxInput=(media.videoBitRate/4).coerceIn(1024*1024,16*1024*1024)
            val format=MediaFormat.createVideoFormat(mime,w,h).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,maxInput)
                setInteger(MediaFormat.KEY_PRIORITY,0)
                if(choice.lowLatency)setInteger(MediaFormat.KEY_LOW_LATENCY,1)
            }
            active.configure(format,surface,null,0)
            codec=active
            active.start();decoderReady=true
            main.post {
                videoError=null;videoDecoderName=active.name;videoDecoderHardware=active.codecInfo.isHardwareAccelerated
            }
        } catch(e:Exception) {
            runCatching {created?.release()}
            releaseDecoder("解码器不可用，连接保留：${e.message}")
        }
    }
    private fun obtainPacket(length:Int):ByteArray {
        synchronized(frameLock) {
            val iterator=recycledPackets.iterator()
            while(iterator.hasNext()) {
                val candidate=iterator.next()
                if(candidate.size>=length) {iterator.remove();return candidate}
            }
        }
        return ByteArray(Integer.highestOneBit((length-1).coerceAtLeast(1)).shl(1))
    }
    private fun enqueueFrame(source:ByteArray,length:Int,ptsUs:Long,flags:Int) {
        if(displaySurface?.isValid!=true||!decoderReady) {
            if(flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) discardedFrameOffset.incrementAndGet()
            return
        }
        val bytes=obtainPacket(length);System.arraycopy(source,0,bytes,0,length)
        val frame=EncodedFrame(bytes,length,ptsUs,flags,System.nanoTime())
        var overflow=false
        synchronized(frameLock) {
            val byteLimit=(media.videoBitRate/4).coerceIn(8*1024*1024,32*1024*1024)
            if(pendingFrames.size>=8||pendingFrameBytes+length>byteLimit) overflow=true
            else {pendingFrames.addLast(frame);pendingFrameBytes+=length}
        }
        if(overflow) {
            recycle(frame)
            scheduleDecoderRecovery("视频积压，正在请求新的关键帧")
        } else codecHandler.post {codec?.let {drainDecoderInputs(it,codecGeneration)}}
    }
    private fun drainDecoderInputs(active:MediaCodec,generation:Int) {
        if(active!==codec||generation!=codecGeneration||!decoderReady)return
        while(true) {
            val pair=synchronized(frameLock) {
                if(availableInputs.isEmpty()||pendingFrames.isEmpty())null else availableInputs.removeFirst() to pendingFrames.removeFirst().also {pendingFrameBytes-=it.length}
            } ?: return
            val (index,frame)=pair
            try {
                val buffer=active.getInputBuffer(index) ?: error("解码输入缓冲区不可用")
                buffer.clear();check(buffer.remaining()>=frame.length){"解码输入缓冲区不足"}
                buffer.put(frame.bytes,0,frame.length)
                active.queueInputBuffer(index,0,frame.length,frame.ptsUs,frame.flags)
                decoderWaitNs.addAndGet(System.nanoTime()-frame.queuedNs)
            } catch(e:Exception) {
                recycle(frame);scheduleDecoderRecovery("视频解码需要恢复：${e.message}",generation);return
            }
            recycle(frame)
        }
    }
    fun retryVideo() { codecHandler.post {releaseDecoder("正在恢复视频，连接保留",true)} }
    override fun detachSurface(surface: Surface?) {
        cancelTouches()
        if(surface!=null && displaySurface!==surface) return
        displaySurface=null
        codecHandler.post {releaseDecoder();main.post {qualityStatus="后台保持连接"}}
    }
    override fun start(surface: Surface?) {
        if(surface!=null) {
            if(closed.get()) return
            displaySurface=surface
            runCatching {surface.setFrameRate(media.maxFps,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)}
            if(currentVideoWidth>0 && currentVideoHeight>0) requestVideoReset()
        }
        if (!started.compareAndSet(false, true)) return
        val metrics = Thread({
            var sampleStart = android.os.SystemClock.elapsedRealtime()
            var lastReceivedFrames = 0L
            var lastReceivedBytes = 0L
            var lastDecodedFrames = 0L
            var lastKeyFrames = 0L
            var lastDecoderWaitNs = 0L
            try {
                while (!closed.get()) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - sampleStart >= 1000) {
                        val elapsed=now-sampleStart
                        val currentReceived=receivedFrames.get();val currentBytes=receivedBytes.get()
                        val currentDecoded=decodedFrames.get();val currentKeyFrames=keyFrames.get();val currentWaitNs=decoderWaitNs.get()
                        val fps=(currentDecoded-lastDecodedFrames)*1000f/elapsed
                        val rxFps=(currentReceived-lastReceivedFrames)*1000f/elapsed
                        val mbps=(currentBytes-lastReceivedBytes)*8f/elapsed/1000f
                        val delay=relativeDelayUs.get()/1000f
                        val keyFps=(currentKeyFrames-lastKeyFrames)*1000f/elapsed
                        val intervalFrames=currentReceived-lastReceivedFrames
                        val avgKiB=if(intervalFrames>0)(currentBytes-lastReceivedBytes).toFloat()/intervalFrames/1024f else 0f
                        val backlog=(currentReceived-currentDecoded-discardedFrameOffset.get()).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()
                        val waitMs=(currentWaitNs-lastDecoderWaitNs)/1_000_000f
                        val quality=when {
                            displaySurface?.isValid!=true -> "后台保持连接"
                            rxFps==0f&&fps==0f -> "静止画面"
                            delay>100f||backlog>4||fps+2f<rxFps -> "受限"
                            delay>35f||backlog>2||fps+0.5f<rxFps -> "一般"
                            else -> "良好"
                        }
                        main.post {
                            renderedFps=fps;receivedFps=rxFps;videoMbps=mbps;relativeVideoDelayMs=delay
                            keyframeFps=keyFps;averageFrameKiB=avgKiB;decoderBacklogFrames=backlog
                            decoderInputWaitMs=waitMs;qualityStatus=quality
                        }
                        lastReceivedFrames=currentReceived;lastReceivedBytes=currentBytes
                        lastDecodedFrames=currentDecoded;lastKeyFrames=currentKeyFrames;lastDecoderWaitNs=currentWaitNs
                        sampleStart = now
                    }
                    Thread.sleep(100)
                }
            } catch (e: Exception) { if (!closed.get()) report("视频呈现已暂停，连接保留：${e.message}") }
        }, "video-metrics").apply { start() }
        Thread({
            try {
                socket.soTimeout = 0 // Static scrcpy frames may remain unchanged indefinitely.
                val videoMime=MediaOptions.videoMime(input.readInt())
                var packet=ByteArray(256*1024)
                while (!closed.get()) {
                    val header = input.readLong()
                    val length = input.readInt()
                    if (header < 0) {
                        val w = (header and 0xffffffffL).toInt(); val h = length
                        check(w in 1..8192 && h in 1..8192) { "无效的视频尺寸" }
                        currentVideoWidth=w;currentVideoHeight=h
                        firstVideoPtsUs=-1;firstVideoOutputNs=0;relativeDelayUs.set(0)
                        val configured=CountDownLatch(1)
                        codecHandler.post {try {configureDecoder(videoMime,w,h)} finally {configured.countDown()}}
                        check(configured.await(5,TimeUnit.SECONDS)) {"等待视频解码器超时"}
                        main.post { cancelTouches(); videoEpoch++; width = w; height = h; inputBlocked = false; if (deviceMirror) geometryStale = true }
                        report("视频会话 ${w}×${h} · ${media.videoCodec}")
                        continue
                    }
                    val packetLimit=(media.videoBitRate/4).coerceIn(4*1024*1024,16*1024*1024)
                    check(length in 1..packetLimit) { "视频帧长度异常" }
                    if(packet.size<length) packet=ByteArray(Integer.highestOneBit(length-1).shl(1)); input.readFully(packet,0,length)
                    val config = header and (1L shl 62) != 0L
                    receivedBytes.addAndGet(length.toLong())
                    if(!config) {
                        receivedFrames.incrementAndGet()
                        if(header and (1L shl 61)!=0L) keyFrames.incrementAndGet()
                    }
                    enqueueFrame(packet,length,header and ((1L shl 61)-1),if(config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
                }
            } catch (e: Exception) { if (!closed.get()) report("远程连接结束：${e.message ?: e.javaClass.simpleName}") }
            finally { close(); metrics.interrupt() }
        }, "video-decoder").start()
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        audioPlayer?.close();audioPlayer=null;runCatching {audioSocket?.close()};audioSocket=null
        runCatching { socket.close() }; synchronized(controlLock) { controlLock.notifyAll() }; sender.interrupt()
        displaySurface=null
        codecHandler.post {releaseDecoder();codecThread.quitSafely()}
        main.post { running = false }
    }
}
