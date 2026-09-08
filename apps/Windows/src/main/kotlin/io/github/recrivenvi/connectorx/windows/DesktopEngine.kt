package io.github.recrivenvi.connectorx.windows

import androidx.compose.runtime.*
import io.github.recrivenvi.connectorx.protocol.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.*
import javax.net.ssl.*
import javax.swing.SwingUtilities

data class Nearby(val id:String,val name:String,val host:String)
data class DesktopApp(val packageName:String,val name:String)
data class Approval(val title:String,val detail:String,val result:CompletableFuture<Boolean>)

class DesktopEngine : AutoCloseable {
    val identity=DesktopIdentity()
    private val mediaStorage=WindowsAppearanceStorage(identity.directory,"media.properties")
    var mediaOptions by mutableStateOf(runCatching {MediaOptions.decode(mediaStorage.getString("config",""))}.getOrDefault(MediaOptions.defaults()));private set
    fun saveMedia(values:Map<String,String>):String?=try {
        val options=MediaOptions.fromValues(values);mediaStorage.putString("config",options.encode());mediaOptions=options;null
    } catch(e:Exception) {e.message ?: "参数无效"}
    private val shortcuts=DesktopShortcuts(identity.directory)
    val appOptions=AppOptionsStore(identity.directory)
    var loadingApps by mutableStateOf(false); private set
    var connectingPeerId by mutableStateOf<String?>(null); private set
    var controlError by mutableStateOf<String?>(null); private set
    val name=System.getenv("COMPUTERNAME") ?: "ConnectorX Windows"
    var status by mutableStateOf("正在启动局域网接收…"); private set
    var pairing by mutableStateOf(false); private set
    var hosting by mutableStateOf(false); private set
    var peers by mutableStateOf(identity.peers()); private set
    var nearby by mutableStateOf(listOf<Nearby>()); private set
    var approval by mutableStateOf<Approval?>(null); private set
    var apps by mutableStateOf<Pair<DesktopPeer,List<DesktopApp>>?>(null); private set
    val sessions=mutableStateListOf<DesktopSession>()
    private val executor=ThreadPoolExecutor(0,12,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>())
    private val connecting=ConcurrentHashMap.newKeySet<String>()
    private val sockets=ConcurrentHashMap<SSLSocket,String>()
    private val asking=AtomicBoolean(false)
    private val starting=AtomicBoolean(false)
    private val dns=CopyOnWriteArrayList<JmDNS>()
    @Volatile private var pairUntil=0L
    @Volatile private var closed=false
    @Volatile private var listener:SSLServerSocket?=null
    fun ui(block:()->Unit) { SwingUtilities.invokeLater(block) }
    fun report(text:String) { println(text); ui { status=text } }
    fun work(block:()->Unit):Boolean {
        try { executor.execute { try { block() } catch(e:Exception) { report(e.message ?: e.javaClass.simpleName);ui {controlError=e.message ?: "操作失败"} } };return true }
        catch(_:RejectedExecutionException) { report("操作较多，请稍后重试");return false }
    }
    private fun pairOpen()=System.nanoTime()<pairUntil
    fun dismissError() {controlError=null}
    fun enablePairing() {
        if(!hosting) {report("请先启动局域网接收");return}
        pairUntil=System.nanoTime()+TimeUnit.MINUTES.toNanos(2); pairing=true
        report("配对入口已开放 2 分钟，请核对双方校验码")
        javax.swing.Timer(120100) { if(!pairOpen()) pairing=false }.apply { isRepeats=false; start() }
    }
    private fun confirm(title:String,detail:String):Boolean {
        check(asking.compareAndSet(false,true)) { "请先处理当前确认请求" }
        val a=Approval(title,detail,CompletableFuture())
        ui { approval=a }
        try { return a.result.get(110,TimeUnit.SECONDS) }
        finally { asking.set(false); ui { if(approval===a) approval=null } }
    }
    private fun pairConfirm(name:String,id:String)=confirm("与 $name 配对", "请确认两端显示相同校验码：\n"+
        Wire.comparison(identity.id,id).uppercase().chunked(4).joinToString(" "))
    private fun refresh() { val p=identity.peers(); ui { peers=p } }
    @Synchronized fun start() {
        if(listener!=null||!starting.compareAndSet(false,true)) return
        if(!work {try {
            val s=identity.tls { identity.paired(it)||pairOpen() }.serverSocketFactory.createServerSocket() as SSLServerSocket
            s.reuseAddress=true; s.needClientAuth=true; s.enabledProtocols=arrayOf("TLSv1.3")
            try {s.bind(InetSocketAddress(Wire.PORT))} catch(e:Exception) {s.close();throw e}
            if(closed) {s.close();return@work};listener=s
            ui { hosting=true }
            report("Windows 控制端已就绪 · 端口 ${Wire.PORT}")
            Thread({ while(!s.isClosed) {
                try { val socket=s.accept() as SSLSocket
                    sockets[socket]=""
                    try { executor.execute { serve(socket) } }
                    catch(_:RejectedExecutionException) { sockets.remove(socket); socket.close() }
                } catch(_:IOException) { break }
            } },"windows-listener").apply { isDaemon=true; start() }
            dns.forEach {mdns->runCatching {mdns.registerService(serviceInfo())}}
            discover()
        } finally {starting.set(false)}}) starting.set(false)
    }
    private fun serviceInfo()=ServiceInfo.create("_connectorx._tcp.local.","ConnectorX-${identity.id.take(8)}",Wire.PORT,0,0,
        mapOf("id" to identity.id,"name" to name,"platform" to "windows","pairVersion" to "2"))
    fun discover() = work {
        if(dns.isNotEmpty()) { dns.forEach { it.list("_connectorx._tcp.local.",2000) }; return@work }
        val addresses=NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp&&!it.isLoopback&&!it.isVirtual }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
        for(address in addresses) runCatching {
            val mdns=JmDNS.create(address); dns.add(mdns)
            if(hosting) mdns.registerService(serviceInfo())
            mdns.addServiceListener("_connectorx._tcp.local.",object:ServiceListener {
                override fun serviceAdded(e:ServiceEvent) { mdns.requestServiceInfo(e.type,e.name) }
                override fun serviceRemoved(e:ServiceEvent) {
                    val id=e.info.getPropertyString("id") ?: return
                    ui { nearby=nearby.filterNot { it.id==id } }
                }
                override fun serviceResolved(e:ServiceEvent) {
                    val id=e.info.getPropertyString("id") ?: return
                    if(id==identity.id||!id.matches(Regex("[0-9a-f]{64}"))) return
                    val host=e.info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                    val n=e.info.getPropertyString("name")?.take(80) ?: e.name
                    ui { nearby=nearby.filterNot { it.id==id }+Nearby(id,n,host) }
                }
            })
        }.onFailure { report("自动发现 ${address.hostAddress} 不可用，可使用手动 IP：${it.message}") }
    }
    private fun serve(socket:SSLSocket) {
        try { socket.use {
            socket.soTimeout=15000; socket.tcpNoDelay=true; socket.startHandshake()
            val id=identity.remoteId(socket); sockets[socket]=id
            val input=DataInputStream(socket.inputStream); val out=DataOutputStream(socket.outputStream)
            check(input.readUTF()==Wire.MAGIC) { "协议版本不兼容" }
            when(val op=input.readUTF()) {
                "PAIR", "PAIR2" -> {
                    check(pairOpen()) { "配对入口未开放" }
                    val n=input.readUTF().take(80)
                    val profile=if(op=="PAIR2") DeviceProfile.read(input) else DeviceProfile.ANDROID
                    check(id!=identity.id)
                    out.writeUTF(name); if(op=="PAIR2") DeviceProfile.WINDOWS.write(out); out.flush()
                    socket.soTimeout=120000
                    val accepted=pairConfirm(n,id)
                    val remoteAccepted=input.readBoolean()
                    if(!accepted||!remoteAccepted||!pairOpen()) { out.writeBoolean(false); out.flush(); return }
                    identity.save(DesktopPeer(id,n,socket.inetAddress.hostAddress,profile))
                    out.writeBoolean(true); out.flush(); refresh(); pairUntil=0; ui { pairing=false }; report("已与 $n 持久配对")
                }
                "PROFILE" -> {
                    check(identity.paired(id)); out.writeUTF("OK"); out.writeUTF(name); DeviceProfile.WINDOWS.write(out); out.flush()
                }
                "CAST" -> {
                    val peer=identity.peers().firstOrNull { it.id==id } ?: error("未授权设备")
                    check(peer.profile.canHost()) { "来源不支持 Android 控制服务" }
                    val pkg=input.readUTF()
                    if(pkg.isNotEmpty()) AppRequest(pkg,1080,1920,320,60f)
                    socket.soTimeout=120000
                    if(!confirm("接收 ${peer.name} 的投送",if(pkg.isEmpty()) "镜像手机屏幕并允许此电脑操作" else "运行远端应用：$pkg")) {
                        out.writeUTF("Windows 已拒绝投送"); out.flush(); return
                    }
                    check(identity.paired(id)) { "配对已撤销" }
                    val existing=sessions.firstOrNull {it.peer.id==id&&it.running}
                    if(existing!=null&&(existing.app?.packageName!=pkg.takeIf {it.isNotEmpty()}||existing.deviceMirror!=pkg.isEmpty())) {
                        out.writeUTF("此手机已有其他会话，请先退出原会话再投送；原连接已保留");out.flush();return
                    }
                    val p=peer.copy(host=socket.inetAddress.hostAddress)
                    out.writeUTF("OK"); out.flush()
                    control(p,pkg.takeIf { it.isNotEmpty() },deviceMirror=pkg.isEmpty(),explicitHost=p.host)
                }
                else -> { check(identity.paired(id)); out.writeUTF("Windows 仅作为控制方，不支持捕获或控制此电脑"); out.flush() }
            }
        } } catch(e:Exception) { if(!closed) report("接收失败：${e.message}") }
        finally { sockets.remove(socket); runCatching { socket.close() } }
    }
    fun connect(peer:DesktopPeer):SSLSocket=connect(peer.host,peer.id)
    private fun connect(host:String,expected:String?):SSLSocket {
        require(host.isNotBlank()&&host.length<=253&&!host.contains('/')) { "请输入 IP 或主机名，不含端口" }
        val socket=identity.tls { expected==null||(it==expected&&identity.paired(it)) }.socketFactory.createSocket() as SSLSocket
        try {
            socket.enabledProtocols=arrayOf("TLSv1.3"); socket.soTimeout=15000; socket.tcpNoDelay=true
            socket.connect(InetSocketAddress(host.trim(),Wire.PORT),8000); socket.startHandshake()
            if(expected!=null) sockets[socket]=expected
            return socket
        } catch(e:Exception) { socket.close(); throw e }
    }
    fun pair(host:String)=work {
        connect(host,null).use { socket ->
            val out=DataOutputStream(socket.outputStream); val input=DataInputStream(socket.inputStream)
            out.writeUTF(Wire.MAGIC); out.writeUTF("PAIR2"); out.writeUTF(name); DeviceProfile.WINDOWS.write(out); out.flush()
            val n=input.readUTF().take(80); val profile=DeviceProfile.read(input); val id=identity.remoteId(socket)
            check(id!=identity.id); socket.soTimeout=120000
            val accepted=pairConfirm(n,id); out.writeBoolean(accepted); out.flush()
            check(input.readBoolean()&&accepted) { "配对已取消" }
            identity.save(DesktopPeer(id,n,host,profile)); refresh(); report("已与 $n 持久配对")
        }
    }
    fun host(peer:DesktopPeer)=nearby.firstOrNull { it.id==peer.id }?.host ?: peer.host
    private fun <T> request(peer:DesktopPeer,operation:String,write:(DataOutputStream)->Unit={},read:(DataInputStream)->T):T {
        val socket=connect(peer)
        try {socket.use {
            socket.soTimeout=30000
            val out=DataOutputStream(socket.outputStream);val input=DataInputStream(socket.inputStream)
            out.writeUTF(Wire.MAGIC);out.writeUTF(operation);write(out);out.flush()
            val response=input.readUTF();check(response=="OK") {response};return read(input)
        }} finally {sockets.remove(socket)}
    }
    fun showApps(peer:DesktopPeer) {
        if(loadingApps) return
        loadingApps=true
        if(!work {try {
            check(peer.profile.canHost());val p=peer.copy(host=host(peer))
            val a=JSONArray(request(p,"APPS",read={Wire.readMetadata(it)}))
            val list=(0 until a.length()).map {a.getJSONObject(it).let {o->DesktopApp(o.getString("package"),o.getString("name"))}}
            ui {apps=p to list}
        } finally {ui {loadingApps=false}}}) loadingApps=false
    }
    fun pin(peer:DesktopPeer,app:DesktopApp)=work {val path=shortcuts.create(peer,app);report("已创建快捷方式：${path.fileName}")}
    fun openShortcut(id:String)=work {
        val (peerId,pkg)=shortcuts.resolve(id)
        val peer=identity.peers().firstOrNull {it.id==peerId} ?: error("设备已解除配对，请重新配对后启动")
        control(peer,pkg)
    }
    fun dismissApps() {apps=null}
    fun refreshInfo(session:DesktopSession) {
        if(!session.running||!session.infoFetching.compareAndSet(false,true)) return
        if(!work {try {
            val peer=session.peer.copy(host=host(session.peer))
            if(session.deviceMirror) {
                val geometry=DesktopGeometry(JSONObject(request(peer,"GEOMETRY",read={Wire.readMetadata(it)})))
                ui {if(geometry.matches(session.width,session.height)) {session.panel.geometry=geometry;session.panel.repaint()}}
            } else if(session.app!=null) {
                val info=JSONObject(request(peer,"SESSION_INFO",write={it.writeInt(session.sessionId)},read={Wire.readMetadata(it)}))
                ui {session.info=info}
            }
        } catch(e:Exception) {ui {session.error="状态读取：${e.message}"}}
        finally {session.infoFetching.set(false)}}) session.infoFetching.set(false)
    }
    fun restartApp(session:DesktopSession) {
        if(!session.running||session.app==null||session.restarting) return
        session.panel.cancelInputs();session.restarting=true;session.restartMessage=null
        if(!work {try {
            val result=request(session.peer.copy(host=host(session.peer)),"RESTART_APP",write={it.writeInt(session.sessionId)},read={it.readUTF()})
            ui {session.restartMessage=result};refreshInfo(session)
        } catch(e:Exception) {ui {session.restartMessage="重启失败：${e.message}"}}
        finally {ui {session.restarting=false}}}) session.restarting=false
    }
    fun saveOptions(session:DesktopSession,options:AppOptions) {
        val pkg=session.app?.packageName ?: return
        runCatching {
            appOptions.save(pkg,options);session.options=options
            if(options.followWindow) session.panel.syncWindowSize()
        }.onFailure {session.error=it.message}
    }
    fun stopReceiving() {
        pairUntil=0;pairing=false;approval?.result?.complete(false)
        runCatching {listener?.close()};listener=null;hosting=false
        dns.forEach {runCatching {it.unregisterAllServices()}}
        report("停止接收新配对/投送；现有连接保留")
    }
    fun control(peer:DesktopPeer,pkg:String?=null,deviceMirror:Boolean=false,explicitHost:String?=null) {
        if(!peer.profile.canHost()) {report("Windows 不可被控制");return}
        if(!connecting.add(peer.id)) return
        ui {connectingPeerId=peer.id}
        ui {
            val existing=sessions.firstOrNull {it.peer.id==peer.id&&it.running}
            if(existing!=null) {
                existing.raise()
                report(if(existing.app?.packageName==pkg&&existing.deviceMirror==deviceMirror) "已恢复现有会话" else "请先退出该设备已有会话再切换模式；原连接未关闭")
                connecting.remove(peer.id);connectingPeerId=null
            } else if(!work {
                try {
                    val p=peer.copy(host=explicitHost ?: host(peer));val options=pkg?.let {appOptions.load(it)} ?: AppOptions()
                    val app=pkg?.let {options.request(it)}
                    val capabilities=runCatching {JSONObject(request(p,"CAPABILITIES",read={Wire.readMetadata(it)}))}.getOrDefault(JSONObject())
                    check(capabilities.optInt("mediaOptions")>=1) {"请升级被控 Android 的 ConnectorX，以使用视频和音频参数"}
                    val selectedMedia=mediaOptions
                    val socket=connect(p)
                    try {
                        socket.soTimeout=40000
                        val out=DataOutputStream(socket.outputStream);val input=DataInputStream(socket.inputStream)
                        out.writeUTF(Wire.MAGIC);out.writeUTF("MEDIA");out.writeUTF(if(app!=null) {if(app.systemDecorations) "APP_OPTIONS" else "APP"} else if(deviceMirror) "DEVICE" else "MIRROR")
                        selectedMedia.write(out)
                        if(app?.systemDecorations==true) app.writeOptions(out) else app?.write(out);out.flush()
                        val response=input.readUTF();check(response=="OK") {response}
                        val info=Wire.readMetadata(input)
                        val session=DesktopSession(p,app,socket,input,out,::report,info,deviceMirror,capabilities,options,selectedMedia,
                            changed=::refreshInfo,ended={sockets.remove(it)})
                        identity.save(p);refresh();ui {sessions.removeAll {!it.running};sessions.add(session);session.open()}
                        if(selectedMedia.audio) work {
                            try {
                                val audioSocket=connect(p)
                                try {
                                    val audioOut=DataOutputStream(audioSocket.outputStream);val audioIn=DataInputStream(audioSocket.inputStream)
                                    audioOut.writeUTF(Wire.MAGIC);audioOut.writeUTF("AUDIO");audioOut.writeInt(session.sessionId);audioOut.flush()
                                    val result=audioIn.readUTF();check(result=="OK") {result};audioSocket.soTimeout=0
                                    session.attachAudio(audioSocket,audioIn)
                                } catch(e:Exception) {audioSocket.close();throw e}
                                finally {sockets.remove(audioSocket)}
                            } catch(e:Exception) {session.audioMessage("音频连接失败：${e.message}；视频保持")}
                        }
                    } catch(e:Exception) {sockets.remove(socket);socket.close();throw e}
                } finally {connecting.remove(peer.id);ui {connectingPeerId=null}}
            }) {connecting.remove(peer.id);connectingPeerId=null}
        }
    }
    fun forget(peer:DesktopPeer) {
        work {
            identity.remove(peer.id)
            sockets.entries.filter { it.value==peer.id }.forEach { runCatching { it.key.close() }; sockets.remove(it.key) }
            ui { sessions.filter { it.peer.id==peer.id }.forEach { it.close() } }; refresh()
        }
    }
    override fun close() {
        closed=true; approval?.result?.complete(false); runCatching { listener?.close() }
        sessions.forEach { it.close() }; sockets.keys.forEach { runCatching { it.close() } }
        dns.forEach { runCatching { it.close() } }; executor.shutdownNow()
    }
}
