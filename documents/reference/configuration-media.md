# 媒体参数

设置保存仅影响新建跨设备会话。本机应用直连 Surface 不使用视频编码参数。字段、校验和服务端参数白名单由 [MediaOptions](../../core/protocol/src/main/java/io/github/recrivenvi/connectorx/protocol/MediaOptions.java) 实现，本页是参数的完整说明。

| 参数 | 默认 | 范围或语义 |
| --- | --- | --- |
| 视频最大边 | 自动 | 实体镜像 1600；独立应用原始尺寸；0 不限，可选覆盖 |
| 尺寸对齐 | 1 px | 1/2/4/8/16 |
| 帧率上限 | 60 fps | 支持小数；0 不限，最高 240；不是显示刷新率请求 |
| 视频码率 | 8000000 bps | 100000–100000000 |
| 视频编码 | H.264 | H.264/H.265/AV1/VP8/VP9，须设备支持 |
| 视频编码器 | 自动 | 可指定编码器名称 |
| 视频 Codec 参数 | 空 | 结构化参数，例如 i-frame-interval=1；不是命令 |
| 裁切 | 空 | width:height:x:y |
| 捕获方向 | 跟随 | scrcpy 方向、翻转与锁定语义 |
| 捕获角度 | 0° | -360–360° |
| 编码失败降档 | 开启 | downsize-on-error |
| 忽略编码器约束 | 关闭 | 高级兼容项，可能导致失败 |
| 本机视频缓冲 | 0 ms | 0–2000 |
| 音频 | 开启 | 可独立关闭；失败不结束视频/控制 |
| 音频编码 | Opus | Opus/AAC/FLAC/RAW PCM |
| 音频码率 | 128000 bps | 16000–1024000；FLAC/RAW 不使用 |
| 音频来源 | output | output/playback 与支持的麦克风音源 |
| 被控端同时播放 | 关闭 | 开启须 playback，受应用策略限制 |
| 音频编码器/Codec 参数 | 自动/空 | 可指定名称和结构化参数 |
| 目标音频缓冲 | 50 ms | 0–2000 |
| AudioTrack 输出缓冲 | 10 ms | 1–1000，同时满足系统最小值 |

音频沿用 scrcpy 的固定采样格式。output 是设备系统音频，不保证只含目标应用。相机、录制、V4L2、桌面窗口及 USB 输入不属于此屏幕会话设置。

界面视频码率以 Mbps、音频码率以 kbps 显示和输入，精确十进制换算为原有整数 bps 后交给 MediaOptions；本表保留存储单位。高级编码/缓冲默认折叠，默认值与校验未改变。

## 传输和指标

视频通过认证 TLS；音频为独立双向 TLS 并绑定会话。音频结束只清理音频；Android 播放使用 MediaCodec/AudioTrack。

视频压缩队列有界，溢出请求关键帧并恢复 decoder。异步 callback 输出到 SurfaceView，只有 codec 声明支持时才设置低延迟选项。Surface 不可用时停止本地解码并排空媒体，重挂重新取得关键帧。Surface.setFrameRate 是提示，不是实际刷新成功证明。

质量面板仅在“显示会话诊断”开启时出现，记录视频接收/解码输出 fps、payload Mbps、关键帧率、帧大小、队列、输入等待、相对积压、解码器及硬件标记；音频记录 payload、包率、格式、underrun 和缓冲。吞吐不含 TLS/IP，解码输出不等于 SurfaceFlinger 上屏，相对积压不含首帧固定延迟。运行提示不能替代完整触摸到显示时延测量。

默认值、序列化和拒绝无效值由现有测试覆盖；真机编码器、音频播放、同步、后台恢复及功耗矩阵见[传输场景](../../validations/scenario-transport_quality/validation.md)。
