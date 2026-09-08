package io.github.recrivenvi.connectorx.windows

import androidx.compose.runtime.*
import io.github.recrivenvi.connectorx.protocol.*
import org.json.JSONObject
import java.awt.*
import java.io.*
import java.util.concurrent.atomic.*
import javax.net.ssl.SSLSocket
import javax.swing.*

class DesktopSession(val peer:DesktopPeer,val app:AppRequest?,private val socket:SSLSocket,
    private val input:DataInputStream,private val output:DataOutputStream,private val report:(String)->Unit,
    initialInfo:String="{}",val deviceMirror:Boolean=false,val capabilities:JSONObject=JSONObject(),
    initialOptions:AppOptions=AppOptions(),val media:MediaOptions=MediaOptions.legacy(app!=null),val changed:(DesktopSession)->Unit={},private val ended:(SSLSocket)->Unit={}):AutoCloseable {
    var audioStatus by mutableStateOf(if(media.audio) "等待音频" else "音频已关闭");private set
    private var videoCodec=0x68323634
    @Volatile private var audioSocket:SSLSocket?=null
    @Volatile private var audioPlayer:WindowsAudioPlayer?=null
    @Synchronized fun attachAudio(socket:SSLSocket,input:DataInputStream) {
        if(closed.get()) {socket.close();return}
        audioSocket=socket;audioPlayer=WindowsAudioPlayer(input,media,::audioMessage).also {it.play()}
    }
    fun audioMessage(message:String) {SwingUtilities.invokeLater {audioStatus=message}}
    var running by mutableStateOf(true); private set
    var frames by mutableLongStateOf(0); private set
    var renderedFps by mutableFloatStateOf(0f); private set
    val infoFetching=AtomicBoolean(false)
    var info by mutableStateOf(JSONObject(initialInfo))
    val sessionId=info.optInt("sessionId",-1)
    var error by mutableStateOf<String?>(null)
    var videoError by mutableStateOf<String?>(null); private set
    var restarting by mutableStateOf(false)
    var restartMessage by mutableStateOf<String?>(null)
    var options by mutableStateOf(initialOptions)
    var width by mutableIntStateOf(0); private set
    var height by mutableIntStateOf(0); private set
    var inputBlocked by mutableStateOf(false); private set
    var touchSupport by mutableStateOf("正在检查触摸设备")
    var window:Window?=null
    val panel=VideoPanel(this)
    private val closed=AtomicBoolean(false);private val started=AtomicBoolean(false)
    private val queue=ControlQueue()
    private val epoch=AtomicLong();private val decoder=AtomicReference<FfmpegDecoder?>()
    private val decoded=AtomicLong();private var lastSample=System.nanoTime();private var lastFrames=0L
    private var lastReset=0L;private var sender:Thread?=null
    private var requestedWidth=app?.width ?: 0;private var requestedHeight=app?.height ?: 0
    private val fpsTimer=Timer(1000) {
        val now=System.nanoTime();val count=decoded.get()
        frames=count;renderedFps=((count-lastFrames)*1e9/(now-lastSample)).toFloat();lastFrames=count;lastSample=now
    }
    fun raise() {window?.let {if(it is Frame) it.extendedState=Frame.NORMAL;it.toFront();it.requestFocus()}}
    fun send(packet:ByteArray) {
        if(!closed.get()&&!queue.offer(packet)) {
            SwingUtilities.invokeLater {panel.cancelInputs()};report("输入积压，取消当前输入并保留连接")
        }
    }
    fun key(code:Int) {send(Wire.key(0,code));send(Wire.key(1,code))}
    fun text(text:String) {
        if(text.isEmpty()) return
        if(text.any {it.code>127}) {
            if(options.unicodePaste&&capabilities.optBoolean("clipboardPaste")) {
                runCatching {send(Wire.paste(text))}.onFailure {error=it.message}
            } else error="此文本需要已升级手机端的 Unicode 粘贴能力，并启用输入法粘贴选项。连接已保留。"
        } else text.chunked(250).forEach {send(Wire.text(it))}
    }
    fun paste(text:String) {
        if(!capabilities.optBoolean("clipboardPaste")) {error="手机端尚不支持粘贴，请升级 Android APK";return}
        runCatching {send(Wire.paste(text))}.onFailure {error=it.message}
    }
    fun resize(w:Int,h:Int) {
        if(app==null||!options.followWindow||!running||w==requestedWidth&&h==requestedHeight) return
        if(w !in 128..4096||h !in 128..4096) {error="窗口物理像素超出 128–4096 范围";return}
        panel.cancelInputs();requestedWidth=w;requestedHeight=h;inputBlocked=true;send(Wire.resize(w,h))
        val generation=epoch.get()
        Timer(4000) {if(epoch.get()==generation&&running) {inputBlocked=false;error="未收到新的显示尺寸，继续按当前视频坐标控制"}}.apply {isRepeats=false;start()}
    }
    @Synchronized private fun requestReset() {
        val now=System.nanoTime();if(now-lastReset>1_000_000_000L) {lastReset=now;send(byteArrayOf(17))}
    }
    fun retryVideo() {epoch.incrementAndGet();decoder.getAndSet(null)?.close();videoError="正在恢复视频，连接保持";requestReset()}
    private fun createDecoder(generation:Long,w:Int,h:Int) {
        decoder.getAndSet(null)?.close()
        try {
            val next=FfmpegDecoder(frame={image->
                if(epoch.get()==generation&&!closed.get()) {panel.image=image;panel.repaint();decoded.incrementAndGet()}
            },failure={reason->
                if(epoch.get()==generation&&!closed.get()) {
                    SwingUtilities.invokeLater {videoError="视频暂不可用：$reason"};requestReset()
                }
            },codecId=videoCodec,width=w,height=h)
            decoder.set(next)
            if(closed.get()||epoch.get()!=generation) decoder.getAndSet(null)?.close()
        } catch(e:Exception) {SwingUtilities.invokeLater {videoError="无法启动解码器：${e.message}"};requestReset()}
    }
    fun open() {
        if(!started.compareAndSet(false,true)) return
        fpsTimer.start()
        if(deviceMirror) runCatching {info.optJSONObject("geometry")?.let {panel.geometry=DesktopGeometry(it)}}.onFailure {error=it.message}
        sender=Thread({try {while(!closed.get()) {val packet=queue.take() ?: break;Wire.writeControl(output,packet,app!=null)}}
            catch(e:Exception) {if(!closed.get()) report("输入连接结束：${e.message}");close()}},"windows-input").apply {isDaemon=true;start()}
        Thread({try {
            socket.soTimeout=0;videoCodec=input.readInt();MediaOptions.videoMime(videoCodec)
            while(!closed.get()) {
                val header=input.readLong();val length=input.readInt()
                if(header<0) {
                    val w=(header and 0xffffffffL).toInt();check(w in 1..8192&&length in 1..8192)
                    val generation=epoch.incrementAndGet()
                    SwingUtilities.invokeLater {panel.setVideoSize(w,length);width=w;height=length;inputBlocked=false;videoError=null;changed(this)}
                    createDecoder(generation,w,length);continue
                }
                check(length in 1..4*1024*1024);val bytes=ByteArray(length);input.readFully(bytes)
                try {decoder.get()?.feed(bytes,header and ((1L shl 61)-1),header and (1L shl 62)!=0L)} catch(e:Exception) {
                    decoder.getAndSet(null)?.close();SwingUtilities.invokeLater {videoError="正在恢复解码：${e.message}"};requestReset()
                }
            }
        } catch(e:Exception) {if(!closed.get()) {SwingUtilities.invokeLater {error=e.message};report("${peer.name} 连接结束：${e.message}")}}
        finally {close()}},"windows-video").apply {isDaemon=true;start()}
    }
    override fun close() {
        if(!closed.compareAndSet(false,true)) return
        audioPlayer?.close();audioPlayer=null;runCatching {audioSocket?.close()};audioSocket=null
        runCatching {socket.close()};queue.close();sender?.interrupt();epoch.incrementAndGet();decoder.getAndSet(null)?.close();ended(socket)
        SwingUtilities.invokeLater {fpsTimer.stop();panel.disposeInput();running=false}
    }
}
