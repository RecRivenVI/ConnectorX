package io.github.recrivenvi.connectorx.ui.devices

import android.os.Build
import io.github.recrivenvi.connectorx.connection.*
import io.github.recrivenvi.connectorx.ui.library.*

fun androidLibrary(engine:ConnectorEngine,launch:(Peer,String,RemoteApp,Boolean)->Unit,
                   settings:(RemoteApp)->Unit,pin:(Peer,RemoteApp)->Unit):LibraryModel {
    val local=engine.localPeer()
    val peers=listOf(local)+engine.peers
    val saved=engine.appLibrary.entries
    fun item(peer:Peer,app:RemoteApp):LibraryApplication {
        val host=engine.candidates.firstOrNull {it.id==peer.id}?.host?:peer.host
        val localControl=peer.id==local.id
        return LibraryApplication("${peer.id}/${app.packageName}",app.name,app.packageName,if(localControl)"此设备 · ${Build.MODEL}" else peer.name,
            saved.any {it.deviceId==peer.id&&it.packageName==app.packageName&&it.favorite},engine.connectingPeerId==null,
            {launch(peer,host,app,localControl)},{engine.appLibrary.favorite(peer.id,app.packageName,app.name)},
            {settings(app)},if(localControl)null else {{pin(peer,app)}})
    }
    val selected=engine.libraryDeviceId.takeIf {id->peers.any {it.id==id}}?:local.id
    val catalog=engine.appCatalogs[selected]
    val remembered=saved.mapNotNull {entry->peers.firstOrNull {it.id==entry.deviceId}?.let {item(it,RemoteApp(entry.packageName,entry.name))}}
    return LibraryModel(peers.map {peer->AppSource(peer.id,if(peer.id==local.id)"此设备 · ${peer.name}" else peer.name,
        if(peer.id==local.id)"本机独立应用" else if(engine.sessions.any {it.running&&it.peer.id==peer.id})"已有会话" else "已配对",{engine.selectLibraryDevice(peer.id)})},
        selected,engine.libraryRequest,engine.loadingApps,catalog!=null,engine.status,engine::refreshLibrary,
        if(selected==local.id&&engine.runtime.service==null){{engine.runtime.enable()}}else null,
        catalog?.apps?.map {item(catalog.peer,it)}?:emptyList(),remembered.filter {it.favorite},remembered.filterNot {it.favorite}.take(4),
        androidDevices(engine,{}).sessions)
}

fun androidAutomation(engine:ConnectorEngine,global:GlobalScrollModel?=null,start:(RemoteApp,Int)->Unit):AutomationModel {
    val apps=engine.appCatalogs[engine.identity.id]?.apps.orEmpty()
    val selected=apps.firstOrNull {it.packageName==engine.appLibrary.automationPackage}
    val active=engine.localSession?.takeIf {it.running}
    return AutomationModel(selected?.name,apps.map {app->AutomationTarget(app.packageName,app.name){engine.appLibrary.automation(app.packageName)}},
        engine.loadingApps,{engine.showLocalApps(navigate=false)},
        {direction->selected?.let {start(it,direction)}},selected!=null&&engine.runtime.service!=null&&engine.connectingPeerId==null,
        if(engine.runtime.service==null)"请先在设备页连接 Shizuku。" else if(apps.isEmpty())"先读取本机应用列表。" else "",
        active?.appTitle,active?.scrollDirection?:0,{active?.let(engine::resumeLocal)},{active?.let {if(it.scrollDirection!=0)it.toggleScroll(it.scrollDirection)}},global)
}
