# 当前开发基线

0.4.0 在下述控制基线上完成 Mishka 风格前端；当前 APK 最低 Android 13，主题设置/本地导航说明见 [Frontend](Frontend.md)。视频边缘模糊与远端预测返回仍不启用，新增的是本地 UI 表面效果及本地页面返回设置。

基线：0.3.13-standard-back / versionCode 18。仓库整理不改变版本号，也不代表新版设备验收。历史中的“最新版本”仅对应当时记录，不覆盖本文。

| 能力 | 当前行为 | 验收边界 |
| --- | --- | --- |
| 局域网控制 | NSD + 手动 IP，TLS 1.3 双向认证，持久身份/配对 | 用户确认画面、控制、多指；完整负向安全矩阵 pending |
| 权限 | Shizuku UserService 启动 scrcpy app_process；启动自动接收/连接权限服务 | 不等于 Root、重启后无人值守均完成 |
| 独立应用 | 列表/包名启动，真实 VD，控制端 DPI；按包名保存下沉和远端系统栏设置 | 用户整体体验确认；ROM/应用适配矩阵 pending |
| 返回 | 普通返回键映射，强制重启当前目标包回到当前 Display | 预测性返回已整体撤回 |
| 会话 | 应用后台保留、Surface 可重新挂载；明确退出关闭对应会话 | 合成生命周期测试通过；所有厂商/后台策略 pending |
| 触摸 | 起点准入，系统栏起手拒绝，接管后越界持续 | 协议/状态测试及 shennong 本地分发通过；完整实机矩阵 pending |
| 设备镜像 | 等比例居中、自有 G2 圆角、外侧灰边 | G2 为近似造型，挖孔遮罩/排除禁用 |
| 刷新/旋转 | 请求刷新率；当前按窗口变化 flex resize | 不等同于实际满帧或传感器→远端→本机旋转闭环 |

## 已确定的保留规则

身份、配对、应用布局偏好、快捷方式映射保持原有键和格式；组件 MainActivity / ConnectorApp / HostService / PrivilegedRuntime、Binder IRuntime 完整名称保持。scrcpy 自有包保持不变。

## 已撤回/暂停

新增待研究能力：可选 FLAG_SECURE 安全窗口捕获支持，默认关闭；明确排除 DRM 和受保护媒体缓冲区。仅确认范围，尚无实现，见 [ADR 0008](Decisions/0008-SecureWindowCapture.md)。

- 预测性返回、其控制消息/Binder/runtime/探针：已删除，不重新启用。
- 视频边缘延展、Miuix progressive blur：已删除。
- 远端自定义 Insets/padding、全屏跟随：暂停；当前使用可用区域与用户下沉设置。
- 挖孔贴图恢复：暂停；不把圆角算法当作挖孔几何。

宏、音频、完整剪贴板/文件、公网、NAT/relay、最终 UI/UX 均不是已完成能力。

## 历史证据

[版本交付记录](History/Prototype2.md)、[布局与触摸](History/AppLayoutTouch.md)、[会话保留](History/RetainedSessions.md)、[研究索引](README.md)。既有 33 项测试迁移为 runtime 3 项、protocol 30 项。重构后结果另见 Development.md 的整理验收。

## Windows 开发

0.5.0 将 Windows x64 纳入当前开发，限定为控制方；Android 增加认证设备类型和主动投送入口。构建与实机证据见 [Windows](Windows.md)。

0.5.1 提取 shared/ui，共享两端前端。Android 产物已构建但本轮未安装；Windows 只启动供用户验收。源码/构建证据见 [Frontend](Frontend.md)。

0.6.0 Windows 控制能力补齐情况与各项证据见 [Windows](Windows.md)。Android versionCode 23 增加滚轮/提交文本能力协商与控制帧；本轮只构建，不安装。

0.6.1 Android 启动修复：日志证实共享 DevicesPage.kt 与 Android 同包同名适配文件生成重复 DevicesPageKt，导致 NoSuchMethodError。Android 适配迁为 AndroidDevicesAdapter.kt。clean 构建/lint PASS；最终 APK 全部 16732 个 DEX 类唯一，首页与适配入口方法定义存在。旧 0.6.0 APK 被新增校验明确拒绝。shennong 已覆盖安装 versionCode 24，保留数据；未代用户打开界面，实际启动验收 pending。

0.7.0 新增共享媒体设置及音频传输/播放实现，默认 H.264/60fps/8Mbps、Opus/128kbps/50ms。音频从延期计划进入实现与验收阶段，实际设备播放 pending；详见 [媒体配置](Media.md)。Windows 图形性能优化按用户要求暂停。

## scrcpy fork 子模块迁移

2026-09-08：scrcpy 改为 RecRivenVI/scrcpy Git Submodule，gitlink 为 19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac。6 个既有文件修改及 VirtualAppHooks 保持原字节内容，配套类现位于子模块内。所有 fork 修改未提交；父仓库仅由 submodule add 暂存 .gitmodules 与 gitlink，未创建提交或推送。tools/Check.ps1（64 项测试、两端构建、lint、APK 类检查）PASS，日志位于 Scratches/ConnectorX/submodule-migration/checks.txt。未安装/重启设备或恢复图形优化。

## 首次发布

按用户授权，scrcpy fork 的集成修改以 SSH 签名 Update 提交并推送，父仓库首次 Initial commit 固定该 gitlink。历史段落中的“未提交”记录仅对应当时状态。GitHub 目标为公开仓库 RecRivenVI/ConnectorX；后续提交/推送仍须用户授权。
