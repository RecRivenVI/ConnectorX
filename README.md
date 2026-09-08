# ConnectorX

让远程 Android 成为本机的扩展运行环境：持久配对后，直接打开远端应用或进入完整设备镜像。

正式开发基于 0.3.13 原型，当前版本 **0.7.0-media-options**。前端参考 Mishka，主页/设置两个 Tab，“外观与主题”完整移植。应用后台保持连接，用户明确退出远控时关闭对应会话。参见 [前端交付说明](documents/Frontend.md)。

## 入口

- [当前基线与限制](documents/CurrentState.md)
- [产品目标](documents/Product.md) · [交互规则](documents/Experience.md)
- [架构与源码地图](documents/Architecture.md)
- [正式开发顺序](documents/Development.md) · [验收矩阵](documents/Prototypes.md)
- [构建与探针](tools/README.md)
- [全部文档、研究与历史](documents/README.md)
- [scrcpy 固定基线与许可](upstream/README.md)

## 仓库结构

| 目录 | 内容 |
| --- | --- |
| shared/ui/ | 两端共享主题、资源、设备页、应用目录、设置和自适应导航；平台状态/动作接口 |
| apps/Windows/ | Windows x64 控制端、DPAPI 身份、配对/投送、视频与鼠标键盘 |
| apps/Android/ | APK、连接/身份、会话/解码、输入、偏好/快捷方式、Compose + Miuix UI |
| runtime/Android/ | 特权服务与 AIDL、几何/启动、scrcpy hooks |
| protocol/ | 纯 Java 协议、输入状态和几何算法 |
| scrcpy/ | scrcpy 4.1 fork Git Submodule |
| upstream/ | 来源、固定提交及集成说明 |
| tools/ | 检查脚本及按需编译的设备探针 |
| documents/ | 当前规范、研究证据、架构决策和历史 |
| gradle/ | Wrapper 与 libs.versions.toml |
| licenses/ | Mishka 移植来源、许可证和署名 |

Android 包名 io.github.recrivenvi.connectorx；当前 APK 最低 Android 13（主题 blur 模块要求）。CMP 1.12.0、Miuix 0.9.4-rc01，当前特权入口 Shizuku shell。构建使用 JDK 21+，app JVM target 21，runtime/protocol Java 17。

Windows x64 客户端已纳入开发，仅作为控制方；支持 Android 主动镜像/应用投送，见 [Windows 使用与验收](documents/Windows.md)。

## 构建

在 local.properties 配置本机 Android SDK 后执行：

```powershell
./tools/Check.ps1
```

默认 debug APK 不编译设备实验入口。明确需要探针时运行 `./tools/Check.ps1 -Diagnostics`。依赖和插件版本集中于 gradle/libs.versions.toml，SDK/Java 编译目标保留在各模块。

APK 输出 apps/Android/build/outputs/apk/debug/app-debug.apk；脚本不自动安装、提交或推送。设备安装会中断进程，须按当前授权执行。移植 UI 的 GPL-3.0 与显式 Apache-2.0 文件归属见 [许可证说明](licenses/Mishka-NOTICE.md)；既有代码和 scrcpy 来源保留。

媒体设置：两端 设置 → 远控画面与音频；参数、默认值和验收边界见 [媒体配置](documents/Media.md)。

初始化源码：`git submodule update --init --recursive`。scrcpy 修改在 `scrcpy` 子模块维护；集成修改已包含在 gitlink 固定的 fork 提交中，见 upstream/README.md。
