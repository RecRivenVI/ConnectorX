# 当前共享前端

0.5.1-shared-ui / Android versionCode 22：两端依赖 shared/ui KMP 模块。主题、设备卡片、主页/设置、自适应侧栏/底栏、应用目录、模糊与液态玻璃来自相同源码；Windows 的临时 Home/设置说明页已移除。平台适配负责权限、身份、会话和偏好存储。

240 条共享本地化资源（含通用导航条目）与固定 Mishka 源逐条比较 PASS；主题正文仅更换资源导入，不重写选项与文案。来源映射已同步到共享目录，Android APK 与 Windows JAR 都打包许可证和来源清单。

本轮 Android/Windows 编译与打包、39 项测试、app/runtime lint PASS。新 Windows 外观和交互的人工验收 pending：按用户要求只启动，不代操作；未安装 Android APK、未操作设备或执行配对。

下文为早期 Android 前端交付记录，不代表当前共享界面已经实机验收。

# 0.4.0 前端与外观主题

0.4.1 修正：远控界面的“收起”全部恢复为“退出”，点击时实际关闭当前会话。后台/Surface 生命周期继续保持连接；两者不混淆。本机主题分区的折叠控件语义不受影响。

0.4.1-exit-behavior / versionCode 20 交付：构建、33 项既有测试及两模块 lint 通过；两台设备覆盖安装并确认版本。退出调用 closeConnection，移除对应 sessions 项；Surface 回调仍调用 detachSurface，快捷方式不隐式调用退出。实机端到端退出/后台回归待用户确认，未创建提交。日志 `D:\Workspaces\Scratches\ConnectorX\exit-behavior-checks.txt`。

版本：0.4.0-ui-foundation / versionCode 19。

## 来源与依赖

- 用户指定参考仓库 Mishka，固定提交 `52f2a7c687fd68fd2617cf6775e26661005e61d2`，参考仓库未修改。
- Compose Multiplatform **1.12.0**：本次官方 releases/latest 返回的最新正式版，2026-08-25 发布；构建依赖实际解析到 org.jetbrains.compose.ui:ui:1.12.0。
- Miuix **0.9.4-rc01**：按用户后续明确选择，与 Mishka 参考版本一致，未使用最初查到的正式版 0.9.3。
- app 因 miuix-blur 要求最低 Android 13，Miuix nav 的内联字节码要求 app JVM target 21；runtime/protocol Java 17 不变。
- 来源/哈希/映射见 licenses/Mishka-sources.json；GPL-3.0 与源文件显式 Apache-2.0 声明保留，许可证与 NOTICE 也打包至 APK assets/licenses。

## 页面与行为

主页保留接收、Shizuku 权限、配对、附近设备、手动地址、远程应用/镜像及保留会话入口，采用 Mishka 的可折叠顶栏、LazyColumn、分段卡片、滚动反馈和模糊表面。

底栏只有“主页”和“设置”，支持 Pager 切换、图标/文字模式、普通/悬浮底栏、Miuix/iOS 液态玻璃样式。宽屏按未经 UI 缩放的 600dp 阈值切换可收起侧栏，内容最大 800dp。远程应用目录也是本地导航子页；控制/镜像画面保持专用布局。

设置入口的“外观与主题”标题、摘要和子页文字直接来自 Mishka。ThemeSettingsScreen 源码除包名/导入替换及来源注释外与原文件一致；颜色与主题、界面与效果、底部导航栏各分区、选项顺序、条件展开、下拉菜单和缩放输入对话框均保留。

主题配置使用独立 connectorx_appearance SharedPreferences，不改 identity、配对、远程包名布局或快捷方式存储。模式、Monet、色板、强调色、纯黑、模糊/样式、界面缩放、底栏选项实时驱动 UI。宽屏判断使用 LocalPlatformDensity，避免缩放误触发布局切换。

“预测性返回手势”是 **ConnectorX 本地页面** 设置，跟随 Mishka 的 ApplicationInfo 开关和 Activity 重建方式；“横移返回”作用于本地导航页面。它们不恢复已撤回的远端预测返回，不拦截远控画面正常触摸。主题表面模糊也不恢复视频边缘像素延展效果。

## 验证

- ThemeSettingsScreen 正文规范化比较 PASS；228 条主题相关本地化 XML 条目与参考源逐条一致，记录 mishka-theme-verification.txt。
- 构建、33 项既有单元测试、app/runtime lint 通过。日志 mishka-ui-final-checks.txt、mishka-ui-delivery-checks.txt；现有 lint 警告保留。
- shennong 实际检查：主页/设置切换、外观页展开、Monet 实际变色、悬浮/iOS 样式、Activity 重建后主题设置恢复、本地边缘返回到设置页。截图均直接保存 PC，没有设备端图片文件。
- 测试修改已恢复 Mishka 默认选择。通过 run-as 只读确认 appearance 文件，未编辑身份或其他偏好。
- 完整宽屏矩阵、每种色板/强调色组合、所有设备上的帧率和新 UI 下远控回归仍待后续验收，不因源码移植或截图自动标为 PASS。

证据目录 `D:\Workspaces\Scratches\ConnectorX`：mishka-ui-home.png、mishka-ui-settings.png、mishka-ui-theme.png、mishka-ui-monet.png、mishka-ui-liquid.png、mishka-ui-recreated.png、mishka-ui-back-result.png、mishka-appearance-restored.xml；依赖解析见 cmp-resolved.txt。

此次设备覆盖安装由用户明确要求。未创建提交或推送 Git。

交付确认：shennong / popsicle 均覆盖安装成功，并确认 versionCode=19、versionName=0.4.0-ui-foundation、minSdk=33。最终 APK SHA-256：`EBA0EE089647F9F5DE96F9C865B9AA5BD04B013FEF580D0E2ABA5E581F619ED2`。App lint 0 errors / 17 warnings，runtime lint 0 errors / 62 warnings；后者主要为已有上游/隐藏 API 警告。本次 UI 实操仅使用 shennong，popsicle 仅确认安装，未宣称两端所有外观组合均实测。

官方版本来源：[CMP 1.12.0](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0)。Miuix RC 选择来自本次用户指示。

0.6.1 Android 启动修复：日志证实共享 DevicesPage.kt 与 Android 同包同名适配文件生成重复 DevicesPageKt，导致 NoSuchMethodError。Android 适配迁为 AndroidDevicesAdapter.kt。clean 构建/lint PASS；最终 APK 全部 16732 个 DEX 类唯一，首页与适配入口方法定义存在。旧 0.6.0 APK 被新增校验明确拒绝。shennong 已覆盖安装 versionCode 24，保留数据；未代用户打开界面，实际启动验收 pending。
