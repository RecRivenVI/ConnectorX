package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.runtime.Composable
import io.github.recrivenvi.connectorx.ui.screen.settings.LocalSessionDiagnostics
import io.github.recrivenvi.connectorx.ui.screen.settings.displayRate
import io.github.recrivenvi.connectorx.session.RemoteSession
import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import java.util.Locale

private fun decimal(value: Number) = String.format(Locale.ROOT, "%.2f", value.toDouble())

@Composable
internal fun StreamQualitySummary(session: RemoteSession) {
    if (!LocalSessionDiagnostics.current) return
    Text(stringResource(Res.string.ui_quality_summary, session.qualityStatus, decimal(session.receivedFps), decimal(session.renderedFps), decimal(session.videoMbps)))
}

@Composable
internal fun StreamQualityDetails(session: RemoteSession) {
    if (!LocalSessionDiagnostics.current) return
    StreamQualitySummary(session)
    Text(stringResource(Res.string.ui_quality_video, session.width, session.height, session.media.videoCodec.uppercase(), session.media.maxFps.toString(), displayRate(session.media.videoBitRate.toString(), 6)))
    Text(stringResource(Res.string.ui_quality_frames, decimal(session.keyframeFps), decimal(session.averageFrameKiB), session.decoderBacklogFrames))
    Text(stringResource(Res.string.ui_quality_delay, decimal(session.relativeVideoDelayMs), decimal(session.decoderInputWaitMs), session.media.videoBufferMs))
    val hardware = stringResource(when (session.videoDecoderHardware) { true -> Res.string.ui_hardware; false -> Res.string.ui_software; null -> Res.string.ui_unknown })
    Text(stringResource(Res.string.ui_quality_decoder, session.videoDecoderName, hardware))
    if (session.media.audio) {
        Text(stringResource(Res.string.ui_quality_audio, session.audioFormat, decimal(session.audioKbps), decimal(session.audioPacketsPerSecond), session.audioUnderruns.toString()))
        Text(stringResource(Res.string.ui_quality_audio_buffer, session.media.audioBufferMs, session.media.audioOutputBufferMs, session.audioStatus))
    } else Text(stringResource(Res.string.ui_audio_disabled))
    Text(stringResource(Res.string.ui_quality_definitions))
}
