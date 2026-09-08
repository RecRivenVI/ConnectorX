# Windows x64 控制端

模块 :windows，复用 shared/ui (:ui) 与 protocol；CMP 1.12.0 + Miuix 0.9.4-rc01、JVM 21。仅控制 Android，绝不提供 Windows 被控服务。

`./tools/StartWindows.ps1 -Build` 构建并启动。打包自带 Java runtime；H.264 解码使用本机 FFmpeg（PATH 或 CONNECTORX_FFMPEG），不将其二进制放入仓库。
默认产物 `D:/Workspaces/Artifacts/ConnectorX/Windows-x64/ConnectorX.exe`。

## 源码职责

- DesktopIdentity / DesktopEngine：DPAPI 身份、持久配对、双向 TLS、发现、投送与会话调度；停止接收保留已有会话。
- WindowsUiAdapter：shared/ui 的平台动作与外观存储，不另写主页/主题页。
- DesktopSession / FfmpegDecoder / ControlQueue：网络会话、可重建解码器、输入发送与积压释放。视频输出以 BMP 管道传递，全分辨率解码；不是零复制硬件解码。
- DesktopSessionPage / AppOptions：逐包名保存分辨率、DPI、刷新率、远端系统栏、跟随窗口、Unicode 输入选项；会话信息和原 Display 内强制重启。
- DesktopGeometry：复用 protocol G2/坐标公式，还原当前系统圆角近似；摄像头遮罩仍禁用。
- VideoPanel / WindowsTouch：画面起点准入与越界持续拖动、键盘修饰键、IME 提交、仅在本进程视频 HWND 接收 WM_TOUCH。没有全局输入钩子或 Windows 输入注入。
- DesktopShortcuts / DesktopInstance：`.lnk` 仅含本机 opaque ID；启动时校验当前配对。已运行实例通过带 DPAPI 保护令牌的 loopback 通道接收请求，不创建重复远控。

## 平台行为

默认输出在本机标题栏/工具栏/任务栏可用区内；Windows 没有 Android 通知栏/导航栏，因此不提供无意义的手机系统栏下沉开关。原生窗口负责最大化/最小化。
普通远程控制显示矩形画面；设备镜像使用 G2 裁切、外侧灰边和上下留白。远端系统栏开关仅作用于独立应用的新会话。
窗口尺寸变化防抖后请求真实 VD resize；这是 flex resize，不是旋转闭环。分辨率/DPI/刷新率的实际结果在会话设置显示，OEM/应用可限制结果。
IME 仅发送 committed text，Unicode 使用显式远端剪贴板粘贴，不持续同步剪贴板；旧手机未声明能力时不发送不支持的控制帧。
关闭远控窗口退出该会话；最小化/视频控件重建保持连接；关闭主窗口明确退出客户端。

完整使用和证据见 ../../documents/Windows.md。音频、宏、文件、持续双向剪贴板、公网与真实旋转闭环仍按整个产品计划延期。

0.7.0：支持 H.264/H.265/AV1/VP8/VP9 及音频配置，WindowsAudioPlayer 提供 Opus/AAC/FLAC/PCM 播放链路；入口在共享设置页。实际播放待用户验收，镜像性能优化暂停，BMP 渲染路径本轮保留。
