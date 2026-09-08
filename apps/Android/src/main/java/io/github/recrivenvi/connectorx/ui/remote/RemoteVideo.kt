package io.github.recrivenvi.connectorx.ui.remote

import io.github.recrivenvi.connectorx.input.RemoteTouchListener
import io.github.recrivenvi.connectorx.session.RemoteSession

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun RemoteVideo(session: RemoteSession, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = if (session.width > 0 && session.height > 0) session.width.toFloat() / session.height
                    else session.app?.let { it.width.toFloat() / it.height } ?: 0.46f
        val width = minOf(maxWidth, maxHeight * ratio)
        AndroidView(modifier = Modifier.width(width).height(width / ratio), factory = { context ->
            object : SurfaceView(context) {
                override fun performClick(): Boolean { super.performClick(); return true }
            }.apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { session.start(holder.surface) }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) { session.detachSurface(holder.surface) }
                })
                setOnTouchListener(RemoteTouchListener(session))
            }
        })
        if (session.width == 0) Text("等待远程画面…", color = Color.White)
    }
}
