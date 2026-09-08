# 0.7.0 媒体配置

入口：两端共享的 设置 → 远控画面与音频。控制端保存自己的默认参数，新建普通控制、实体镜像或独立应用会话时发送给被控端。保存/恢复默认不重启现有会话，分辨率沿用现有显示设置。

| 参数 | 默认 | 范围或选择 |
| --- | --- | --- |
| 视频帧率上限 | 60 fps | 0 不限，最高 240；与虚拟显示刷新率独立 |
| 视频码率 | 8,000,000 bps | 100,000–100,000,000 |
| 视频编码 | H.264 | H.264、H.265、AV1、VP8、VP9 |
| 视频编码器 | 自动 | 可指定被控 Android 编码器名称 |
| 视频 Codec 参数 | 空 | 可选，例如 i-frame-interval=1；不接受任意服务端命令 |
| 音频 | 开启 | 可关闭 |
| 音频编码 | Opus | Opus、AAC、FLAC、RAW PCM |
| 音频码率 | 128,000 bps | 16,000–1,024,000；FLAC/RAW 不使用 |
| 音频来源 | output | playback、麦克风与 scrcpy 支持的其他来源 |
| 被控端同时播放 | 关闭 | 开启须使用 playback，仍受应用捕获策略限制 |
| 音频编码器/Codec 参数 | 自动/空 | 可指定名称和可选参数 |
| 本机音频缓冲 | 50 ms | 20–1000；Android 同时满足 AudioTrack 最小缓冲要求 |

采样率沿用 scrcpy 固定的 48 kHz 双声道。output 是被控设备系统音频，不保证只包含独立应用声音。指定编码/编码器必须由设备支持；没有承诺所有 ROM、通话音源、受保护内容都能捕获，不处理 DRM。

## 链路

MediaOptions 是 protocol 中的版本化、有界参数对象。共享 UI 通过平台适配保存参数：Android media_options SharedPreferences；Windows 用户目录 media.properties。身份、配对、外观及逐包名布局不变。

新版控制端先查询已认证 CAPABILITIES/mediaOptions，再使用 MEDIA + 原会话模式 + 参数 + 可选 AppRequest。被控端经 IRuntime.startConfigured（新增事务 9）把校验后的白名单参数交给 scrcpy。视频仍使用原认证 TLS；音频通过另一条双向 TLS，AUDIO 请求必须匹配当前控制设备指纹和 sessionId，且只允许附着一次。旧控制端的 MIRROR/APP/DEVICE 操作仍由新版被控端兼容；新配置需要升级被控端，不能把设置静默忽略。

scrcpy 本地 FD 顺序由两路 video/control 扩展为 video/audio/control（音频启用时）。音频结束或音频客户端失效，只结束音频通道。Server.java 的 ConnectorX 专用环境开关隔离音频处理器失败，不改原版无开关行为；修改来源记录在 upstream/README.md。

Android 使用 MediaCodec + AudioTrack；Windows 使用 FFmpeg + SourceDataLine。AAC/Opus/FLAC 的编码配置按各平台要求封装；Windows VP8/VP9 使用 IVF 封装，AV1 使用 OBU。当前 Windows 视频仍是原 BMP/ImageIO/AWT 路径，本轮不进行图形性能优化。

## 验证和暂停边界

构建、自动测试与最终 APK DEX 类/入口检查完成后归档本轮产物。测试使用合成图案和静音数据，不读取麦克风、不输出扬声器声音、不操作 Android 设备。

已覆盖参数序列化/默认值/白名单/边界、五种视频格式的 Windows 合成解码、Opus/AAC/FLAC 的重封装与解码、RAW 采样布局。Android 实际音频捕获与播放、双设备音视频同步、OEM 编码器矩阵仍 pending。

按用户要求，配置完成后暂停；Windows 镜像性能另行处理。仅为优化准备而添加的 JavaCPP 依赖已撤回，未替换渲染后端；当前连接不被重启或中断。

最终检查 PASS：64 项测试（protocol 47、runtime 3、Windows 14），Android/Windows 构建与打包、app/runtime lint、最终 APK 16778 个 DEX 类唯一和启动入口定义检查通过。日志 media-final-checks.txt。产物独立归档至 Artifacts/ConnectorX/ConnectorX-0.7.0-debug.apk 与 Windows-x64-0.7.0；未覆盖运行中的 Windows 客户端，未安装 APK，未创建提交。配置开发完成后暂停。
