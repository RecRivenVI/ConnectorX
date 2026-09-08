# 原型 2：远程单应用

## 当前版本：0.3.13-standard-back / versionCode 18

撤回整个预测性返回实现，恢复普通 BackHandler → KEYCODE_BACK 映射，不再受预测接口焦点校验影响。保留逐包名布局/远端系统栏设置、强制重启、画面外起手规则、越界拖动与会话保留。研究文档作为历史留存。

2026-09-07 构建、33 项测试、lint 通过（0 errors / 12 warnings），源代码检索无预测性返回实现残留。两台设备覆盖安装成功，包版本均确认 versionCode=18。日志 `D:\Workspaces\Scratches\ConnectorX\revert-predictive-back-checks.txt`；APK SHA-256 `9C10ACF1660EAF59A01A7CE4534E03239887BEEDBDDDEB8276B16F16B05C3C97`。未创建提交。

## 当前版本：0.3.11-predictive-back / versionCode 16

独立应用模式由本机 PredictiveBackHandler 驱动远端返回手势；支持的应用可预览并取消，不适用的返回类型确认后降级普通返回。界面和权限边界见 [预测性返回](PredictiveBack.md)。保留现有布局、触摸起点和连接保留策略。

## 当前版本：0.3.10-retained-sessions / versionCode 15

退出画面改为收起并保留连接，Surface 生命周期与会话分离；设备页恢复或明确手动断开。此前“退出释放虚拟显示”不再是当前规则。细节与验证范围见 [会话保留](RetainedSessions.md)。

## 当前版本：0.3.9-app-options / versionCode 14

右上角“⋮”新增“强制重启远端应用”和“显示远端系统栏”。前者强制停止当前目标包并在当前 Display 启动，后者按包名持久化、默认关闭、下次新建会话生效。设置/验证见 [布局与触摸](AppLayoutTouch.md)。下方旧版本保留为历史。

## 当前版本：0.3.8-app-layout / versionCode 13

已移除模糊、边缘取样、专用预览及 blur 依赖。右上角“⋮”提供按包名保存的通知栏/导航栏下沉开关，默认关闭。独立应用与镜像改为按触点起点判断是否接管，内部起手越界后继续发送有符号坐标。使用及验收见 [布局与触摸](AppLayoutTouch.md)。以下版本均为历史。

## 当前预览：0.3.7-edge-blur / versionCode 12

- 横屏：初始 AppRequest（含快捷方式）与 Compose 可用区域都只扣除 systemBars，不为 cutout 额外让出侧边空间；竖屏维持原行为，模糊区域仍沿用原边界。
- 上下 Miuix ProgressiveBlur 设置 curve=3.0，保留既有延展/裁切层和最大半径 10dp。Miuix 使用自身非线性合成，不能把旧版“线性 10→1”的说明当作实际逐像素 Gaussian 半径测量。
- 移除固定 50ms 采样延时。Choreographer 每个 VSYNC 尝试取样；上次 PixelCopy 尚未完成则跳过，避免积压。动态刷新率由系统帧回调决定，不硬编码 60/120Hz；实际成功取样/呈现率还受 GPU 和视频来源限制，未宣称满刷新率性能通过。
- 自动开启接收与 Shizuku 连接保持。

2026-09-07 构建、既有单元测试及 lint 通过（0 errors / 23 warnings）；安装至两台设备并确认 versionCode=12。APK SHA-256：`E48F2A5CF02B719D5DCC766DA1CC25738F0B3CE3673CE9311044E3132B0E1686`。检查日志 `D:\Workspaces\Scratches\ConnectorX\edge-blur-037-checks.txt`、`edge-blur-037-final.txt`。本轮尝试横屏合成预览得到黑色截图，未确认当时屏幕状态，不作为视觉 PASS；后续视觉/性能验收 pending。未创建提交。

## 当前预览：0.3.6-edge-blur / versionCode 11

