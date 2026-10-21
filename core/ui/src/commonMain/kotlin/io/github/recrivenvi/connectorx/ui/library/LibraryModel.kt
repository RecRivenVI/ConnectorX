package io.github.recrivenvi.connectorx.ui.library

import io.github.recrivenvi.connectorx.ui.devices.DeviceCard
import io.github.recrivenvi.connectorx.ui.devices.DevicePresence

data class AppSource(val id:String,val name:String,val local:Boolean,val presence:DevicePresence,val select:()->Unit)
data class LibraryApplication(
    val key:String,val name:String,val packageName:String,val deviceName:String,val favorite:Boolean,
    val enabled:Boolean,val launch:()->Unit,val toggleFavorite:()->Unit,
    val underStatus:Boolean,val underNavigation:Boolean,val setLayout:(Boolean,Boolean)->Unit,
    val pin:(()->Unit)?=null,
)
data class LibraryModel(
    val sources:List<AppSource>,val selectedSource:String,val request:Int,val loading:Boolean,
    val loaded:Boolean,val readFailed:Boolean,val refresh:()->Unit,val enablePrivilege:(()->Unit)?,
    val applications:List<LibraryApplication>,val favorites:List<LibraryApplication>,
    val sessions:List<DeviceCard>,
)
data class AutomationTarget(val packageName:String,val name:String,val select:()->Unit)
data class GlobalScrollModel(
    val permissionGranted:Boolean,val visible:Boolean,val direction:Int,val ready:Boolean,val failed:Boolean,
    val open:()->Unit,val stop:()->Unit,val close:()->Unit,
)
data class AutomationModel(
    val targetName:String?,val targets:List<AutomationTarget>,val loading:Boolean,val refresh:()->Unit,
    val start:(Int)->Unit,val enabled:Boolean,val readFailed:Boolean,val activeTitle:String?,val direction:Int,
    val resume:()->Unit,val stop:()->Unit,val permissionAvailable:Boolean,val openPrivilege:()->Unit,val global:GlobalScrollModel?=null,
)
