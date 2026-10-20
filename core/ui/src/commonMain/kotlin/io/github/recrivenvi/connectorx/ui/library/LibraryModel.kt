package io.github.recrivenvi.connectorx.ui.library

import io.github.recrivenvi.connectorx.ui.devices.DeviceCard

data class AppSource(val id:String,val name:String,val summary:String,val select:()->Unit)
data class LibraryApplication(
    val key:String,val name:String,val packageName:String,val deviceName:String,val favorite:Boolean,
    val enabled:Boolean,val launch:()->Unit,val toggleFavorite:()->Unit,val settings:()->Unit,
    val pin:(()->Unit)?=null,
)
data class LibraryModel(
    val sources:List<AppSource>,val selectedSource:String,val request:Int,val loading:Boolean,
    val loaded:Boolean,val status:String,val refresh:()->Unit,val enablePrivilege:(()->Unit)?,
    val applications:List<LibraryApplication>,val favorites:List<LibraryApplication>,val recent:List<LibraryApplication>,
    val sessions:List<DeviceCard>,
)
data class AutomationTarget(val packageName:String,val name:String,val select:()->Unit)
data class GlobalScrollModel(
    val permissionGranted:Boolean,val visible:Boolean,val direction:Int,val status:String,
    val open:()->Unit,val stop:()->Unit,val close:()->Unit,
)
data class AutomationModel(
    val targetName:String?,val targets:List<AutomationTarget>,val loading:Boolean,val refresh:()->Unit,
    val start:(Int)->Unit,val enabled:Boolean,val status:String,val activeTitle:String?,val direction:Int,
    val resume:()->Unit,val stop:()->Unit,val global:GlobalScrollModel?=null,
)
