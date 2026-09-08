package io.github.recrivenvi.connectorx.shortcuts

import io.github.recrivenvi.connectorx.MainActivity
import io.github.recrivenvi.connectorx.R
import io.github.recrivenvi.connectorx.connection.Peer
import io.github.recrivenvi.connectorx.connection.RemoteApp

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import org.json.JSONObject
import java.util.UUID

data class RemoteShortcut(val peerId: String, val packageName: String, val name: String)

internal object RemoteShortcuts {
    const val ACTION = "io.github.recrivenvi.connectorx.OPEN_REMOTE_APP"
    private const val EXTRA = "shortcut"
    fun request(activity: Activity, peer: Peer, app: RemoteApp, report: (String) -> Unit) {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) { report("当前桌面不支持固定快捷方式"); return }
        val prefs = activity.getSharedPreferences("shortcuts", Activity.MODE_PRIVATE)
        val token = UUID.randomUUID().toString()
        val record = JSONObject().put("peer", peer.id).put("package", app.packageName).put("name", app.name)
        check(prefs.edit().putString(token, record.toString()).commit())
        val intent = Intent(activity, MainActivity::class.java).setAction(ACTION).putExtra(EXTRA, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfo.Builder(activity, "remote-$token")
            .setShortLabel("${peer.name} · ${app.name}".take(40))
            .setLongLabel("${peer.name} · ${app.name}")
            .setIcon(Icon.createWithResource(activity, R.drawable.ic_connector)).setIntent(intent).build()
        if (manager.requestPinShortcut(shortcut, null)) report("已向桌面请求添加，请在系统提示中确认")
        else { prefs.edit().remove(token).apply(); report("桌面未接受快捷方式请求") }
    }
    fun resolve(activity: Activity, intent: Intent): RemoteShortcut? {
        if (intent.action != ACTION) return null
        val token = intent.getStringExtra(EXTRA) ?: return null
        // Only locally-created launcher records can select a target. Ignore arbitrary external host/package extras.
        val json = activity.getSharedPreferences("shortcuts", Activity.MODE_PRIVATE).getString(token, null) ?: return null
        val record = JSONObject(json)
        return RemoteShortcut(record.getString("peer"), record.getString("package"), record.getString("name"))
    }
}
