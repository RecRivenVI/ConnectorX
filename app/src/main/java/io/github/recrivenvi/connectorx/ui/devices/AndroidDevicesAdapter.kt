package io.github.recrivenvi.connectorx.ui.devices

import android.os.Build
import io.github.recrivenvi.connectorx.connection.ConnectorEngine

/** Unique JVM facade; platform ownership remains in ConnectorEngine. */
fun androidDevices(
    engine: ConnectorEngine,
    toggleHost: () -> Unit,
    address: String = "",
    openPrivilege: () -> Unit = { engine.runtime.enable() }
) = DevicesModel(
    name = Build.MODEL, receiving = engine.hosting,
    toggleReceiving = toggleHost, enablePrivilege = { engine.runtime.enable() },
    pairing = engine.pairing, enablePairing = engine::enablePairing, refresh = engine::discover, pair = engine::pair,
    peers = engine.peers.map { peer ->
        val host = engine.candidates.firstOrNull { it.id == peer.id }?.host ?: peer.host
        DeviceCard(peer.id, peer.name, "", listOf(
            DeviceAction("apps", "打开应用", !engine.loadingApps) { engine.showApps(peer, host) },
            DeviceAction("mirror", "设备镜像", engine.connectingPeerId == null) { engine.control(peer, host, deviceMirror = true) },
            DeviceAction("remove", "解除配对") { engine.forget(peer) }
        ), devicePresence(engine.sessions.any { it.running && it.peer.id == peer.id }, engine.candidates.any { it.id == peer.id }), host)
    },
    nearby = engine.candidates.filter { c -> engine.peers.none { it.id == c.id } }
        .map { c -> NearbyDevice(c.id, c.name, c.host) { engine.pair(c.host) } },
    sessions = engine.sessions.filter { it.running }.map { s ->
        DeviceCard(s.peer.id, s.peer.name, s.appTitle ?: "", listOf(
            DeviceAction("resume", "回到会话") { engine.resume(s) },
            DeviceAction("close", "退出会话") { engine.closeConnection(s) }
        ), DevicePresence.Session, s.peer.host, sessionType = when {
            s.app != null -> SessionType.RemoteApplication
            s.deviceMirror -> SessionType.DeviceMirror
            else -> SessionType.ScreenControl
        })
    } + listOfNotNull(engine.localSession?.takeIf { it.running }?.let { s ->
        DeviceCard("local-app", Build.MODEL, s.appTitle, listOf(
            DeviceAction("resume", "回到会话") { engine.resumeLocal(s) },
            DeviceAction("close", "退出会话") { engine.resumeLocal(s); engine.disconnect() }
        ), DevicePresence.Session, sessionType = SessionType.LocalApplication)
    }),
    manualConnections = { addressInput -> engine.peers.map { peer ->
        DeviceAction(peer.id, peer.name, engine.connectingPeerId == null) { engine.control(peer, addressInput) }
    } },
    permission = permissionPresentation(engine.runtime.service != null, engine.privilege),
    address = address, openPrivilege = openPrivilege,
    pairingAttempt = engine.pairingAttempt, pairingFailed = engine.pairingFailed,
    discovering = engine.discovering, discoveryFailed = engine.discoveryFailed
)
