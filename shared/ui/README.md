# 共享 Compose UI

`:ui` 是 Kotlin Multiplatform 模块，提供 Android library 与 JVM 两个目标。两端依赖同一源码，不再复制设备页或另写 Windows 主题页。

- `commonMain/kotlin/.../ui/theme`：ThemeConfig、主题渲染、颜色/密度 CompositionLocal、主题标签。
- `ui/component`：卡片、顶栏、模糊、液态玻璃、动画。
- `ui/navigation`：两 Tab、600dp 宽屏侧栏、Pager、本地导航与主题页面。
- `ui/devices`：DevicesModel、DeviceAction、统一设备卡片和主页。
- `ui/remote/ApplicationsPage`：搜索、应用卡片、启动与可选快捷方式入口。
- `ui/screen/settings`：设置和完整外观与主题页面。
- `commonMain/composeResources`：从 Android 迁出的 Mishka 原文资源，两个目标共同打包。
- `platform/PlatformStorage`：偏好存储接口与原有键名，不包含平台实现。

Android 提供 SharedPreferences、androidDevices、系统 BackHandler、Shizuku/接收/投送/快捷方式动作。Windows 提供 WindowsAppearanceStorage、windowsDevices、桌面身份与会话动作；不伪造 Shizuku 或被控权限。

远控视频和输入仍由各自平台承载；共享组件不拥有连接、Surface 或进程生命周期。Android 独立应用系统栏和触摸规则保持原实现。Windows 窗口统一使用 ConnectorTheme，系统预测返回选项只在有对应平台回调时显示。

依赖 CMP 1.12.0 / Miuix 0.9.4-rc01；JVM 21，Android 最低 33。迁移来源与许可证见 ../../licenses/Mishka-sources.json。
