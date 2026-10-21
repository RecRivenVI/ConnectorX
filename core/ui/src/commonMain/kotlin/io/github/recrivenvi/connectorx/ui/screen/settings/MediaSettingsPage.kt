package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.TaskPage
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*

data class MediaSettingsModel(val values: Map<String, String>, val defaults: Map<String, String>, val save: (Map<String, String>) -> String?)

@Composable
fun MediaSettingsPage(model: MediaSettingsModel, onBack: () -> Unit) {
    var draft by remember(model.values) { mutableStateOf(model.values) }
    var videoRate by remember(model.values) { mutableStateOf(displayRate(model.values["videoBitRate"].orEmpty(), 6)) }
    var audioRate by remember(model.values) { mutableStateOf(displayRate(model.values["audioBitRate"].orEmpty(), 3)) }
    var advanced by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(Res.string.ui_media_saved)
    val restored = stringResource(Res.string.ui_media_restored)
    val invalidRate = stringResource(Res.string.ui_media_invalid_rate)
    fun set(key: String, value: String) { draft = draft + (key to value); message = null }
    @Composable fun field(key: String, title: String) {
        TextField(draft[key].orEmpty(), { set(key, it) }, label = title, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
    @Composable fun choice(key: String, title: String, options: List<Pair<String, String>>, summary: String? = null) {
        val choices = if (options.any { it.first == draft[key] }) options else options + (draft[key].orEmpty() to draft[key].orEmpty())
        val selected = choices.indexOfFirst { it.first == draft[key] }.coerceAtLeast(0)
        OverlayDropdownPreference(title = title, summary = summary, items = choices.map { it.second },
            selectedIndex = selected, onSelectedIndexChange = { set(key, choices[it].first)
                if (key == "audioSource" && choices[it].first != "playback") set("audioDup", "false") })
    }
    @Composable fun plainChoice(key: String, title: String, values: List<String>) = choice(key, title, values.map { it to it.uppercase() })
    TaskPage(stringResource(Res.string.ui_media_title), onBack = onBack) {
        item { BasicComponent(title = stringResource(Res.string.ui_media_scope)) }
        item { SmallTitle(stringResource(Res.string.ui_video)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            choice("videoMaxSize", stringResource(Res.string.ui_resolution), listOf(
                "-1" to stringResource(Res.string.ui_resolution_auto), "0" to stringResource(Res.string.ui_resolution_native),
                "1280" to "1280 px", "1600" to "1600 px", "1920" to "1920 px", "2560" to "2560 px"))
            field("videoMaxSize", stringResource(Res.string.ui_resolution_custom))
            field("maxFps", stringResource(Res.string.ui_fps))
            TextField(videoRate, { videoRate = it; message = null }, label = stringResource(Res.string.ui_video_rate), modifier = Modifier.fillMaxWidth().padding(12.dp))
        } }
        item { SmallTitle(stringResource(Res.string.ui_sound)) }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SwitchPreference(title = stringResource(Res.string.ui_sound_enabled), checked = draft["audio"] == "true", onCheckedChange = { set("audio", it.toString()) })
            if (draft["audio"] == "true") {
                choice("audioSource", stringResource(Res.string.ui_sound_source), SoundSource.entries.map { it.value to stringResource(soundSourceLabel(it)) })
                SwitchPreference(title = stringResource(Res.string.ui_sound_duplicate), summary = stringResource(Res.string.ui_sound_duplicate_reason),
                    checked = draft["audioDup"] == "true", enabled = draft["audioSource"] == "playback", onCheckedChange = { set("audioDup", it.toString()) })
                BasicComponent(title = stringResource(Res.string.ui_sound_policy))
            }
        } }
        item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            ArrowPreference(title = stringResource(Res.string.ui_advanced), summary = stringResource(if (advanced) Res.string.ui_collapse else Res.string.ui_expand), onClick = { advanced = !advanced })
        } }
        if (advanced) {
            item { SmallTitle(stringResource(Res.string.ui_video_encoding)) }
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                plainChoice("videoCodec", stringResource(Res.string.ui_encoding), listOf("h264", "h265", "av1", "vp8", "vp9"))
                BasicComponent(title = stringResource(Res.string.ui_encoding_support))
                field("videoEncoder", stringResource(Res.string.ui_video_encoder))
                field("videoCodecOptions", stringResource(Res.string.ui_video_options))
                choice("minSizeAlignment", stringResource(Res.string.ui_alignment), listOf(1, 2, 4, 8, 16).map { it.toString() to "$it px" })
                field("crop", stringResource(Res.string.ui_crop))
                choice("captureOrientation", stringResource(Res.string.ui_orientation), listOf(
                    "" to stringResource(Res.string.ui_orientation_follow), "0" to "0°", "90" to "90°", "180" to "180°", "270" to "270°",
                    "flip0" to stringResource(Res.string.ui_orientation_flip), "flip90" to stringResource(Res.string.ui_orientation_flip_angle, 90),
                    "flip180" to stringResource(Res.string.ui_orientation_flip_angle, 180), "flip270" to stringResource(Res.string.ui_orientation_flip_angle, 270),
                    "@" to stringResource(Res.string.ui_orientation_lock), "@0" to stringResource(Res.string.ui_orientation_lock_angle, 0),
                    "@90" to stringResource(Res.string.ui_orientation_lock_angle, 90), "@180" to stringResource(Res.string.ui_orientation_lock_angle, 180),
                    "@270" to stringResource(Res.string.ui_orientation_lock_angle, 270), "@flip0" to stringResource(Res.string.ui_orientation_lock_flip),
                    "@flip90" to stringResource(Res.string.ui_orientation_lock_flip_angle, 90), "@flip180" to stringResource(Res.string.ui_orientation_lock_flip_angle, 180),
                    "@flip270" to stringResource(Res.string.ui_orientation_lock_flip_angle, 270)))
                field("angle", stringResource(Res.string.ui_angle))
                SwitchPreference(title = stringResource(Res.string.ui_downsize), checked = draft["downsizeOnError"] == "true", onCheckedChange = { set("downsizeOnError", it.toString()) })
                SwitchPreference(title = stringResource(Res.string.ui_ignore_constraints), summary = stringResource(Res.string.ui_ignore_constraints_reason),
                    checked = draft["ignoreVideoEncoderConstraints"] == "true", onCheckedChange = { set("ignoreVideoEncoderConstraints", it.toString()) })
            } }
            item { SmallTitle(stringResource(Res.string.ui_audio_encoding)) }
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                plainChoice("audioCodec", stringResource(Res.string.ui_audio_format), listOf("opus", "aac", "flac", "raw"))
                TextField(audioRate, { audioRate = it; message = null }, label = stringResource(Res.string.ui_audio_rate), modifier = Modifier.fillMaxWidth().padding(12.dp))
                BasicComponent(title = stringResource(Res.string.ui_audio_rate_reason))
                field("audioEncoder", stringResource(Res.string.ui_audio_encoder))
                field("audioCodecOptions", stringResource(Res.string.ui_audio_options))
            } }
            item { SmallTitle(stringResource(Res.string.ui_buffers)) }
            item { Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                field("videoBufferMs", stringResource(Res.string.ui_video_buffer))
                field("audioBufferMs", stringResource(Res.string.ui_audio_buffer))
                field("audioOutputBufferMs", stringResource(Res.string.ui_output_buffer))
            } }
        }
        item { TextButton(stringResource(Res.string.ui_save), {
            val video = storedRate(videoRate, 6); val audio = storedRate(audioRate, 3)
            message = if (video == null || audio == null) invalidRate else model.save(draft + mapOf("videoBitRate" to video, "audioBitRate" to audio))
                ?.replace("Codec", "编码")?.replace("playback", "应用播放的声音") ?: saved
        }, Modifier.fillMaxWidth().padding(12.dp), colors = ButtonDefaults.textButtonColorsPrimary()) }
        item { TextButton(stringResource(Res.string.ui_restore), {
            draft = model.defaults; videoRate = displayRate(draft["videoBitRate"].orEmpty(), 6); audioRate = displayRate(draft["audioBitRate"].orEmpty(), 3)
            message = model.save(model.defaults) ?: restored
        }, Modifier.fillMaxWidth().padding(horizontal = 12.dp)) }
        message?.let { item { BasicComponent(title = it) } }
    }
}
