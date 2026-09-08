# ConnectorX 仓库约定

## 开发基线

- 0.4.0 前端以 Mishka `52f2a7c687fd68fd2617cf6775e26661005e61d2` 为参考；“外观与主题”页面正文/文案完整移植，来源见 licenses。CMP 使用正式版 1.12.0，Miuix 按用户最终决定使用 0.9.4-rc01。应用因 blur 模块最低 Android 13，app JVM target 21；runtime/protocol 保持 Java 17。
- 应用级主页/设置使用两个 Tab，宽屏切侧栏；本地主题模糊/液态玻璃及本地预测/横移返回按设置生效。此处不恢复视频边缘模糊、不恢复远端预测返回协议，也不把本地横移返回应用到远控画面的触摸上。

- 正式开发基于当前 0.3.13 原型延续，不重写 scrcpy、不丢弃已验证能力。先读 README、documents/CurrentState.md、Product.md、Architecture.md、Prototypes.md 和 upstream/README.md。
- 名称 ConnectorX，applicationId / namespace 固定 io.github.recrivenvi.connectorx。MainActivity、ConnectorApp、HostService、PrivilegedRuntime 和 IRuntime 的完整类名保持稳定；不批量更名 scrcpy 包。
- Android UI 使用 Compose + Miuix，参考 MiuixDemo。Windows x64 已纳入开发且仅作为控制方，不提供被控能力；必要的 Go 服务只保留边界，宏后续共用输入引擎，不提前创建空工程。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| shared/ui/ | 两端共享主题、资源、设备页、应用目录、设置和自适应导航；平台状态/动作接口 |
| apps/Windows/ | Windows x64 控制端、DPAPI 身份、配对/投送、视频与鼠标键盘 |
| apps/Android/ | APK 入口、连接/身份、runtime 客户端、会话/解码、输入、偏好、快捷方式、Compose UI |
| runtime/Android/ | 特权服务、AIDL、启动/几何采集、scrcpy hooks 与上游编译 |
| protocol/ | 不依赖 Android 的协议、输入状态和几何计算及单元测试 |
| scrcpy/ | RecRivenVI/scrcpy fork Git Submodule，含 ConnectorX 修改 |
| upstream/ | 上游来源、固定提交和集成说明 |
| tools/diagnostics/ | 显式 opt-in 的实机探针；默认 APK 不包含测试 Activity |
| documents/ | 当前产品、架构、开发计划、验收；History 保存旧记录，Research 保存研究 |
| gradle/ | Wrapper、依赖/插件版本目录 |
| licenses/ | 移植来源、文件映射、原许可证及署名 |

根组织目录小写，文档用 documents；Android 子目录和源码包沿用生态约定。改结构时同步本表、README、模块说明和链接。

## 当前行为约束

- Windows 0.6.0 控制端具备逐包名显示配置、重启、flex resize、G2 实体镜像、IME 提交/多指接入、桌面快捷方式和单实例转交。输入法 Unicode 只在用户提交时粘贴，必须先协商 clipboardPaste 能力；不自动同步剪贴板。不把合成测试当作真实硬件/双机验收。

- 0.5.1 两端依赖 shared/ui (:ui) KMP 模块，共用 Compose 主题/页面/导航。平台只提供身份、权限、会话与存储适配；不可另写简化 Windows 主题或设备页。Android 视频/系统栏/输入和 Windows 解码窗口仍按平台承载。

- Windows 仅控制 Android；设备类型和能力随认证配对保存，发现广播不是授权。Android Windows 卡片提供主动镜像/应用投送；投送需 Windows 接受，不抢占已有会话。见 ADR 0009。

- 可选 FLAG_SECURE 支持仅为待验证计划：默认关闭、限定活动远控会话，优先隔离于捕获路径；不处理 DRM、受保护缓冲区或安全媒体链路，不将 Root 身份当作能力已完成。见 ADR 0008。

- 产品身份与 ADB 身份分离；权限失效不删除配对，未认证连接不得进入控制或媒体。
- 独立应用默认位于本机系统栏内；按包名持久化通知栏/导航栏下沉选项。横屏不额外避让挖孔。背景纯黑，无边缘模糊或远端 Insets 代理。
- 远端系统装饰默认关闭，可按包名保存开关，下次新建会话生效。强制重启只操作当前会话目标包并保持 Display，停止失败不能误报成功。
- 普通 BackHandler → KEYCODE_BACK；预测性返回已撤回。真正旋转闭环未接入，不把 flex resize 当成完成旋转支持。
- 镜像为系统半径 + 自有 G2 圆角，不宣称真实物理曲率；挖孔绘制/触摸排除暂时禁用，后续仍用真实贴图。
- 触点仅按 DOWN 起点接管；画面外/本机系统栏起手不得转发，画面内起手越界后继续 MOVE/UP，不能钳制/提前抬起。系统 CANCEL、失效布局、断连仍释放触点。
- 0.4.1 明确区分退出与后台：用户点击远控“退出”时关闭对应会话并释放远端显示；应用进入后台、Surface 重建或静止画面不主动断连。不能将退出偷换为收起。停止新接收不关旧连接，切换入口不隐式退出旧会话；显式断开、撤销配对及实际传输失效按原规则处理。
- 应用启动自动开启接收并连接已运行的 Shizuku；初次授权和启动 Shizuku 本身仍需系统/用户配合。

## 工作与验证

- 用户明确要求前不创建任何提交，不推送 Git；保留无关改动。目录整理不隐含设备覆盖安装授权。
- 设备实操测试默认只用 shennong；两台设备允许只读查询几何，必要时 Root 只读。不改系统设置/SELinux。覆盖安装会重启进程，不擅自破坏已有连接。
- 截图必须 adb exec-out screencap -p 直接保存 PC：D:/Workspaces/Scratches/ConnectorX；禁止在 Android 保存图片。
- 生成物按 D:/Workspaces/Artifacts/ConnectorX，临时文件按 D:/Workspaces/Scratches/ConnectorX。APK、录屏、凭据和原始隐私日志不入仓库。
- 研究固定提交；改上游必须更新来源/理由/许可证说明。不得假设普通 APK、shell、Root 在所有 ROM 上等价。
- 检查入口：tools/Check.ps1。探针构建显式加 -Diagnostics，执行探针前读 tools/diagnostics/README.md。
- 验收用 pending / PASS / degraded / FAIL / deferred。区分编译、单元测试、合成实机与端到端用户验收，未验证能力不得写成完成。

- 共享 Kotlin 文件与平台适配不得在同包生成同名 JVM facade；Android 首页适配文件固定用 AndroidDevicesAdapter.kt。交付前执行 tools/VerifyApk.py 检查最终 APK 的重复类和入口方法定义，避免编译成功却启动 NoSuchMethodError。

- 0.7.0 媒体配置统一放在共享设置页，保存只影响新会话，不断开旧连接。音频独立双向 TLS 并绑定控制方指纹/sessionId；音频失败不结束视频/控制。双方新协议需能力校验，不静默忽略配置。Windows 渲染优化当前暂停，不能把本轮合成音频/编码测试写成真机播放已通过。

- scrcpy 是 RecRivenVI/scrcpy fork 的 Git Submodule；scrcpy 源码改动与 VirtualAppHooks 均在子模块内维护。未经用户要求，不提交子模块或父仓库、不推送；gitlink 不能保存子模块未提交改动。

- 所有 Git Submodule 统一位于仓库根目录；scrcpy 子模块路径为 scrcpy/，不得再嵌套在 upstream/ 下。
