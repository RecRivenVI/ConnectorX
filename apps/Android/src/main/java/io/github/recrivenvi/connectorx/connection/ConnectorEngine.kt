package io.github.recrivenvi.connectorx.connection

import io.github.recrivenvi.connectorx.display.MirrorGeometry
import io.github.recrivenvi.connectorx.runtimeclient.RuntimeChannels
import io.github.recrivenvi.connectorx.runtimeclient.RuntimeConnection
import io.github.recrivenvi.connectorx.session.RemoteSession

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.*
import io.github.recrivenvi.connectorx.protocol.DeviceProfile
import io.github.recrivenvi.connectorx.protocol.Wire
import io.github.recrivenvi.connectorx.protocol.AppRequest
import io.github.recrivenvi.connectorx.protocol.MediaOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

data class Candidate(val name: String, val host: String, val id: String)
class PairPrompt(val name: String, val code: String, val response: CompletableFuture<Boolean>)
data class RemoteApp(val packageName: String, val name: String)
data class AppCatalog(val peer: Peer, val host: String, val apps: List<RemoteApp>)

class ConnectorEngine(private val context: Context) {
    private val mediaPrefs=context.getSharedPreferences("media_options",Context.MODE_PRIVATE)
    var mediaOptions by mutableStateOf(runCatching {MediaOptions.decode(mediaPrefs.getString("config","")!!)}.getOrDefault(MediaOptions.defaults()));private set
    fun saveMedia(values:Map<String,String>):String?=try {
        val options=MediaOptions.fromValues(values)
        check(mediaPrefs.edit().putString("config",options.encode()).commit()) {"参数保存失败"}
        mediaOptions=options;null
    } catch(e:Exception) {e.message ?: "参数无效"}
    private data class HostAudio(val peer:String,val token:Int,val stream:InputStream,val stop:()->Unit,val claimed:AtomicBoolean=AtomicBoolean(false),@Volatile var socket:SSLSocket?=null)
    @Volatile private var hostAudio:HostAudio?=null
    private val main = Handler(Looper.getMainLooper())
    private fun ui(action: () -> Unit) { main.post(action) }
    var status by mutableStateOf("准备就绪"); private set
    var privilege by mutableStateOf("被控端请先启用 Shizuku"); private set
    var hosting by mutableStateOf(false); private set
    var pairing by mutableStateOf(false); private set
    var peers by mutableStateOf(listOf<Peer>()); private set
    var candidates by mutableStateOf(listOf<Candidate>()); private set
    var prompt by mutableStateOf<PairPrompt?>(null); private set
    var remote by mutableStateOf<RemoteSession?>(null); private set
    val sessions = mutableStateListOf<RemoteSession>()
    var connectingPeerId by mutableStateOf<String?>(null); private set
    var controlError by mutableStateOf<String?>(null); private set
    var catalog by mutableStateOf<AppCatalog?>(null); private set
    var castApps by mutableStateOf<AppCatalog?>(null); private set
    var loadingApps by mutableStateOf(false); private set
    val identity = Identity(context)
    val runtime = RuntimeConnection(context) { message -> ui { privilege = message } }
    private val tasks = ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, SynchronousQueue())
    private val sockets = ConcurrentHashMap<SSLSocket, String>()
    private val serving = AtomicBoolean(false)
    private val asking = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    @Volatile private var server: SSLServerSocket? = null
    @Volatile private var pairingUntil = 0L
    @Volatile private var activePeerId: String? = null
    @Volatile private var activeSessionId = -1
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    init { peers = identity.peers() }

    fun message(value: String) { ui { status = value }; Log.i("ConnectorX", value) }
    fun work(task: () -> Unit): Boolean {
        return try {
            tasks.execute { try { task() } catch (e: Exception) {
                Log.e("ConnectorX", "Operation failed", e)
                message("操作失败：${e.message ?: e.javaClass.simpleName}")
            } }
            true
        } catch (_: RejectedExecutionException) { message("操作较多，请稍后重试"); false }
    }
    fun enablePairing() {
        if (!hosting) { message("请先开启局域网接收"); return }
        pairingUntil = android.os.SystemClock.elapsedRealtime() + 120_000
        pairing = true
        message("配对入口已开放 2 分钟；请在另一台设备发起配对")
        main.postDelayed({ if (!pairingOpen()) pairing = false }, 120_100)
    }
    private fun pairingOpen() = android.os.SystemClock.elapsedRealtime() < pairingUntil
    private fun refreshPeers() { val saved = identity.peers(); ui { peers = saved } }
    fun forget(peer: Peer) = work {
        identity.remove(peer.id)
        sockets.entries.filter { it.value == peer.id }.forEach { runCatching { it.key.close() } }
        sessions.filter { it.peer.id==peer.id }.forEach { it.close() }
        sessions.removeAll { it.peer.id==peer.id }
        if(remote?.peer?.id==peer.id) remote=null
        refreshPeers(); message("已解除配对，旧授权立即失效")
    }
    fun startHost() = work {
        if (server != null) return@work
        val tls = identity.tls { id -> identity.paired(id) || pairingOpen() }
        val listener = tls.serverSocketFactory.createServerSocket() as SSLServerSocket
        listener.reuseAddress = true
        listener.needClientAuth = true
        listener.enabledProtocols = arrayOf("TLSv1.3")
        listener.bind(InetSocketAddress(Wire.PORT))
        server = listener
        ui { hosting = true; advertise(); discover() }
        message("局域网接收已开启 · 端口 ${Wire.PORT}")
        Thread({
            while (!listener.isClosed) {
                try {
                    val socket = listener.accept() as SSLSocket
                    sockets[socket] = ""
                    try { tasks.execute { serve(socket) } }
                    catch (_: RejectedExecutionException) { sockets.remove(socket); socket.close() }
                } catch (_: IOException) { break }
            }
        }, "connectorx-listener").start()
    }
    fun stopHost() {
        pairingUntil = 0
        prompt?.response?.complete(false)
        runCatching { server?.close() }; server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null
        hosting = false; pairing = false
        message("已停止接收新连接；已建立连接与配对关系保留")
    }
    private fun serve(socket: SSLSocket) {
        var phase = "TLS 握手"
        try {
            socket.use {
                socket.soTimeout = 15_000; socket.tcpNoDelay = true
                socket.startHandshake()
                phase = "协议读取"
                val id = identity.remoteId(socket)
                sockets[socket] = id
                val input = DataInputStream(socket.inputStream)
                val output = DataOutputStream(socket.outputStream)
                require(input.readUTF() == Wire.MAGIC) { "协议版本不兼容" }
                when (val operation = input.readUTF()) {
                    "PAIR", "PAIR2" -> {
                        phase = "配对确认"
                        require(pairingOpen()) { "配对入口未开放" }
                        val name = input.readUTF().take(80)
                        val profile = if(operation=="PAIR2") DeviceProfile.read(input) else DeviceProfile.ANDROID
                        output.writeUTF(Build.MODEL.take(80))
                        if(operation=="PAIR2") DeviceProfile.ANDROID.write(output)
                        output.flush()
                        val approval = requestConfirmation(name, id)
                        socket.soTimeout = 120_000
                        // Both users confirm the same 96-bit certificate comparison before saving.
                        if (!input.readBoolean() || !approval.get(110, TimeUnit.SECONDS) || !pairingOpen()) {
                            output.writeBoolean(false); output.flush(); return
                        }
                        identity.save(Peer(id, name, socket.inetAddress.hostAddress!!, profile))
                        output.writeBoolean(true); output.flush()
                        refreshPeers(); message("已与 $name 持久配对")
                        pairingUntil = 0; ui { pairing = false }
                    }
                    "CAPABILITIES" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        output.writeUTF("OK"); Wire.writeMetadata(output,"{\"scroll\":true,\"clipboardPaste\":true,\"mediaOptions\":1}")
                    }
                    "PROFILE" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        output.writeUTF("OK"); output.writeUTF(Build.MODEL); DeviceProfile.ANDROID.write(output); output.flush()
                    }
                    "APPS" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        try {
                            val json = runtime.service?.listApps() ?: throw IllegalStateException("被控端尚未启用 Shizuku")
                            output.writeUTF("OK"); Wire.writeMetadata(output, json)
                        } catch (e: Exception) { output.writeUTF("读取应用失败：${e.message}"); output.flush() }
                    }
                    "RESTART_APP" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        val token=input.readInt()
                        try {
                            check(activePeerId==id && activeSessionId==token) { "会话已结束或不属于此设备" }
                            val result=runtime.service?.restartApp(token) ?: error("被控端权限不可用")
                            output.writeUTF("OK"); output.writeUTF(result); output.flush()
                        } catch(e:Exception) { output.writeUTF((e.message ?: "重启失败").take(1000)); output.flush() }
                    }
                    "SESSION_INFO" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        val token = input.readInt()
                        if (activePeerId != id || activeSessionId != token) {
                            output.writeUTF("会话已结束"); output.flush()
                        } else {
                            val info = runtime.service?.sessionInfo() ?: "{}"
                            output.writeUTF("OK"); Wire.writeMetadata(output, info)
                        }
                    }
                    "GEOMETRY" -> {
                        require(identity.paired(id)) { "未授权设备" }
                        try {
                            val geometry = runtime.service?.screenGeometry() ?: throw IllegalStateException("被控端尚未启用 Shizuku")
                            output.writeUTF("OK"); Wire.writeMetadata(output, geometry)
                        } catch (e: Exception) { output.writeUTF("几何读取失败：${e.message}"); output.flush() }
                    }
                    "AUDIO" -> {
                        require(identity.paired(id)) {"未授权设备"}
                        val token=input.readInt();val source=hostAudio
                        if(source==null||source.peer!=id||source.token!=token||!source.claimed.compareAndSet(false,true)) {
                            output.writeUTF("音频会话不可用或已连接");output.flush();return
                        }
                        source.socket=socket
                        output.writeUTF("OK");output.flush();socket.soTimeout=0
                        try {source.stream.copyTo(output,16384)} finally {source.socket=null;source.stop()}
                    }
                    "MEDIA", "MIRROR", "APP", "APP_OPTIONS", "DEVICE" -> {
                        phase = "远程控制会话"
                        require(identity.paired(id)) { "未授权设备" }
                        val mode=if(operation=="MEDIA") input.readUTF() else operation
                        require(mode in listOf("MIRROR","APP","APP_OPTIONS","DEVICE")) {"无效的会话模式"}
                        val media=if(operation=="MEDIA") MediaOptions.read(input) else null
                        val app = if (mode == "APP_OPTIONS") AppRequest.readOptions(input)
                            else if (mode == "APP") AppRequest.read(input) else null
                        if (!serving.compareAndSet(false, true)) {
                            output.writeUTF("被控设备已有远程控制会话，请先断开或等待当前启动结束")
                            output.flush()
                            return
                        }
                        activePeerId = id
                        try { mirror(socket, input, output, id, app, mode == "DEVICE",media) }
                        finally { activePeerId = null; activeSessionId = -1; serving.set(false) }
                    }
                    else -> throw IOException("不支持的操作")
                }
            }
        } catch (e: Exception) {
            Log.e("ConnectorX", "Connection failed at $phase", e)
            message("$phase 失败：${e.message ?: e.javaClass.simpleName}")
        } finally { sockets.remove(socket); runCatching { socket.close() } }
    }
    private fun requestConfirmation(name: String, id: String): CompletableFuture<Boolean> {
        check(asking.compareAndSet(false, true)) { "请先处理当前配对请求" }
        val response = CompletableFuture<Boolean>()
        val code = Wire.comparison(identity.id, id).uppercase().chunked(4).joinToString(" ")
        val p = PairPrompt(name, code, response)
        ui { prompt = p }
        response.whenComplete { _, _ -> asking.set(false); ui { if (prompt === p) prompt = null } }
        main.postDelayed({ response.complete(false) }, 110_000)
        return response
    }
    fun pair(host: String) {
        if (!connecting.compareAndSet(false, true)) return
        if (!work {
            try {
                connect(host, null).use { socket ->
                    val input = DataInputStream(socket.inputStream)
                    val output = DataOutputStream(socket.outputStream)
                    output.writeUTF(Wire.MAGIC); output.writeUTF("PAIR2"); output.writeUTF(Build.MODEL); DeviceProfile.ANDROID.write(output); output.flush()
                    val name = input.readUTF().take(80)
                    val profile = DeviceProfile.read(input)
                    val id = identity.remoteId(socket)
                    require(id != identity.id) { "不能与自己配对" }
                    val accepted = requestConfirmation(name, id).get(110, TimeUnit.SECONDS)
                    output.writeBoolean(accepted); output.flush()
                    socket.soTimeout = 120_000
                    check(input.readBoolean() && accepted) { "配对取消或被拒绝" }
                    identity.save(Peer(id, name, host, profile)); refreshPeers(); message("已与 $name 持久配对")
                }
            } finally { connecting.set(false) }
        }) connecting.set(false)
    }
    private fun connect(host: String, expected: String?): SSLSocket {
        require(host.isNotBlank() && host.length <= 253 && !host.contains('/')) { "请输入设备 IP 或主机名，不含端口" }
        val socket = identity.tls { id -> expected == null || (id == expected && identity.paired(id)) }
            .socketFactory.createSocket() as SSLSocket
        try {
            socket.enabledProtocols = arrayOf("TLSv1.3")
            socket.soTimeout = 15_000; socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host.trim(), Wire.PORT), 8_000)
            socket.startHandshake()
            return socket
        } catch (e: Exception) {
            socket.close()
            throw IOException("连接或 TLS 握手失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    fun control(peer: Peer, host: String = peer.host, app: AppRequest? = null, appTitle: String? = null, deviceMirror: Boolean = false) {
        if (!peer.profile.canHost()) { message("Windows 仅作为控制方，请使用镜像到此电脑或投送本机应用"); return }
        sessions.removeAll { !it.running }
        sessions.firstOrNull { it.peer.id==peer.id && it.running }?.let { existing ->
            if(existing.app?.packageName==app?.packageName && existing.deviceMirror==deviceMirror) {
                resume(existing); message("已恢复现有连接")
            } else {
                controlError="此设备已有连接，请进入原会话，或退出该会话后再切换模式/应用。现有连接未关闭。"
            }
            return
        }
        if (!connecting.compareAndSet(false, true)) { message("正在连接，请等待当前操作完成"); return }
        connectingPeerId = peer.id
        controlError = null
        message("正在连接 ${peer.name}，等待被控端启动画面…")
        if (!work {
            try {
                connect(host,peer.id).use { probe ->
                    val out=DataOutputStream(probe.outputStream);val input=DataInputStream(probe.inputStream)
                    out.writeUTF(Wire.MAGIC);out.writeUTF("CAPABILITIES");out.flush()
                    val supported=runCatching {input.readUTF()=="OK"&&JSONObject(Wire.readMetadata(input)).optInt("mediaOptions")>=1}.getOrDefault(false)
                    check(supported) {"请升级被控 Android 的 ConnectorX，以使用视频和音频参数"}
                }
                val socket = connect(host, peer.id)
                try {
                    val output = DataOutputStream(socket.outputStream)
                    val input = DataInputStream(socket.inputStream)
                    socket.soTimeout = if (app == null && !deviceMirror) 15_000 else 40_000
                    output.writeUTF(Wire.MAGIC);output.writeUTF("MEDIA");output.writeUTF(if (deviceMirror) "DEVICE" else if (app == null) "MIRROR" else if(app.systemDecorations) "APP_OPTIONS" else "APP")
                    val selectedMedia=mediaOptions;selectedMedia.write(output)
                    if(app?.systemDecorations==true) app.writeOptions(output) else app?.write(output)
                    output.flush()
                    val response = input.readUTF()
                    check(response == "OK") { response }
                    identity.save(peer.copy(host = host)); refreshPeers()
                    val info = Wire.readMetadata(input)
                    val session = RemoteSession(peer.copy(host = host), socket, input, output, ::message, app, appTitle, info, deviceMirror,selectedMedia)
                    session.start()
                    if(selectedMedia.audio) {
                        Thread({runCatching {
                            val audioSocket=connect(host,peer.id)
                            try {
                                val audioOut=DataOutputStream(audioSocket.outputStream);val audioIn=DataInputStream(audioSocket.inputStream)
                                audioOut.writeUTF(Wire.MAGIC);audioOut.writeUTF("AUDIO");audioOut.writeInt(session.sessionId);audioOut.flush()
                                val result=audioIn.readUTF();check(result=="OK") {result};audioSocket.soTimeout=0
                                session.attachAudio(audioSocket,audioIn,context)
                            } catch(e:Exception) {audioSocket.close();throw e}
                        }.onFailure {session.audioFailed(it.message ?: "音频连接失败")} },"audio-connect").start()
                    }
                    // Receive media even when no page/Surface is attached.
                    ui { remote?.detachSurface(); sessions.add(session); remote = session }
                    message("已认证连接 ${peer.name}，等待视频")
                } catch (e: Exception) { socket.close(); throw e }
            } catch (e: Exception) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: "对端在启动画面前断开，请检查被控端状态"
                ui { controlError = reason }
                throw e
            } finally { connecting.set(false); ui { connectingPeerId = null } }
        }) { connecting.set(false); connectingPeerId = null }
    }
    fun dismissControlError() { controlError = null }
    fun restartRemoteApp(session: RemoteSession) {
        if(session.app==null || !session.running || session.restarting) return
        session.cancelTouches(); session.restarting=true; session.restartMessage=null
        if(!work {
            try {
                val host=candidates.firstOrNull { it.id==session.peer.id }?.host ?: session.peer.host
                connect(host,session.peer.id).use { socket ->
                    socket.soTimeout=25000
                    val output=DataOutputStream(socket.outputStream); val input=DataInputStream(socket.inputStream)
                    output.writeUTF(Wire.MAGIC); output.writeUTF("RESTART_APP"); output.writeInt(session.sessionId); output.flush()
                    val response=input.readUTF(); check(response=="OK") { response }
                    val result=input.readUTF(); ui { session.restartMessage=result }
                }
            } catch(e:Exception) { ui { session.restartMessage="重启失败：${e.message}" } }
            finally { ui { session.restarting=false } }
        }) { session.restarting=false; session.restartMessage="重启任务未能启动" }
    }
    fun dismissCastApps() { castApps=null }
    fun showCastApps(peer: Peer, host: String = peer.host) = work {
        check(peer.profile.canReceiveCast()) { "对方不支持接收投送" }
        val array=JSONArray(runtime.service?.listApps() ?: error("请先启用本机 Shizuku"))
        val apps=(0 until array.length()).map { i -> array.getJSONObject(i).let { RemoteApp(it.getString("package"),it.getString("name")) } }
        ui { castApps=AppCatalog(peer,host,apps) }
    }
    fun cast(peer: Peer, host: String = peer.host, packageName: String = "") = work {
        check(peer.profile.canReceiveCast()) { "对方不支持接收投送" }
        check(hosting && runtime.service!=null) { "请先开启本机接收并启用 Shizuku" }
        connect(host,peer.id).use { socket ->
            socket.soTimeout=120_000
            val out=DataOutputStream(socket.outputStream); val input=DataInputStream(socket.inputStream)
            out.writeUTF(Wire.MAGIC); out.writeUTF("CAST"); out.writeUTF(packageName); out.flush()
            message("等待 ${peer.name} 接受投送…")
            val response=input.readUTF(); check(response=="OK") { response }
            message("${peer.name} 已接受投送，正在回连本机")
        }
    }
    fun closeCatalog() { catalog = null }
    fun showApps(peer: Peer, host: String = peer.host) {
        if (!peer.profile.canHost()) { message("此设备不提供远程应用"); return }
        if (loadingApps) return
        loadingApps = true
        if (!work {
            try {
                connect(host, peer.id).use { socket ->
                    socket.soTimeout = 30_000
                    val output = DataOutputStream(socket.outputStream); val input = DataInputStream(socket.inputStream)
                    output.writeUTF(Wire.MAGIC); output.writeUTF("APPS"); output.flush()
                    val response = input.readUTF(); check(response == "OK") { response }
                    val array = JSONArray(Wire.readMetadata(input))
                    val apps = (0 until array.length()).map { i -> array.getJSONObject(i).let { RemoteApp(it.getString("package"), it.getString("name")) } }
                    ui { catalog = AppCatalog(peer, host, apps) }
                }
            } catch (e: Exception) { ui { controlError = e.message ?: "无法读取应用列表" }; throw e }
            finally { ui { loadingApps = false } }
        }) loadingApps = false
    }
    fun refreshSessionInfo(session: RemoteSession) {
        if (session.app == null || !session.running || !session.infoFetching.compareAndSet(false, true)) return
        if (!work {
            try {
                val host = candidates.firstOrNull { it.id == session.peer.id }?.host ?: session.peer.host
                connect(host, session.peer.id).use { socket ->
                    val output = DataOutputStream(socket.outputStream); val input = DataInputStream(socket.inputStream)
                    output.writeUTF(Wire.MAGIC); output.writeUTF("SESSION_INFO"); output.writeInt(session.sessionId); output.flush()
                    check(input.readUTF() == "OK") { "会话状态不可用" }
                    val info = Wire.readMetadata(input)
                    ui { session.updateInfo(info) }
                }
            } catch (e: Exception) { ui { session.infoError = e.message ?: "会话状态读取失败" } }
            finally { session.infoFetching.set(false) }
        }) session.infoFetching.set(false)
    }
    fun refreshGeometry(session: RemoteSession) {
        if (!session.deviceMirror || !session.running || !session.geometryFetching.compareAndSet(false, true)) return
        val epoch = session.videoEpoch
        if (!work {
            try {
                val host = candidates.firstOrNull { it.id == session.peer.id }?.host ?: session.peer.host
                connect(host, session.peer.id).use { socket ->
                    socket.soTimeout = 20_000
                    val output = DataOutputStream(socket.outputStream); val input = DataInputStream(socket.inputStream)
                    output.writeUTF(Wire.MAGIC); output.writeUTF("GEOMETRY"); output.flush()
                    val response = input.readUTF(); check(response == "OK") { response }
                    val text = Wire.readMetadata(input)
                    val existing = session.geometry
                    val geometry = if (existing?.raw?.toString() == text) existing else MirrorGeometry(JSONObject(text))
                    ui { session.updateGeometry(geometry, epoch) }
                }
            } catch (e: Exception) { ui { session.geometryError = e.message ?: "屏幕几何读取失败" } }
            finally { session.geometryFetching.set(false) }
        }) session.geometryFetching.set(false)
    }
    // Explicit user exit closes this session. Background/Surface callbacks must only detach the output.
    fun disconnect() { remote?.let { closeConnection(it) }; remote = null; catalog=null }
    fun resume(session: RemoteSession) {
        if(!session.running) return
        remote?.takeIf { it!==session }?.detachSurface()
        catalog=null; remote=session
    }
    fun closeConnection(session: RemoteSession) {
        session.close(); sessions.remove(session)
        if(remote===session) { remote=null; catalog=null }
    }
    private fun mirror(socket: SSLSocket, input: DataInputStream, output: DataOutputStream, peerId: String, app: AppRequest?, deviceMirror: Boolean,media:MediaOptions?=null) {
        val service = runtime.service
        if (service == null) { output.writeUTF("被控端尚未启用 Shizuku"); output.flush(); return }
        val scid = SecureRandom().nextInt(Int.MAX_VALUE)
        activeSessionId = scid
        var channels: RuntimeChannels? = null
        var ready = false
        try {
            val geometry = if (deviceMirror) JSONObject(service.screenGeometry()) else null
            // start() is bounded by the runtime's poll deadline and returns connected FDs.
            val bridge = RuntimeChannels.open(service, scid, app,media)
            channels = bridge
            check(identity.paired(peerId) && !socket.isClosed) { "会话在启动期间已取消或撤销" }
            hostAudio=bridge.audio?.let {HostAudio(peerId,scid,it,bridge::closeAudio)}
            output.writeUTF("OK"); output.flush()
            if(media!=null) Wire.writeMetadata(output,(if(app!=null) JSONObject(service.sessionInfo()) else JSONObject())
                .put("sessionId",scid).put("geometry",geometry).put("audio",media.audio).toString())
            else if (app != null) Wire.writeMetadata(output, JSONObject(service.sessionInfo()).put("sessionId", scid).toString())
            else if (deviceMirror) Wire.writeMetadata(output, JSONObject().put("geometry", geometry).put("sessionId", scid).toString())
            ready = true
            socket.soTimeout = 0
            val drain = Thread({ runCatching { bridge.controlInput.copyTo(object : OutputStream() {
                override fun write(value: Int) {}
                override fun write(bytes: ByteArray, offset: Int, length: Int) {}
            }) } }, "scrcpy-events")
            drain.start()
            val lastTouches = LinkedHashMap<Long, ByteArray>()
            val controlThread = Thread({
                val target = bridge.controlOutput
                try {
                    while (!socket.isClosed) {
                        val packet = Wire.readControl(input, app != null)
                        check(identity.paired(peerId)) { "授权已撤销" }
                        if (packet[0].toInt() == 21) {
                            lastTouches.values.forEach { p -> target.write(p.copyOf().apply { this[1] = 1; this[22] = 0; this[23] = 0 }) }
                            lastTouches.clear()
                        } else if (packet[0].toInt() == 2) {
                            val id = ByteBuffer.wrap(packet, 2, 8).long
                            if (packet[1].toInt() == 1) lastTouches.remove(id) else lastTouches[id] = packet
                        }
                        target.write(packet); target.flush()
                    }
                } catch (_: Exception) {
                    // Release pointers before closing the scrcpy process on network failure.
                } finally {
                    lastTouches.values.forEach { p -> runCatching { target.write(p.copyOf().apply { this[1] = 1; this[22] = 0; this[23] = 0 }) } }
                    runCatching { target.flush() }; runCatching { socket.close() }; bridge.close()
                }
            }, "remote-input").apply { start() }
            try { bridge.video.copyTo(output, 64 * 1024) }
            finally {
                runCatching { socket.close() }
                controlThread.join(500)
                bridge.close()
                controlThread.join(500)
                drain.join(500)
            }
        } catch (e: Exception) {
            if (!ready) runCatching {
                output.writeUTF("被控端启动失败：${(e.message ?: e.javaClass.simpleName).take(500)}")
                output.flush()
            }
            throw e
        } finally {
            hostAudio?.socket?.let {runCatching {it.close()}};hostAudio=null
            channels?.close()
            runCatching { service.stop() }
        }
    }
    private fun advertise() {
        if (registration != null) return
        val r = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { message("自动发现广播失败：$error，可使用 IP") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = r
        nsd.registerService(NsdServiceInfo().apply {
            serviceName = "ConnectorX-${identity.id.take(8)}"; serviceType = "_connectorx._tcp."
            port = Wire.PORT; setAttribute("platform", "android"); setAttribute("pairVersion", "2"); setAttribute("id", identity.id); setAttribute("name", Build.MODEL)
        }, NsdManager.PROTOCOL_DNS_SD, r)
    }
    @Suppress("DEPRECATION")
    fun discover() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        candidates = emptyList()
        val d = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { message("发现失败：$error，可手动输入 IP") }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val id = info.attributes["id"]?.toString(Charsets.UTF_8) ?: return
                        if (id == identity.id || !id.matches(Regex("[0-9a-f]{64}"))) return
                        val host = info.host?.hostAddress ?: return
                        val name = info.attributes["name"]?.toString(Charsets.UTF_8)?.take(80) ?: info.serviceName
                        ui { candidates = candidates.filterNot { it.id == id } + Candidate(name, host, id) }
                    }
                })
            }
        }
        discovery = d
        runCatching { nsd.discoverServices("_connectorx._tcp.", NsdManager.PROTOCOL_DNS_SD, d) }
            .onFailure { message("发现不可用：${it.message}，请使用 IP") }
    }
}
