package io.github.recrivenvi.connectorx

import android.app.*
import android.content.Intent
import android.os.IBinder

class HostService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("connection", "局域网连接", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "connection").setSmallIcon(R.drawable.ic_connector)
            .setContentTitle("ConnectorX 连接服务运行中")
            .setContentText("已有会话保持连接；点击管理接收")
            .setContentIntent(open).setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        (application as ConnectorApp).engine.startHost()
        return START_NOT_STICKY
    }
    override fun onDestroy() { (application as ConnectorApp).engine.stopHost(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
