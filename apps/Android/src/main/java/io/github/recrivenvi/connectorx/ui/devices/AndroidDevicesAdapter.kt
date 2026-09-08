package io.github.recrivenvi.connectorx.ui.devices

import android.os.Build
import io.github.recrivenvi.connectorx.connection.ConnectorEngine

/** Unique JVM facade: this file must not share the common UI's DevicesPageKt name. */
fun androidDevices(engine:ConnectorEngine,toggleHost:()->Unit)=DevicesModel(
    name=Build.MODEL,identityId=engine.identity.id,status=engine.status,receiving=engine.hosting,
    toggleReceiving=toggleHost,privilege=engine.privilege,enablePrivilege={engine.runtime.enable()},
    pairing=engine.pairing,enablePairing=engine::enablePairing,refresh=engine::discover,pair=engine::pair,
    peers=engine.peers.map { peer ->
        val host=engine.candidates.firstOrNull { it.id==peer.id }?.host ?: peer.host
        DeviceCard(peer.id,peer.name,"${if(peer.profile.platform()=="windows") "Windows · 仅控制方" else "Android"} · $host",
            (if(peer.profile.canHost()) listOf(
                DeviceAction("remote","远程控制",engine.connectingPeerId==null) {engine.control(peer,host)},
                DeviceAction("apps",if(engine.loadingApps) "正在读取应用…" else "远程应用",!engine.loadingApps) {engine.showApps(peer,host)},
                DeviceAction("mirror","设备镜像",engine.connectingPeerId==null) {engine.control(peer,host,deviceMirror=true)}
            ) else listOf(
                DeviceAction("cast","镜像到此电脑") {engine.cast(peer,host)},
                DeviceAction("cast-app","投送本机应用") {engine.showCastApps(peer,host)}
            ))+DeviceAction("remove","解除配对") {engine.forget(peer)})
    },
    nearby=engine.candidates.filter { c->engine.peers.none {it.id==c.id} }.map { c->NearbyDevice(c.id,c.name,c.host) {engine.pair(c.host)} },
    sessions=engine.sessions.filter {it.running}.map { s->DeviceCard(s.peer.id,s.peer.name,s.appTitle ?: s.app?.packageName ?: "设备镜像",listOf(
        DeviceAction("resume","进入画面") {engine.resume(s)},DeviceAction("close","断开连接") {engine.closeConnection(s)}
    )) },
    manualConnections={address->engine.peers.filter {it.profile.canHost()}.map {peer->
        DeviceAction(peer.id,"通过此 IP 连接 ${peer.name}",engine.connectingPeerId==null) {engine.control(peer,address)}
    }}
)
