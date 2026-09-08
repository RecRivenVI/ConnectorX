# 包名布局与触摸起点规则

## 0.3.9：重启与远端系统栏

独立应用右上角“⋮”新增：

- **强制重启远端应用**：释放已有触点，通过已认证连接发送 RESTART_APP 与会话令牌；主机核对配对身份、会话所属设备和令牌，runtime 再核对当前会话/目标包与 child 存活。只重启当前目标包，不接受任意包名或 shell 文本。复用 scrcpy Device.startApp(forceStop=true)，保留当前 VD，并轮询确认目标 task 回到同一 displayId。UI 防止重复点击并显示结果。重启会丢失未保存内容；不能强制停止承载当前连接的 ConnectorX 自身。
- **显示远端系统栏**：保存到控制端相同包名的 layout preferences，默认 false。下次新建远程应用会话生效；按钮重启应用不重建 VD，因此不会应用这个开关的待生效修改。UI 同时显示当前 Display 请求值与保存开关。应用列表和快捷方式启动均读取该值。

系统装饰启用时使用 APP_OPTIONS 扩展操作及显式布尔字段，普通 APP 保留旧格式和默认关闭行为。新版 runtime 新增 AIDL startAppOptions / restartApp；旧事务编号不变。双方应更新本版。参数最终传至 scrcpy vd_system_decorations，不修改 VD 尺寸/DPI、本机栏下沉或触摸规则。开启并不保证 ROM 在副屏生成状态栏，也不强迫全屏应用显示栏。

scrcpy 基线仍为 `19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac`；本次在已修改的两个上游文件增加错误传播：ActivityManager.forceStopPackage 返回布尔结果，Device.startApp 在停止失败时返回 -1，避免假报强制重启成功。归属/许可见 upstream/README.md。

验证：构建、32 项单元测试及 lint 通过，0 errors / 12 warnings；新增 APP_OPTIONS 布尔值往返和旧请求默认 false 测试。日志 `D:\Workspaces\Scratches\ConnectorX\app-options-final-checks.txt`、`app-options-protocol-tests.txt`。最终 debug 探针增加后再次 assembleDebug 成功，见 app-options-probe-build.txt。

shennong Android 16：仅安装并使用自建临时测试包 `io.github.recrivenvi.connectorx.restartfixture`，其无业务数据、无产品 UI，仅记录启动 PID/Display。新建 VD 130，Device.startApp(false) 返回 0 / PID 32048；Device.startApp(true) 返回 0 / PID 17108，displayId 仍为 130。底层重启原语 PASS，不代表完整 TLS→菜单→runtime 链路或所有第三方应用均已验收。证据 `restart-contract.txt`、`restart-fixture-events.txt`；临时包源码/安装包在 PC restart-fixture 目录，不提交设备产物。菜单交互和开关跨进程体验 pending，未创建提交。

以下为 0.3.8 布局/输入规则，仍保持。

0.3.9 交付：两台设备 install -r 成功，包版本均确认 versionCode=14 / 0.3.9-app-options。APK SHA-256：`45E02D643CC4239989DB701407B38899F1FF98CCF3818E3941F52BC9002FC3D4`。探针已释放 VD，临时 restartfixture 包已卸载；未操作日常应用进行强制停止测试。

当前版本：0.3.8-app-layout，versionCode 13，2026-09-07。

## 布局

整个移除 RemoteEdgeBars、PixelCopy/Choreographer 边缘采样、模糊专用合成预览及 miuix-blur 依赖，APK 最低版本恢复 Android 10。画面未覆盖的系统栏区域纯黑。

远程应用右上角“⋮”菜单提供“延伸到通知栏下方”和“延伸到导航栏下方”，独立开关、默认关闭。修改立即调整真实 VD 尺寸并自动写入控制端私有 SharedPreferences。以包名为键，同包名跨被控设备共用设置；不同控制端不自动同步。重连、重开及桌面快捷方式都会读取保存值。横屏保持忽略挖孔预留；不启用远端系统装饰或注入 Insets。顶部菜单始终避让本机状态栏。

## 输入规则

RemoteVideo（P1/P2）和 DeviceMirrorView 共用 RemoteTouchListener / TouchOrigins：

- 每个触点只在 DOWN 判断起点：真实画面内部且不属于控制端系统栏才能接管。
- 系统栏区域起手始终拒绝，即使视频已延伸至栏下方。
- 画面外起手后移入，不补 DOWN，不产生远端输入。
- 内部起手后不重复命中测试；Android 保留原触摸目标，禁止父布局拦截，跨出画面后仍传递 MOVE/UP。
- 镜像灰边、G2 圆角裁掉部分不可起手；已接管拖动可经过这些区域。
- 不钳制越界坐标：可以发送负值和超出画面宽高的移动/抬起位置，不在边界发送提前 UP。
- ACTION_CANCEL、Surface 销毁、布局尺寸失效和断连仍释放触点。Android 系统可能接管手势，不能承诺接收系统未交给应用的事件，也不能在取消后伪造继续拖动。

Wire 的 DOWN 校验仍要求画面内坐标，MOVE/UP 允许有符号越界值；RemoteSession 只允许已存在触点的 MOVE/UP。双方均须更新，旧版校验会拒绝越界坐标。没有修改 scrcpy 上游：其 PositionMapper 按尺寸版本检查和坐标矩阵变换，本来就不裁掉画面外位置。

## 验证和证据

- PASS：31 项既有/新增单元测试。新增 TouchOrigins 的起点拒绝、持续接管、多指独立性、取消，以及 Wire 的负坐标/越界 MOVE/UP 往返与越界 DOWN 拒绝。
- PASS：assembleDebug、lintDebug，0 errors / 12 warnings。日志 `D:\Workspaces\Scratches\ConnectorX\app-layout-final-checks.txt`。
- PASS（shennong、Android 16、VD 123、本地 Android 分发）：TouchContractActivity 使用产品 RemoteTouchListener，实际 View.dispatchTouchEvent 输出 outside-start=true、inside-exit=true（DOWN 200,200 → MOVE -200,900 → UP -200,900）、bar-start=true（nav=56px）。证据 `touch-contract-events.txt`、`touch-contract-runtime.txt`，位于同一 PC 临时目录。
- 该测试只记录本地 sink，不发送远端输入，不代表完整网络远控或所有 ROM 的系统手势兼容。完整远控手感、多指实机、设置视觉和跨进程持久化体验仍 pending。
- 两台设备已覆盖安装；最终版本与清理结果见交付记录。未创建提交。

交付确认：shennong/popsicle 均 versionCode=13、versionName=0.3.8-app-layout、minSdk=29。APK SHA-256 `874D5BCC374D14C66CA5032E9EA4090F3FC077DC5632F3371FED3B8DB1E689F0`。探针已退出，dumpsys display / activity 未发现测试 VD 或 TouchContractActivity 残留。源文件检索确认产品无 RemoteEdgeSampler、RemoteEdgeBand、progressiveTextureBlur 或直接 miuix-blur 依赖。

复现：使用已有 DisplaySemanticsProbe 的 `touch-contract` 参数，在其新建的装饰 VD 上运行测试 Activity，60 秒自动销毁；测试 Activity 仅 debug 可用，受 DUMP 权限保护。没有截图保存到 Android。
