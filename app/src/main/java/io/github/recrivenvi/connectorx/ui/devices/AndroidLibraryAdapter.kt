package io.github.recrivenvi.connectorx.ui.devices

import android.os.Build
import android.content.Context
import io.github.recrivenvi.connectorx.preferences.AppLayoutPreferences
import io.github.recrivenvi.connectorx.connection.*
import io.github.recrivenvi.connectorx.ui.library.*

fun androidLibrary(engine:ConnectorEngine,context:Context,openPrivilege:()->Unit,launch:(Peer,String,RemoteApp,Boolean)->Unit,
                   pin:(Peer,RemoteApp)->Unit):LibraryModel {
    val local=engine.localPeer()
    val peers=(listOf(local)+engine.peers).distinctBy { it.id }
    val saved=engine.appLibrary.entries
    fun item(peer:Peer,app:RemoteApp):LibraryApplication {
        val host=engine.candidates.firstOrNull {it.id==peer.id}?.host?:peer.host
        val localControl=peer.id==local.id
        val layout=AppLayoutPreferences.load(context,app.packageName)
        return LibraryApplication("${peer.id}/${app.packageName}",app.name,app.packageName,if(localControl)"此设备 · ${Build.MODEL}" else peer.name,
            saved.any {it.deviceId==peer.id&&it.packageName==app.packageName&&it.favorite},engine.connectingPeerId==null,
            {launch(peer,host,app,localControl)},{engine.appLibrary.favorite(peer.id,app.packageName,app.name)},
            layout.underStatus,layout.underNavigation,{status,navigation->layout.copy(underStatus=status,underNavigation=navigation).save(context,app.packageName)},
            if(localControl)null else {{pin(peer,app)}})
    }
    val selected=engine.libraryDeviceId.takeIf {id->peers.any {it.id==id}}?:local.id
    val catalog=engine.appCatalogs[selected]
    val remembered=saved.mapNotNull {entry->peers.firstOrNull {it.id==entry.deviceId}?.let {item(it,RemoteApp(entry.packageName,entry.name))}}
    return LibraryModel(peers.map {peer->AppSource(peer.id,peer.name,peer.id==local.id,
        if(peer.id==local.id) DevicePresence.Nearby else devicePresence(engine.sessions.any {it.running&&it.peer.id==peer.id},engine.candidates.any {it.id==peer.id}),
        {engine.selectLibraryDevice(peer.id)})},
        selected,engine.libraryRequest,engine.loadingApps,catalog!=null,engine.appCatalogErrors.containsKey(selected),engine::refreshLibrary,
        if(selected==local.id&&engine.runtime.service==null)openPrivilege else null,
        catalog?.apps?.map {item(catalog.peer,it)}?:emptyList(),remembered.filter {it.favorite&&it.key.startsWith("$selected/")},
        androidDevices(engine,{}).sessions)
}

fun androidAutomation(engine:ConnectorEngine,openPrivilege:()->Unit,global:GlobalScrollModel?=null,start:(RemoteApp,Int)->Unit):AutomationModel {
    val apps=engine.appCatalogs[engine.identity.id]?.apps.orEmpty()
    val selected=apps.firstOrNull {it.packageName==engine.appLibrary.automationPackage}
    val active=engine.localSession?.takeIf {it.running}
    return AutomationModel(selected?.name,apps.map {app->AutomationTarget(app.packageName,app.name){engine.appLibrary.automation(app.packageName)}},
        engine.loadingApps,{engine.showLocalApps(navigate=false)},
        {direction->selected?.let {start(it,direction)}},selected!=null&&engine.runtime.service!=null&&engine.connectingPeerId==null,
        engine.appCatalogErrors.containsKey(engine.identity.id),
        active?.appTitle,active?.scrollDirection?:0,{active?.let(engine::resumeLocal)},{active?.let {if(it.scrollDirection!=0)it.toggleScroll(it.scrollDirection)}},engine.runtime.service!=null,openPrivilege,global)
}