系统栏内的真实虚拟显示尺寸、远端无系统装饰保持不变。取视频 Surface 顶部/底部各一行像素，沿竖向重复至对应本机系统栏，再使用 `miuix-blur-android:0.9.4-rc01` 的 progressiveTextureBlur。半径按 Miuix 单位为 dp：靠画面 1、外侧 10，顶部向上增强、底部向下增强。不加 tint/noise；首帧前及左右区域仍黑色。图标样式保持浅色。

上游参考源码与 Demo：`compose-miuix-ui/miuix` commit `7cc339c28616708b1e417224a8e9a7e05f32f253` 的 `miuix-blur/.../ProgressiveBlur.kt`、`TextureEffect.kt`、`example/.../component/BlurSection.kt`；使用依赖，不复制上游实现。每个栏层扩展至可见高度的 10/9，裁出外侧 90%，使用线性 10→0 梯度得到可见 10→1。上下方向分别使用 Top / Bottom，外层裁切，不模糊视频及系统图标。

PixelCopy 异步复制两个一像素行，最多一对请求在途、完成后间隔 50ms；取样约不超过 20Hz，不将 CPU 整帧回读引入解码。视频 Surface 缓冲尺寸跟随视频尺寸以保持行取样，视频布局/触摸坐标仍使用原可用区域。图像仅在内存，不保存到 Android。实际性能与远控画面匹配需实机验收。

启动 MainActivity（含重新进入）自动开启 HostService 并调用 Shizuku 连接；已有授权不弹窗，binder 晚到时自动重试；首次未授权仍需确认 Shizuku 授权。不会自行激活尚未运行的 Shizuku 服务，不会取消 TLS、配对认证或开放陌生设备控制。仍可在前台手动停止接收；再次进入应用将重新开启。

由于 Miuix blur 模块声明 minSdk=33，本预览 APK 最低 Android 13，不采用强行覆盖库最低版本的方式。两台现有测试设备支持。构建日志 `D:\Workspaces\Scratches\ConnectorX\edge-blur-final-checks.txt`；新增 debug 合成 SurfaceView 入口 `DisplaySemanticsActivity --ez edge-preview true` 复用产品取样/模糊代码，仅验证图形链路，不能代表完整网络远控验收。

以下保留旧版历史。

2026-09-07 0.3.6 验证：构建与 27 项既有测试通过；最终 lint 0 errors / 23 warnings（包含最低版本升高后的旧 SDK 分支提示）。两台设备均安装并确认 versionCode=11、minSdk=33。APK SHA-256：`B9C8D5918CEFBBD240DF465EDF54EA6B82B82656C074012784BF4B28A7104A15`。

shennong 实测：合成 SurfaceView 输出 `1440×1` 两行，顶栏 48dp、底栏 16dp，截图可见顶部向上/底部向下增强的 Miuix 渐变模糊，状态栏与导航图标正常覆盖。证据 `D:\Workspaces\Scratches\ConnectorX\edge-blur-preview.png`。打开 MainActivity 后未点击“开启接收”或“启用 Shizuku”，页面已显示“正在接收局域网连接”与“权限已就绪 · UID 2000 · scrcpy 4.1”；证据 `edge-blur-startup.png`、`edge-blur-services.txt`。均为 adb exec-out 截图直接保存 PC；没有在 Android 保存图片。合成图形与已有授权自动启动 PASS，仅 shennong；真实远程 App 画面/性能及 popsicle 自动启动验收 pending。没有提交。

## 当前版本：0.3.5-safe-viewport / versionCode 10

按用户要求撤回 0.3.4 的远端系统装饰开关，恢复 `vd_system_decorations=false`。尺寸、DPI、本机黑色系统栏区域及触摸范围均保留 0.3.3 行为。版本递增用于覆盖安装，不回退应用数据。重新开启被控端接收控制并新建远程应用会话后生效。

