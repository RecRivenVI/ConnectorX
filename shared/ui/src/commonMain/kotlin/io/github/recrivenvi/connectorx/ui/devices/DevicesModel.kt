package io.github.recrivenvi.connectorx.ui.devices

/** UI-only state and platform actions. No certificates, sockets, Android services or decoder ownership. */
data class DeviceAction(val key:String,val title:String,val enabled:Boolean=true,val invoke:()->Unit)
data class DeviceCard(val id:String,val name:String,val summary:String,val actions:List<DeviceAction>)
data class NearbyDevice(val id:String,val name:String,val host:String,val pair:()->Unit)
data class DevicesModel(
    val name:String,val identityId:String,val status:String,val receiving:Boolean,
    val toggleReceiving:(()->Unit)?,val privilege:String?,val enablePrivilege:(()->Unit)?,
    val pairing:Boolean,val enablePairing:()->Unit,val refresh:()->Unit,val pair:(String)->Unit,
    val peers:List<DeviceCard>,val nearby:List<NearbyDevice>,val sessions:List<DeviceCard>,
    val manualConnections:(String)->List<DeviceAction> = { emptyList() },
)
