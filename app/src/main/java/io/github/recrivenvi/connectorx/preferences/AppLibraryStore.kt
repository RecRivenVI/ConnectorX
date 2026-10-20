package io.github.recrivenvi.connectorx.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class SavedApplication(val deviceId:String,val packageName:String,val name:String,val favorite:Boolean=false,val lastUsed:Long=0) {
    val key get()="$deviceId/$packageName"
}

/** Device identity, never its transient LAN address, owns an application shortcut. */
class AppLibraryStore(context:Context) {
    private val prefs=context.getSharedPreferences("application_library",Context.MODE_PRIVATE)
    var entries by mutableStateOf(read());private set
    var automationPackage by mutableStateOf(prefs.getString("scroll_package",null));private set
    private fun read():List<SavedApplication> = runCatching {
        val array=JSONArray(prefs.getString("entries","[]"))
        (0 until array.length()).mapNotNull { i -> runCatching {array.getJSONObject(i).let {
            SavedApplication(it.getString("device"),it.getString("package"),it.getString("name"),it.optBoolean("favorite"),it.optLong("used"))
        }}.getOrNull() }.distinctBy {it.key}
    }.getOrDefault(emptyList())
    private fun update(entry:SavedApplication) {
        val next=(entries.filterNot {it.key==entry.key}+entry).sortedByDescending {it.lastUsed}
            .let {list->list.filter {it.favorite}+list.filterNot {it.favorite}.take(40)}
        val array=JSONArray()
        next.forEach {array.put(JSONObject().put("device",it.deviceId).put("package",it.packageName).put("name",it.name)
            .put("favorite",it.favorite).put("used",it.lastUsed))}
        prefs.edit().putString("entries",array.toString()).apply();entries=next
    }
    fun used(deviceId:String,packageName:String,name:String) {
        val old=entries.firstOrNull {it.deviceId==deviceId&&it.packageName==packageName}
        update(SavedApplication(deviceId,packageName,name,old?.favorite?:false,System.currentTimeMillis()))
    }
    fun favorite(deviceId:String,packageName:String,name:String) {
        val old=entries.firstOrNull {it.deviceId==deviceId&&it.packageName==packageName}
        update(SavedApplication(deviceId,packageName,name,old?.favorite!=true,old?.lastUsed?:0))
    }
    fun automation(packageName:String) {prefs.edit().putString("scroll_package",packageName).apply();automationPackage=packageName}
}
