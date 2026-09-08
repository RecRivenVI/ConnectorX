package io.github.recrivenvi.connectorx.session

import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.display.MirrorGeometry

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
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
import javax.net.ssl.SSLSocket

class RemoteSession(val peer: Peer, private val socket: SSLSocket, private val input: DataInputStream,
                    private val output: DataOutputStream, private val report: (String) -> Unit,
                    val app: AppRequest? = null, val appTitle: String? = null, initialInfo: String = "{}",
                    val deviceMirror: Boolean = false,val media:MediaOptions=MediaOptions.legacy(app!=null)) : AutoCloseable {
    var audioStatus by mutableStateOf(if(media.audio) "等待音频" else "音频已关闭");private set
    @Volatile private var audioSocket:SSLSocket?=null
    @Volatile private var audioPlayer:AndroidAudioPlayer?=null
    @Synchronized fun attachAudio(socket:SSLSocket,input:DataInputStream,context:android.content.Context) {
        if(closed.get()) {socket.close();return}
        audioSocket=socket;audioPlayer=AndroidAudioPlayer(context,input,media,::audioFailed).also {it.play()}
    }
    fun audioFailed(message:String) {main.post {audioStatus=message};report(message)}
    var width by mutableIntStateOf(0); private set
    var height by mutableIntStateOf(0); private set
    var running by mutableStateOf(true); private set
    var info by mutableStateOf(JSONObject(initialInfo)); private set
    var infoError by mutableStateOf<String?>(null)
    var localUiActive = false
    var restarting by mutableStateOf(false)
    var restartMessage by mutableStateOf<String?>(null)
    var renderedFps by mutableFloatStateOf(0f); private set
    val sessionId = info.optInt("sessionId", -1)
    val infoFetching = AtomicBoolean(false)
    val geometryFetching = AtomicBoolean(false)
    var geometry by mutableStateOf(if (deviceMirror) MirrorGeometry(info.getJSONObject("geometry")) else null); private set
    var geometryStale by mutableStateOf(false); private set
    var geometryError by mutableStateOf<String?>(null)
    private var requestedWidth = app?.width ?: 0
    private var requestedHeight = app?.height ?: 0
    var inputBlocked by mutableStateOf(false); private set
    var videoEpoch by mutableIntStateOf(0); private set
    private data class Touch(val x: Int, val y: Int, val w: Int, val h: Int)
    private val touches = mutableMapOf<Long, Touch>()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val lock = Any()
    private var codec: MediaCodec? = null
    private var displaySurface: Surface? = null
    private var idleReader: android.media.ImageReader? = null
    private var lastDecoderReset = 0L
    var videoError by mutableStateOf<String?>(null); private set
    private val main = Handler(Looper.getMainLooper())
    private val controlLock = java.lang.Object()
    private val controls = java.util.ArrayDeque<ByteArray>()
    private val releases = java.util.ArrayDeque<ByteArray>()
    private val sentTouches = mutableMapOf<Long,ByteArray>()
    private val sentKeys = mutableSetOf<Int>()
    private var inFlight: ByteArray? = null
    private var overloaded = false
    var inputOverloaded by mutableStateOf(false); private set
    private val sender = Thread({
        try {
            while (!closed.get()) {
                val packet = synchronized(controlLock) {
                    while(controls.isEmpty() && releases.isEmpty() && !closed.get()) controlLock.wait()
                    if(closed.get()) return@Thread
                    (if(releases.isEmpty()) controls.removeFirst() else releases.removeFirst()).also { p ->
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
                controls.clear(); overloaded=true
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
    fun key(code: Int) { send(Wire.key(0, code)); send(Wire.key(1, code)) }
    fun updateInfo(value: String) { info = JSONObject(value); infoError = null }
    fun updateGeometry(value: MirrorGeometry, epoch: Int) {
        if (epoch != videoEpoch) return
        if (width > 0 && !value.matchesVideo(width, height)) { geometryError = "显示与视频尺寸暂不一致，等待下一次几何快照"; return }
        geometry = value; geometryStale = false; geometryError = null
    }
    fun touch(action: Int, id: Long, x: Int, y: Int) {
        if (inputBlocked || inputOverloaded || restarting || width <= 0 || height <= 0 || !running) return
        if (action != 0 && id !in touches) return // A resize cancelled this gesture; wait for a fresh DOWN.
        if (action == 0 && (x !in 0 until width || y !in 0 until height)) return
        val touch = Touch(x, y, width, height)
        if (action == 1) touches.remove(id) else touches[id] = touch
        send(Wire.touch(action, id, touch.x, touch.y, touch.w, touch.h))
    }
    fun cancelTouches() {
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
    private fun resetDecoderLocked(reason: String) {
        runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
        main.post { videoError = reason }
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastDecoderReset>1000) { lastDecoderReset=now; send(byteArrayOf(17)) }
    }
    fun retryVideo() { synchronized(lock) { resetDecoderLocked("正在恢复视频，连接保留") } }
    fun detachSurface(surface: Surface? = null) {
        cancelTouches()
        synchronized(lock) {
            if(surface!=null && displaySurface!==surface) return
            displaySurface=null
            try { idleReader?.surface?.let { codec?.setOutputSurface(it) } }
            catch(e:Exception) { resetDecoderLocked("画面暂离，等待重新挂载：${e.message}") }
        }
    }
    fun start(surface: Surface? = null) {
        if(surface!=null) synchronized(lock) {
            if(closed.get()) return
            displaySurface=surface
            try { codec?.setOutputSurface(surface) }
            catch(e:Exception) { resetDecoderLocked("正在重新挂载画面：${e.message}") }
        }
        if (!started.compareAndSet(false, true)) return
        val render = Thread({
            val info = MediaCodec.BufferInfo()
            var sampleStart = android.os.SystemClock.elapsedRealtime()
            var frames = 0
            try {
                while (!closed.get()) {
                    synchronized(lock) {
                        codec?.let { c ->
                            try {
                                val index = c.dequeueOutputBuffer(info, 1000)
                                if (index >= 0) { c.releaseOutputBuffer(index, true); frames++ }
                            } catch(e:Exception) { resetDecoderLocked("视频呈现需要恢复：${e.message}") }
                        }
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - sampleStart >= 1000) {
                        val fps = frames * 1000f / (now - sampleStart)
                        main.post { renderedFps = fps }
                        frames = 0; sampleStart = now
                    }
                    Thread.sleep(1)
                }
            } catch (e: Exception) { if (!closed.get()) report("视频呈现已暂停，连接保留：${e.message}") }
        }, "video-render").apply { start() }
        Thread({
            try {
                socket.soTimeout = 0 // Static scrcpy frames may remain unchanged indefinitely.
                val videoMime=MediaOptions.videoMime(input.readInt())
                while (!closed.get()) {
                    val header = input.readLong()
                    val length = input.readInt()
                    if (header < 0) {
                        val w = (header and 0xffffffffL).toInt(); val h = length
                        check(w in 1..8192 && h in 1..8192) { "无效的视频尺寸" }
                        synchronized(lock) {
                            runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
                            try { idleReader?.close(); idleReader=null
                            idleReader = android.media.ImageReader.newInstance(w,h,android.graphics.ImageFormat.PRIVATE,2,
                                android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE).apply {
                                setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } },main)
                            }
                            codec = MediaCodec.createDecoderByType(videoMime)
                            codec!!.apply {
                                val format = MediaFormat.createVideoFormat(videoMime, w, h)
                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
                                if (android.os.Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                                configure(format, displaySurface?.takeIf { it.isValid } ?: idleReader!!.surface, null, 0); start()
                            }; main.post { videoError=null } }
                            catch(e:Exception) { resetDecoderLocked("解码器不可用，连接保留：${e.message}") }
                        }
                        main.post { cancelTouches(); videoEpoch++; width = w; height = h; inputBlocked = false; if (deviceMirror) geometryStale = true }
                        report("视频会话 ${w}×${h} · ${media.videoCodec}")
                        continue
                    }
                    check(length in 1..(4 * 1024 * 1024)) { "视频帧长度异常" }
                    val bytes = ByteArray(length); input.readFully(bytes)
                    val config = header and (1L shl 62) != 0L
                    var submitted = false
                    while (!closed.get() && !submitted) {
                        synchronized(lock) {
                            val c = codec
                            if(c==null) { submitted=true; return@synchronized } // Drain media while decoder recovers.
                            try { val slot = c.dequeueInputBuffer(1000)
                            if (slot >= 0) {
                                val buffer = c.getInputBuffer(slot)!!; buffer.clear()
                                check(buffer.remaining() >= bytes.size) { "解码输入缓冲区不足" }
                                buffer.put(bytes)
                                c.queueInputBuffer(slot, 0, bytes.size, header and ((1L shl 61) - 1),
                                    if (config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
                                submitted = true
                            }
                            } catch(e:Exception) { resetDecoderLocked("视频解码需要恢复：${e.message}"); submitted=true }
                        }
                    }
                }
            } catch (e: Exception) { if (!closed.get()) report("远程连接结束：${e.message ?: e.javaClass.simpleName}") }
            finally { close(); render.interrupt() }
        }, "video-decoder").start()
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        audioPlayer?.close();audioPlayer=null;runCatching {audioSocket?.close()};audioSocket=null
        runCatching { socket.close() }; synchronized(controlLock) { controlLock.notifyAll() }; sender.interrupt()
        synchronized(lock) { runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
            idleReader?.close(); idleReader=null; displaySurface=null }
        main.post { running = false }
    }
}
