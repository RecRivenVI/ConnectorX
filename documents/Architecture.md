# 架构与源码地图

## 模块依赖

```text
apps/Android (:app) ──→ runtime/Android (:runtime) ──→ protocol (:protocol)
                  └───────────────────────────────→ protocol
runtime ──编译固定源码──→ scrcpy/server
```

apps/Windows (:windows) → protocol，桌面 Compose + Miuix，DPAPI 设备身份、TLS 配对/CAST 回连、FFmpeg 解码与桌面输入。仅为控制端，无 Windows 捕获/被控模块。

protocol 无 Android 依赖。runtime 不依赖 app，拥有 Binder 定义和特权实现。APK 终结 TLS、认证授权后才调用 runtime；scrcpy 使用安装 APK 的 CLASSPATH 运行。

## Android 源码包

在 apps/Android/src/main/java/io/github/recrivenvi/connectorx/ 下：

| 包/入口 | 职责 |
| --- | --- |
| MainActivity、ConnectorApp、HostService | 稳定 Android 组件；路由、应用生命周期、前台服务 |
| connection | ConnectorEngine、Identity、Peer/候选/目录模型；当前连接协调器仍是原型实现 |
| runtimeclient | Shizuku 权限连接、已连接 FD 的客户端所有权 |
| session | RemoteSession、解码输出和控制队列生命周期 |
| input | 触摸转发适配及系统栏起点过滤 |
| display | 镜像几何、G2 轮廓适配 |
| preferences | 按包名持久化布局 |
| shortcuts | 本地快捷方式映射 |
| ui/devices | 设备/接收/配对管理页 |
| ui/remote | 远程应用与主屏控制页、视频组件 |
| ui/mirror | 实体镜像页面及绘制 View |
| platform | 外观偏好存储及本机返回设置；与身份/远端布局偏好分离 |
| ui/theme | Mishka 主题配置、颜色/密度提供器、标签 |
| ui/component | 分段卡片、自适应顶栏、主题模糊与液态玻璃等共享组件 |
| ui/navigation | 两 Tab Pager、宽屏侧栏、本地页面导航 |
| ui/screen/settings | 设置入口和完整移植的外观主题页 |

Kotlin internal 仍是模块可见，不通过改变可见性扩大对外 API。MainActivity 的设备页和控制页已分离，未重写页面行为。

## 特权模块

runtime/Android/src/main/aidl/io/github/recrivenvi/connectorx/IRuntime.aidl 保持原包和事务编号。PrivilegedRuntime、RuntimeStartup、ScreenGeometryCollector 从 APK 源集迁入 runtime，完整类名不变；VirtualAppHooks 保持 io.github.recrivenvi.connectorx.runtime。

上游代码保持独立目录，不批量移动/更名；变更清单见 upstream/README.md。宏将来在设备端复用输入引擎，尚未实现。

## 测试边界

runtime/src/test 测启动等待/超时；protocol/src/test 测控制帧、布局与触点起点。tools/diagnostics/android 保存可选 app/runtime debug 源集，默认不参与构建。探针不能被当作产品功能或端到端验收。

## 后续拆分

ConnectorEngine 仍承担发现、认证、配对和会话编排；RemoteSession 仍组合控制队列与解码。正式开发优先用行为测试覆盖后逐步拆分，不把目录归类当作领域层已解耦。真实旋转、后台保活与协议能力协商仍需设计/实测。

## 0.5.1 共享 UI

apps/Android (:app) 与 apps/Windows (:windows) 都依赖 shared/ui (:ui)。这是具备 Android 与 JVM 目标的 KMP 模块，主题、Mishka 字符串、组件、设备页、应用目录、设置和导航均只有一份源代码。

源码地图上表中的 ui/theme、ui/component、ui/navigation、ui/screen/settings、ui/util 现位于 shared/ui/src/commonMain/kotlin。ui/devices 的视图也迁入 shared，Android 原路径仅保留 androidDevices 适配。RemoteAppsPage 委托共享 ApplicationsPage；实际远控视频/输入页仍在 Android。

PlatformStorage 变为共享接口，Android 同名工厂仍返回原 SharedPreferences 实现（原文件名和键名保留）；Windows WindowsAppearanceStorage 原子写入用户目录 appearance.properties。DevicesModel 只包含可展示状态和动作回调，不持有特权服务、证书或连接。平台身份/权限/会话生命周期保持各自引擎所有权。

## Windows 0.6.0

Windows 模块按 DesktopEngine、AppOptions、DesktopSession、FfmpegDecoder、ControlQueue、VideoPanel/WindowsTouch、DesktopGeometry 和 DesktopShortcuts/DesktopInstance 分离。共享 UI 不拥有这些资源。新增 CAPABILITIES 为认证后能力查询，原 Wire 控制帧增加有界 scroll/type3 与 committed paste/type9，不改变 Binder/runtime 接口。详见 apps/Windows/README.md。

## 媒体配置与音频

protocol/MediaOptions 管理有界参数与服务器白名单；shared/ui/MediaSettingsPage 是两端同一设置入口。startConfigured 新增 Binder 事务 9，不恢复预测返回。音频启用时 runtime 输出三路 scrcpy FD，APK 通过独立认证 AUDIO 通道桥接音频。AndroidAudioPlayer 与 WindowsAudioPlayer 独立于视频解码生命周期；见 Media.md。

## scrcpy 子模块

scrcpy 已由 vendored 文件改为 RecRivenVI/scrcpy 的完整 Git 子模块。子模块路径统一调整为仓库根目录 scrcpy/；VirtualAppHooks 的唯一源码现位于子模块 server/src/main/java/io/github/recrivenvi/connectorx/runtime，ConnectorX runtime 继续编译它。子模块定制修改现已提交至 fork，父仓库 gitlink 固定对应集成提交。
