package io.github.recrivenvi.connectorx.runtimeclient

import io.github.recrivenvi.connectorx.IRuntime

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.github.recrivenvi.connectorx.protocol.AppRequest
import io.github.recrivenvi.connectorx.protocol.MediaOptions
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Owns descriptors received over the Shizuku Binder. No cross-domain connect() in the APK. */
class RuntimeChannels private constructor(private val descriptors: Array<ParcelFileDescriptor>) : Closeable {
    val video = ParcelFileDescriptor.AutoCloseInputStream(descriptors[0])
    val controlOutput = ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1])
    val controlInput = ParcelFileDescriptor.AutoCloseInputStream(descriptors[2])
    val audio=descriptors.getOrNull(3)?.let {ParcelFileDescriptor.AutoCloseInputStream(it)}
    private val closed = AtomicBoolean(false)
    fun closeAudio() {
        descriptors.getOrNull(3)?.let {runCatching {Os.shutdown(it.fileDescriptor,OsConstants.SHUT_RDWR)}}
        runCatching {audio?.close()}
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // shutdown affects all duplicates and unblocks reads; closing one duplicate alone does not.
        descriptors.forEach { runCatching { Os.shutdown(it.fileDescriptor, OsConstants.SHUT_RDWR) } }
        runCatching { audio?.close() };runCatching { video.close() }; runCatching { controlOutput.close() }; runCatching { controlInput.close() }
    }
    companion object {
        fun open(service: IRuntime, sessionId: Int, app: AppRequest? = null, media:MediaOptions?=null): RuntimeChannels {
            val received = if(media!=null) service.startConfigured(sessionId,app?.packageName ?: "",app?.width ?: 0,app?.height ?: 0,app?.dpi ?: 0,app?.refreshRate ?: 60f,app?.systemDecorations ?: false,media.encode())
                else if (app == null) service.start(sessionId)
                else if (app.systemDecorations) service.startAppOptions(sessionId, app.packageName, app.width, app.height, app.dpi, app.refreshRate,true)
                else service.startApp(sessionId, app.packageName, app.width, app.height, app.dpi, app.refreshRate)
            try {
                check(received != null && received.size == if(media?.audio==true) 3 else 2) { "runtime 返回的通道不完整" }
                val control=received.last()
                return RuntimeChannels((listOf(received[0],control,ParcelFileDescriptor.dup(control.fileDescriptor))+if(media?.audio==true) listOf(received[1]) else emptyList()).toTypedArray())
            } catch (e: Exception) {
                received?.forEach { runCatching { it.close() } }
                throw e
            }
        }
    }
}
