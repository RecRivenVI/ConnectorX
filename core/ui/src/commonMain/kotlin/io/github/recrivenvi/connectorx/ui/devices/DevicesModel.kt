package io.github.recrivenvi.connectorx.ui.devices

/** UI-only state and platform actions. No certificates, sockets, Android services or decoder ownership. */
data class DeviceAction(val key:String,val title:String,val enabled:Boolean=true,val invoke:()->Unit)
data class DeviceCard(val id:String,val name:String,val summary:String,val actions:List<DeviceAction>,
    val presence:DevicePresence=DevicePresence.Saved,val address:String="",val identity:String=id,
    val sessionType:SessionType=SessionType.DeviceMirror)
enum class SessionType { LocalApplication, RemoteApplication, DeviceMirror, ScreenControl }
data class NearbyDevice(val id:String,val name:String,val host:String,val pair:()->Unit)
data class DevicesModel(
    val name:String,val receiving:Boolean,
    val toggleReceiving:()->Unit,val enablePrivilege:()->Unit,
    val pairing:Boolean,val enablePairing:()->Unit,val refresh:()->Unit,val pair:(String)->Unit,
    val peers:List<DeviceCard>,val nearby:List<NearbyDevice>,val sessions:List<DeviceCard>,
    val manualConnections:(String)->List<DeviceAction> = { emptyList() },
    val permission:PermissionPresentation=permissionPresentation(false,""),
    val address:String="",val openPrivilege:()->Unit=enablePrivilege,
    val pairingAttempt:Boolean=false,val pairingFailed:Boolean=false,
    val discovering:Boolean=false,val discoveryFailed:Boolean=false,
)
