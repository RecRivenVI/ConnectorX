package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.runtime.Composable
import io.github.recrivenvi.connectorx.session.RemoteSession
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun StreamQualitySummary(session:RemoteSession) {
    Text("质量 ${session.qualityStatus} · 接收 ${"%.1f".format(session.receivedFps)} / 解码输出 ${"%.1f".format(session.renderedFps)} fps · ${"%.2f".format(session.videoMbps)} Mbps")
}

@Composable
internal fun StreamQualityDetails(session:RemoteSession) {
    StreamQualitySummary(session)
    Text("视频 ${session.width}×${session.height} · ${session.media.videoCodec.uppercase()} · 目标 ${session.media.maxFps} fps / ${session.media.videoBitRate} bps")
    Text("关键帧 ${"%.2f".format(session.keyframeFps)} fps · 平均帧 ${"%.1f".format(session.averageFrameKiB)} KiB · 解码积压 ${session.decoderBacklogFrames} 帧")
    Text("相对视频积压 ${"%.1f".format(session.relativeVideoDelayMs)} ms · 本秒解码输入等待 ${"%.1f".format(session.decoderInputWaitMs)} ms · 配置缓冲 ${session.media.videoBufferMs} ms")
    Text("解码器 ${session.videoDecoderName} · ${when(session.videoDecoderHardware){true->"硬件";false->"软件";null->"未知"}}")
    if(session.media.audio) {
        Text("音频 ${session.audioFormat} · ${"%.1f".format(session.audioKbps)} kbps · ${"%.1f".format(session.audioPacketsPerSecond)} 包/s · underrun ${session.audioUnderruns}")
        Text("音频缓冲 ${session.media.audioBufferMs} ms · 输出缓冲 ${session.media.audioOutputBufferMs} ms · ${session.audioStatus}")
    } else Text("音频已关闭")
    Text("解码输出统计提交给 Surface 的帧，不等同于 SurfaceFlinger 实际上屏。吞吐仅统计媒体 payload；相对积压不包含首帧固定延迟，也不是触摸到显示的端到端延迟。")
}
