package io.github.recrivenvi.connectorx.windows

import io.github.recrivenvi.connectorx.platform.PlatformStorage
import io.github.recrivenvi.connectorx.ui.devices.*
import java.nio.file.*
import java.util.Properties

class WindowsAppearanceStorage(private val directory:Path,fileName:String="appearance.properties"):PlatformStorage {
    private val path=directory.resolve(fileName)
    private val values=Properties().apply { if(Files.exists(path)) Files.newInputStream(path).use { load(it) } }
    @Synchronized override fun getString(key:String,default:String):String=values.getProperty(key,default)
    @Synchronized override fun putString(key:String,value:String) {
        values.setProperty(key,value); Files.createDirectories(directory)
        val temp=Files.createTempFile(directory,"appearance-",".tmp")
        try {
            Files.newOutputStream(temp).use { values.store(it,"ConnectorX appearance") }
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}

fun windowsDevices(engine:DesktopEngine)=DevicesModel(
    name=engine.name,identityId=engine.identity.id,status=engine.status,receiving=engine.hosting,
    toggleReceiving={if(engine.hosting) engine.stopReceiving() else engine.start()},privilege=null,enablePrivilege=null,pairing=engine.pairing,
    enablePairing=engine::enablePairing,refresh={engine.discover()},pair={engine.pair(it)},
    peers=engine.peers.map { peer -> DeviceCard(peer.id,peer.name,
        "${if(peer.profile.canHost()) "Android" else "Windows · 仅控制方"} · ${engine.host(peer)}",
        (if(peer.profile.canHost()) listOf(
            DeviceAction("control","远程控制",engine.connectingPeerId==null) {engine.control(peer)},
            DeviceAction("mirror","设备镜像",engine.connectingPeerId==null) {engine.control(peer,deviceMirror=true)},
            DeviceAction("apps",if(engine.loadingApps) "正在读取应用…" else "远程应用",!engine.loadingApps) {engine.showApps(peer)}
        ) else emptyList())+DeviceAction("remove","解除配对") {engine.forget(peer)}) },
    nearby=engine.nearby.filter { n->engine.peers.none {it.id==n.id} }.map { n->NearbyDevice(n.id,n.name,n.host) {engine.pair(n.host)} },
    sessions=engine.sessions.filter {it.running}.map { s->DeviceCard(s.peer.id,s.peer.name,s.app?.packageName ?: "设备镜像",listOf(
        DeviceAction("resume","进入画面") {s.raise()},DeviceAction("close","断开连接") {s.close()}
    )) },
    manualConnections={address->engine.peers.filter {it.profile.canHost()}.map {peer->
        DeviceAction(peer.id,"通过此 IP 连接 ${peer.name}") {engine.control(peer,explicitHost=address)}
    }}
)
