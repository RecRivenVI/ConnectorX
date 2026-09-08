package io.github.recrivenvi.connectorx.runtimeclient

import io.github.recrivenvi.connectorx.BuildConfig
import io.github.recrivenvi.connectorx.IRuntime
import io.github.recrivenvi.connectorx.PrivilegedRuntime

import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

class RuntimeConnection(private val context: Context, private val status: (String) -> Unit) {
    @Volatile var service: IRuntime? = null
        private set
    private var binding = false
    private var autoEnabled = false
    private var permissionPending = false
    private val args = Shizuku.UserServiceArgs(ComponentName(context, PrivilegedRuntime::class.java))
        .daemon(false).processNameSuffix("runtime").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            service = IRuntime.Stub.asInterface(binder)
            status(runCatching { "权限已就绪 · ${service?.identity()}" }.getOrElse { "权限连接失败：${it.message}" })
        }
        override fun onServiceDisconnected(name: ComponentName?) { binding = false; service = null; status("特权服务已断开，请重新启用") }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        permissionPending = false
        if (result == PackageManager.PERMISSION_GRANTED) bind() else status("未获 Shizuku 授权")
    }
    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderDeadListener { service = null; binding = false; permissionPending = false; status("Shizuku 已停止；配对关系保留") }
        Shizuku.addBinderReceivedListenerSticky { if (autoEnabled) enable() }
    }
    fun enable() {
        autoEnabled = true
        if (service?.asBinder()?.isBinderAlive == true || binding || permissionPending) return
        if (!Shizuku.pingBinder()) { status("请先在 Shizuku 中启动服务，再返回重试"); return }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
        else { permissionPending = true; runCatching { Shizuku.requestPermission(1) }
            .onFailure { permissionPending = false; status("Shizuku 授权请求失败：${it.message}") } }
    }
    private fun bind() {
        if (binding || service?.asBinder()?.isBinderAlive == true) return
        binding = true
        runCatching { Shizuku.bindUserService(args, connection); status("正在连接 Shizuku…") }
            .onFailure { binding = false; status("Shizuku 连接失败：${it.message}") }
    }
}
