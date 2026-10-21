package io.github.recrivenvi.connectorx.ui.screen.settings

import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.StringResource

/** Decimal UI units only. Stored MediaOptions values remain integer bits per second. */
fun displayRate(bits: String, decimals: Int): String {
    val number = bits.toLongOrNull() ?: return bits
    val scale = if (decimals == 6) 1_000_000L else 1_000L
    val fraction = (number % scale).toString().padStart(decimals, '0').trimEnd('0')
    return (number / scale).toString() + if (fraction.isEmpty()) "" else ".$fraction"
}

fun storedRate(input: String, decimals: Int): String? {
    val match = Regex("([+-]?)([0-9]+)(?:\\.([0-9]*))?").matchEntire(input.trim()) ?: return null
    val fraction = match.groupValues[3]
    if (fraction.drop(decimals).any { it != '0' }) return null
    val digits = (match.groupValues[2] + fraction.take(decimals).padEnd(decimals, '0')).trimStart('0').ifEmpty { "0" }
    return (match.groupValues[1] + digits).toLongOrNull()?.toString()
}

enum class SoundSource(val value: String) {
    System("output"), Playback("playback"), Microphone("mic"),
    Unprocessed("mic-unprocessed"), Camera("mic-camcorder"), Recognition("mic-voice-recognition"),
    Communication("mic-voice-communication"), Call("voice-call"),
    CallUplink("voice-call-uplink"), CallDownlink("voice-call-downlink"), Performance("voice-performance");

    companion object {
        fun fromValue(value: String): SoundSource? = entries.firstOrNull { it.value == value }
    }
}

fun soundSourceLabel(source: SoundSource): StringResource = when (source) {
    SoundSource.System -> Res.string.ui_sound_system
    SoundSource.Playback -> Res.string.ui_sound_playback
    SoundSource.Microphone -> Res.string.ui_sound_mic
    SoundSource.Unprocessed -> Res.string.ui_sound_unprocessed
    SoundSource.Camera -> Res.string.ui_sound_camera
    SoundSource.Recognition -> Res.string.ui_sound_recognition
    SoundSource.Communication -> Res.string.ui_sound_communication
    SoundSource.Call -> Res.string.ui_sound_call
    SoundSource.CallUplink -> Res.string.ui_sound_uplink
    SoundSource.CallDownlink -> Res.string.ui_sound_downlink
    SoundSource.Performance -> Res.string.ui_sound_performance
}