2026-09-07 构建、单元测试及 lintDebug 通过（0 errors / 10 既有 warnings）。shennong 与 popsicle 均覆盖安装成功，包版本确认 versionCode=10 / 0.3.5-safe-viewport。日志：`D:\Workspaces\Scratches\ConnectorX\revert-decorations-checks.txt`。安装 PASS，未创建提交。

## 已撤回的对比预览：0.3.4-system-decorations / versionCode 9

保持 0.3.3 的尺寸计算、DPI、画面/触摸可用区域与本机黑色系统栏区域不变，仅把启动参数改为 `vd_system_decorations=true`，让 Android 在虚拟屏上正常提供其支持的系统装饰。没有裁掉或隐藏远端栏图像，也没有远端自定义 Insets。ROM 可能只生成导航栏，不能把开启开关等同于所有栏完整生成；应用自身的全屏请求仍由远端系统处理。

更新两端后退出旧远控会话，并关闭再开启被控端“接收控制”以确保 runtime 使用新 APK；重新启动远程应用查看。此版本视觉结果待用户确认。

2026-09-07 构建、单元测试、lintDebug 通过（0 errors / 10 既有 warnings），两台设备 install -r 成功且包版本均确认 versionCode=9。日志：`D:\Workspaces\Scratches\ConnectorX\system-decorations-checks.txt`；APK SHA-256：`A2242C66354ED9B95DC9DF779EB94BC2533CA689D366C6AE17EB15D892957D04`。安装 PASS，实际系统栏显示及布局验收 pending，未提交。

## 当前预览：0.3.3-safe-viewport / versionCode 8

用户最新选择：暂停远端 padding/Insets 方案，远端虚拟显示直接使用控制端系统栏以内的尺寸。初次启动和桌面快捷方式都扣除本机系统栏/cutout 并集；进入画面后根据实际内容布局尺寸进行 flex resize。保留控制端 DPI，不强杀目标 App；远端 system decorations 仍关闭。

视频、触摸及菜单在内部可用区域；外侧为纯黑，系统栏图标使用浅色。本机栏保持可见，暂不跟随远端全屏请求隐藏；没有接入新旋转闭环或修复既有 Surface 生命周期限制。远端不接收额外 padding/Insets。

安装后退出旧远程应用会话，再从设备应用列表或桌面快捷方式重新打开；观察底部控件不再和本机导航条重合，顶部画面位于本机状态栏之下。比较横屏时的侧边避让；完整镜像 G2 圆角逻辑不变。构建/安装结果与用户视觉验收分开记录。

2026-09-07 交付：assembleDebug、27 项既有单元测试通过，lint 为 0 errors / 10 既有 warnings。shennong `192.168.0.101:33193` 和 popsicle `192.168.0.13:43157` 均 install -r 成功，并以 dumpsys package 确认 versionCode=8 / versionName=0.3.3-safe-viewport。构建日志：`D:\Workspaces\Scratches\ConnectorX\safe-viewport-checks.txt`。APK SHA-256：`C05219EDB9E54B9B4007392DBD5FD591D8FBCE4A640CD112BD609DA034653187`。安装 PASS；本次未代用户操作完整远控，视觉及触摸验收 pending。没有提交。

以下为此前版本与研究历史，不能当作 0.3.3 当前栏策略。

2026-09-07 最新需求与实验：[显示语义可行性](../Research/DisplaySemantics.md)。独立应用系统栏改为跟随远端全屏请求；旋转改为传感器意图驱动远端、实际状态驱动控制端。仅完成 debug 底层实验，下面保留已交付版本行为，不代表新策略已经接入。

版本 `0.2.0-prototype2` / versionCode 4，仍为同一 `io.github.recrivenvi.connectorx` APK。原型 1 主屏镜像继续保留。用户已确认 P1 画面、操作及多指正常；本轮 P2 由用户操作验收。

后续用户反馈 P2 目前符合预期，但通知栏与导航栏是否悬浮覆盖尚待商议。原型 3 不擅自改变这项策略；下面的细分矩阵仍保留各自的证据要求。

