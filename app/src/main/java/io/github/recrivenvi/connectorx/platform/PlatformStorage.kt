package io.github.recrivenvi.connectorx.platform
import android.content.Context
fun PlatformStorage(context:Context):PlatformStorage = AndroidPlatformStorage(context)
private class AndroidPlatformStorage(context:Context):PlatformStorage {
    private val preferences=context.getSharedPreferences("connectorx_appearance",Context.MODE_PRIVATE)
    override fun getString(key:String,default:String)=preferences.getString(key,default) ?: default
    override fun putString(key:String,value:String) { preferences.edit().putString(key,value).apply() }
}
