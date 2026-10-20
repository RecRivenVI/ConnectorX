package io.github.recrivenvi.connectorx.ui.devices

import android.os.Build
import io.github.recrivenvi.connectorx.connection.ConnectorEngine

/** Unique JVM facade: this file must not share the common UI's DevicesPageKt name. */
fun androidDevices(engine:ConnectorEngine,toggleHost:()->Unit)=DevicesModel(
    name=Build.MODEL,identityId=engine.identity.id,status=engine.status,receiving=engine.hosting,
    toggleReceiving=toggleHost,privilege=engine.privilege,enablePrivilege={engine.runtime.enable()},
    pairing=engine.pairing,enablePairing=engine::enablePairing,refresh=engine::discover,pair=engine::pair,
    localActions=listOf(DeviceAction("local-apps",if(engine.loadingApps) "正在读取本机应用…" else "本机应用控制",!engine.loadingApps) {engine.showLocalApps()}),
    peers=engine.peers.map { peer ->
        val host=engine.candidates.firstOrNull { it.id==peer.id }?.host ?: peer.host
        DeviceCard(peer.id,peer.name,"Android · $host",
            listOf(
                DeviceAction("remote","远程控制",engine.connectingPeerId==null) {engine.control(peer,host)},
                DeviceAction("apps",if(engine.loadingApps) "正在读取应用…" else "远程应用",!engine.loadingApps) {engine.showApps(peer,host)},
                DeviceAction("mirror","设备镜像",engine.connectingPeerId==null) {engine.control(peer,host,deviceMirror=true)}
            )+DeviceAction("remove","解除配对") {engine.forget(peer)})
    },
    nearby=engine.candidates.filter { c->engine.peers.none {it.id==c.id} }.map { c->NearbyDevice(c.id,c.name,c.host) {engine.pair(c.host)} },
    sessions=engine.sessions.filter {it.running}.map { s->DeviceCard(s.peer.id,s.peer.name,s.appTitle ?: s.app?.packageName ?: "设备镜像",listOf(
        DeviceAction("resume","进入画面") {engine.resume(s)},DeviceAction("close","断开连接") {engine.closeConnection(s)}
    )) }+listOfNotNull(engine.localSession?.takeIf {it.running}?.let {s->DeviceCard("local-app",Build.MODEL,s.appTitle,listOf(
        DeviceAction("resume","进入画面") {engine.resumeLocal(s)},DeviceAction("close","退出应用") {engine.resumeLocal(s);engine.disconnect()}
    ))}),
    manualConnections={address->engine.peers.map {peer->
        DeviceAction(peer.id,"通过此 IP 连接 ${peer.name}",engine.connectingPeerId==null) {engine.control(peer,address)}
    }}
)