两台设备已安装此版本，配对数据保持不变。构建、14 项本地测试及 lint 检查通过，详细证据见 [交付记录](Deliveries/Prototype2Delivery.md)。

## 操作

1. 更新后两台重新打开 ConnectorX；被控端 **开启接收 → 启用 Shizuku**。原有配对继续使用，无需解除或重配。
2. 控制端在已配对设备下点击新增的 **远程应用**。
3. 搜索并选择应用，点击 **打开远程应用**。建议先试系统设置，再试日常应用，以区分基础显示能力与 App 的副屏限制。
4. 画面进入独立虚拟显示模式。本机状态栏与导航栏保持可见；系统返回发给远端应用，本机 Home 保留本机行为。
5. 右上角 **⋮** 可查看实际 Display ID、Activity Display、尺寸/dpi、刷新率、视频尺寸与解码输出率，或退出远程应用。
6. 应用选择弹窗的 **添加到桌面** 会请求系统确认。桌面入口绑定“设备 + 包名”，以后点击直接认证连接并启动该应用。

两台地址仍使用局域网 IP；应用内不用无线调试端口。快捷方式冷启动会先尝试发现，再使用最后地址；发现失败且 IP 已变时可回到设备页更新地址。

## 重点验收

| 项目 | 观察方式 | 当前状态 |
| --- | --- | --- |
| 列表与按包名启动 | 可以搜索；所选 App 出现在独立画面；失败有原因 | pending |
| 真正的虚拟显示 | 菜单里 Display ID 为非主屏，Activity Display 匹配；设备日志有实际参数 | pending |
| 尺寸与 density | 比较请求与实际值，正常内容不变形；记录编码对齐/限制 | pending |
| 系统栏 | 远端系统栏不混入画面，本机系统栏可见且尽量透明；检查其后内容可触性 | pending |
| 返回、触摸、键盘 | 页面内正常导航、多指；键盘优先使用视频内远端 IME，硬件键盘新增按键/修饰键映射 | pending |
| 刷新率 | 区分请求 Hz、实际 Display Hz、解码输出 fps；fps 计数不是端到端延迟 | pending |
| 旋转 | 本机开启自动旋转，竖转横再返回，观察布局、菜单尺寸/rotation 和触摸对应 | pending |
| 方向锁定 | 测试固定横/竖屏 App；允许等比留边，不强制拉伸或宣称遵循所有方向请求 | pending |
| 快捷方式 | 从桌面打开正确设备的正确应用；保留认证，不再人工配对 | pending |
| 结束与重开 | 菜单退出后可以再次启动；返回根部若剩空画面，先使用 ⋮ 退出 | pending |

旋转使用 scrcpy flex display 真正调整 Display 宽高，Activity 的 orientation/configuration 响应需实测；不能把 rotation 数字未变直接当作没有显示尺寸变化，也不能把视频缩放当作旋转成功。

## 实现范围

本版每次新建虚拟 Display，关闭会销毁其中任务；未实现跨会话恢复。视频 Surface 被系统销毁（例如某些后台/旋转行为）时会断开；此类行为需要记录，不能视为已解决的本机级体验。

App 列表为当前用户可启动应用，不包含完整多用户/工作资料管理。桌面图标暂用 ConnectorX 通用图标，最终视觉与管理留后。音频仍未实现，完整剪贴板、公网、宏与原型 3 屏幕外形也不在本轮。

客户端窗口尺寸支持 128–4096 像素、72–960dpi；请求刷新率 24–240Hz，编码上限不超过 120fps。接口不支持刷新率请求时会显示降级，不能把请求值当作实际值。

架构及源码修改见 [ADR 0004](../Decisions/0004-VirtualApplications.md)。构建命令沿用 README，截图仍只允许直接输出电脑端 `D:\Workspaces\Scratches\ConnectorX`，本轮不代为操作或截图。
