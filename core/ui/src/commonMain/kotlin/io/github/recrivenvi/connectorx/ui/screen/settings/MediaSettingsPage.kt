package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.ui.component.AdaptiveTopAppBar
import io.github.recrivenvi.connectorx.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

data class MediaSettingsModel(val values:Map<String,String>,val defaults:Map<String,String>,val save:(Map<String,String>)->String?)

@Composable fun MediaSettingsPage(model:MediaSettingsModel,onBack:()->Unit) {
    var draft by remember(model.values) {mutableStateOf(model.values)}
    var message by remember {mutableStateOf<String?>(null)}
    fun set(key:String,value:String) {draft=draft+(key to value);message=null}
    @Composable fun field(key:String,title:String) {TextField(draft[key].orEmpty(),{set(key,it)},label=title,modifier=Modifier.fillMaxWidth().padding(12.dp))}
    @Composable fun choice(key:String,title:String,options:List<String>) {
        OverlayDropdownPreference(title=title,summary=draft[key],items=options,selectedIndex=options.indexOf(draft[key]).coerceAtLeast(0),onSelectedIndexChange={
            set(key,options[it]);if(key=="audioSource"&&options[it]!="playback") set("audioDup","false")
        })
    }
    @Composable fun mappedChoice(key:String,title:String,options:List<Pair<String,String>>) {
        val selected=options.indexOfFirst {it.first==draft[key]}.coerceAtLeast(0)
        OverlayDropdownPreference(title=title,summary=options[selected].second,items=options.map {it.second},selectedIndex=selected,
            onSelectedIndexChange={set(key,options[it].first)})
    }
    Scaffold(topBar={AdaptiveTopAppBar("远控画面与音频",navigationIcon={IconButton(onBack) {Icon(MiuixIcons.Back,"返回")}})}) {padding->WideContentBox {side->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=side+12.dp,end=side+12.dp,top=padding.calculateTopPadding(),bottom=24.dp)) {
            item {Text("控制端默认参数，应用于下次新建镜像或独立应用会话。保存不会断开已有连接。",Modifier.padding(12.dp))}
            item {SmallTitle("视频")}
            item {Card {Column {
                mappedChoice("videoMaxSize","视频最大边",listOf(
                    "-1" to "自动（设备镜像 1600，独立应用原始尺寸）",
                    "1280" to "1280 px（流畅）","1600" to "1600 px（均衡）","1920" to "1920 px（清晰）",
                    "2560" to "2560 px（超清）","0" to "原始尺寸（不限）"))
                field("videoMaxSize","自定义视频最大边（-1 自动、0 不限、128–8192）")
                field("maxFps","帧率上限 fps（支持小数；0 不限制，最高 240）")
                field("videoBitRate","视频码率 bps（默认 8000000）")
                choice("videoCodec","视频编码",listOf("h264","h265","av1","vp8","vp9"))
                field("videoEncoder","指定视频编码器（留空自动）")
                field("videoCodecOptions","视频 Codec 参数（可选，例如 i-frame-interval=1）")
                mappedChoice("minSizeAlignment","编码尺寸对齐",listOf("1" to "1 px（scrcpy 默认）","2" to "2 px","4" to "4 px","8" to "8 px","16" to "16 px"))
                field("crop","画面裁切（留空；或 width:height:x:y）")
                mappedChoice("captureOrientation","捕获方向",listOf(
                    "" to "跟随显示方向","0" to "0°","90" to "90°","180" to "180°","270" to "270°",
                    "flip0" to "水平翻转","flip90" to "水平翻转 + 90°","flip180" to "水平翻转 + 180°","flip270" to "水平翻转 + 270°",
                    "@" to "锁定当前方向","@0" to "锁定 0°","@90" to "锁定 90°","@180" to "锁定 180°","@270" to "锁定 270°",
                    "@flip0" to "锁定水平翻转","@flip90" to "锁定翻转 + 90°","@flip180" to "锁定翻转 + 180°","@flip270" to "锁定翻转 + 270°"))
                field("angle","附加旋转角度（-360–360，默认 0）")
                SwitchPreference(title="编码失败时自动降低尺寸",summary="scrcpy downsize-on-error；关闭后直接报告编码失败",
                    checked=draft["downsizeOnError"]=="true",onCheckedChange={set("downsizeOnError",it.toString())})
                SwitchPreference(title="忽略编码器尺寸约束",summary="高级兼容选项；可能导致编码器启动失败",
                    checked=draft["ignoreVideoEncoderConstraints"]=="true",onCheckedChange={set("ignoreVideoEncoderConstraints",it.toString())})
                field("videoBufferMs","本机视频缓冲 ms（0–2000；默认 0 低延迟）")
            }}}
            item {SmallTitle("音频")}
            item {Card {Column {
                SwitchPreference(title="传输音频",checked=draft["audio"]=="true",onCheckedChange={set("audio",it.toString())})
                if(draft["audio"]=="true") {
                    choice("audioCodec","音频编码",listOf("opus","aac","flac","raw"))
                    field("audioBitRate","音频码率 bps（默认 128000；FLAC/RAW 不使用）")
                    choice("audioSource","音频来源",listOf("output","playback","mic","mic-unprocessed","mic-camcorder","mic-voice-recognition","mic-voice-communication","voice-call","voice-call-uplink","voice-call-downlink","voice-performance"))
                    Text("output：系统输出；playback：应用播放捕获；mic 系列：麦克风；voice-call 系列：通话来源。是否可捕获由 Android 版本、权限和应用策略决定。",Modifier.padding(12.dp))
                    Text("系统音频可能包含被控设备其他应用的声音，不保证只捕获当前独立应用。采样率沿用 scrcpy 的 48 kHz 双声道。",Modifier.padding(12.dp))
                    SwitchPreference(title="同时在被控设备播放",summary="需要 playback 音源，受应用捕获策略限制",checked=draft["audioDup"]=="true",onCheckedChange={set("audioDup",it.toString());if(it) set("audioSource","playback")})
                    field("audioBufferMs","目标音频缓冲 ms（0–2000，默认 50）")
                    field("audioOutputBufferMs","AudioTrack 输出缓冲 ms（1–1000，默认 10）")
                    field("audioEncoder","指定音频编码器（留空自动）")
                    field("audioCodecOptions","音频 Codec 参数（可选）")
                }
            }}}
            item {Text("参数以当前 scrcpy fork 的屏幕传输能力为准。相机、录制、V4L2 和桌面窗口参数不属于 Android 远控会话。帧率上限限制编码输出，不等于虚拟显示刷新率；高级项保持默认通常兼容性最好。",Modifier.padding(12.dp))}
            item {TextButton("保存参数",{message=model.save(draft) ?: "已保存，下次新建会话生效"},Modifier.fillMaxWidth().padding(top=12.dp))}
            item {TextButton("恢复默认值",{draft=model.defaults;message=model.save(model.defaults) ?: "已恢复默认值"},Modifier.fillMaxWidth())}
            item {message?.let {Text(it,Modifier.padding(12.dp))}}
        }
    }}
}
