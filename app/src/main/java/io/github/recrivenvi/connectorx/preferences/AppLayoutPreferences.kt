package io.github.recrivenvi.connectorx.preferences

import android.content.Context

/** Local controller preference, shared by every peer for the same package name. */
internal data class AppLayoutPreferences(val underStatus: Boolean = false, val underNavigation: Boolean = false,
                                         val systemDecorations: Boolean = false) {
    fun save(context: Context, packageName: String) {
        context.getSharedPreferences("remote_app_layout", Context.MODE_PRIVATE).edit()
            .putBoolean("$packageName.status", underStatus)
            .putBoolean("$packageName.navigation", underNavigation)
            .putBoolean("$packageName.decorations", systemDecorations).apply()
    }
    companion object {
        fun load(context: Context, packageName: String): AppLayoutPreferences {
            val store=context.getSharedPreferences("remote_app_layout", Context.MODE_PRIVATE)
            return AppLayoutPreferences(store.getBoolean("$packageName.status", false), store.getBoolean("$packageName.navigation",false),
                store.getBoolean("$packageName.decorations",false))
        }
    }
}
