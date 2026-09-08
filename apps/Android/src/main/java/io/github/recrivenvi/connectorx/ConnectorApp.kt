package io.github.recrivenvi.connectorx

import io.github.recrivenvi.connectorx.connection.ConnectorEngine

import android.app.Application

class ConnectorApp : Application() {
    lateinit var engine: ConnectorEngine
    override fun onCreate() { super.onCreate()
        val storage=io.github.recrivenvi.connectorx.platform.PlatformStorage(this)
        io.github.recrivenvi.connectorx.platform.LocalBackSettings.apply(applicationInfo,
            storage.getString(io.github.recrivenvi.connectorx.platform.StorageKeys.PREDICTIVE_BACK,"false")=="true")
        engine = ConnectorEngine(this) }
}
