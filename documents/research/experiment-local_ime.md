# 本机虚拟屏：主屏键盘与虚拟 IME 高度

2026-09-23

## 问题

保留本主题在上述日期的调查问题、环境及证据边界；不作为当前版本能力声明。

## 方法

沿用原记录注明的固定源码审计、只读资料或有界实机实验方法；本轮仅迁移文档，没有重跑这些实验。

## 发现

1.0.2 产品续验：实际 ConnectorX → QQ 复现物理宿主接管 IME 后提前收键盘、返回再退页的问题；本机页面通过 `FLAG_ALT_FOCUSABLE_IM` 排除宿主的输入法目标资格，保留正常窗口焦点，不恢复主动显示/任务聚焦。shennong 上重复唤起、按键预编辑、两步返回、本地目录输入及继续会话已实测 PASS；完整中文提交与其他 ROM/输入法仍 pending。见 [本机显示与输入](../design/mechanism-local_display.md)。下述探针结论保留原测试边界。

当前决策（1.0.1）：用户放弃主屏键盘及虚拟高度方案，改回虚拟屏 LOCAL；撤回 LocalImeFocus，保留运行时收键盘/返回保护。下面的 FALLBACK 和焦点恢复证据仅为历史研究。新的 Display 72 不调用焦点覆盖接口，证实 LOCAL IME 1241px 和隐藏后返回原语；主屏点击后回到同一输入框有 active=false 的 degraded 样本，切换到另一个输入框恢复。原生探针、产品安装与产品交互的边界见 [本机显示与输入](../design/mechanism-local_display.md)。

日期：2026-09-23。用户授权在 shennong 做必要实操并清理测试文件。本轮只验证机制，不修改产品行为、不覆盖 ConnectorX、不创建提交。

### 环境与隔离

- shennong，ADB 序列号 `500bbf7e`，Android 16 / API 36，1440×3200 / 560dpi。
- 特权探针为独立 `app_process`，UID 2000，SELinux `u:r:shell:s0`；没有使用 Root、修改系统设置或 SELinux。
- 产品保持 `0.11.1-virtual-ime` / versionCode 31。原有本机会话 Display 65 / Task 459 在测试前后都存在。
- 临时包 `io.github.recrivenvi.connectorx.imefixture`，两个原生 Activity 分别运行在物理 Display 0 和测试 Display 66。两者同包/同进程；不能将结果扩大为任意第三方 App、不同 UID、所有输入法均已通过。
- 测试 Display 66 使用产品相同的 trusted/own-focus 等 flags、scrcpy display wrapper 和 LocalImeFocus；指定 FALLBACK_DISPLAY，保持 1440×3200。ImageReader 只消费测试显示输出，不用于产品后台解码，也没有向 Android 文件系统保存图像。
- 主屏宿主不接收文本、使用 ADJUST_NOTHING；目标有两个原生 EditText，分别检查 Insets、编辑器焦点和 InputConnection。未注入文字，测试中文来自点击百度小米版输入法的拼音键和候选项。
- 测试 APK SHA-256：`CD6902B595C73A54CC598389215EAFDDE551F14B3C970ABF0AB8B1A901A679DB`。

### 结果

| 项目 | 状态 | 实际证据 |
| --- | --- | --- |
| 主屏键盘连接虚拟屏输入框 | PASS（受控样本） | `mCurTokenDisplayId=0`，IME client 的 `mSelfReportedDisplayId=66`，EditorInfo 指向测试包和 fieldId=101 |
| 中文候选提交 | PASS（受控样本） | 点击主屏键盘 n、i 和候选“你”；目标 EditText 101 的 TextWatcher 收到“你”，未使用 input text 或应用侧 setText |
| 失焦后恢复、切换输入框 | PASS（单次恢复样本） | 点击物理宿主后 IME 隐藏；复用 LocalImeFocus 并注入虚拟屏 DOWN/UP，fieldId=102 成为 active，重新点击键盘后第二个输入框收到“你” |
| 主屏键盘高度可观测 | PASS | 主屏宿主收到 IME bottom 858→1067→1241px，最终 visible=true；目标虚拟屏仍为 IME 0 / false |
| 键盘出现不改变窗口尺寸 | PASS（窗口/显示尺寸） | 主屏宿主、目标 root 均为 1440×3200，VirtualDisplay 仍为 1440×3200；本实验没有绘制产品的真实缩放镜像，不能写成完整镜像布局验收 |
| shell 给任务添加 560px 导航栏来源 | PASS（正向对照） | WCT 成功；目标真实 WindowInsets.navigationBars.bottom=560，底部 EditText y 从 2880 移至 2320；移除后 Insets 回到 0 |
| shell 给任务添加 560px IME 来源 | FAIL（普通 WCT 路径） | Binder 调用成功，但目标仍收到 IME 0 / false；先添加来源再 showSoftInput 也没有形成可见 IME 高度 |
| 现有顶层焦点 getter | degraded | LocalImeFocus 的请求返回 accepted=true，但可选的 focusedDisplay() 始终为 -1；有效绑定需由目标端状态和 IMMS 数据交叉确认 |
| 向虚拟屏发送 KEYCODE_BACK | FAIL（仅收起键盘目标） | 测试目标 Activity 被销毁，不能将该注入动作视为可靠的跨屏 IME 收起 |
| 向物理屏发送 KEYCODE_BACK | FAIL（仅收起键盘目标） | 键盘隐藏，同时物理宿主 Activity 随后销毁；并非可直接用于产品的通用替代方案 |
| 完整第三方应用兼容、动画和长期稳定性 | pending | 没有修改/操作第三方 App 内的输入框，没有压力测试或 IME 动画同步验收 |

### 对实现的影响

1. 主屏绘制 IME、虚拟屏应用持有 InputConnection 的原生链路具备真实输入证据。焦点请求成功不等于绑定成功；产品诊断需区分请求、窗口/编辑器状态、实际 IME 绑定和显示位置。
2. 主屏 IME 高度能由宿主 WindowInsets 读取，同时保持宿主窗口大小。产品可据镜像内容矩形与主屏键盘相交区域计算虚拟遮挡高度：遮挡像素高度 / 镜像缩放比例。此次没有实现或验收完整动态映射。
3. `WindowContainerTransaction.addInsetsSource()` 是不经过窗口创建的任务级通路，因此成功绕开了旧透明窗口实验的 `openSession / Unknown pid` 阻碍。但这不等于解决 IME 可见性。
4. 导航栏来源成功只能证明普通底部 Insets 可注入；把它作为兼容占位会改变类型语义，不能声称提供了真实 IME Insets，亦不能保证只监听 IME 的应用响应。
5. IME 来源可见状态仍需另一条系统集成路径。没有验证 Root 或系统 Hook；本次失败不能推导为所有 Shizuku 路线都不可能，也不能据此宣称 Root 必然解决。
6. 返回收起需专门研究 IME 所在显示及实际输入目标，不能直接把所有返回键改发 Display 0。测试没有恢复已撤回的远端预测返回。

### 源码依据

固定参考 `android-16.0.0_r3`，ROM 结论以本轮实测为准：

- [WindowContainerTransaction.addInsetsSource](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/core/java/android/window/WindowContainerTransaction.java)：隐藏任务事务接口。
- [WindowContainer.addLocalInsetsFrameProvider](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/services/core/java/com/android/server/wm/WindowContainer.java)：创建来源、绑定 owner，并通知子窗口；没有把 IME 来源强制设为 visible。
- [InsetsSource](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/core/java/android/view/InsetsSource.java) 与 [WindowInsets](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/core/java/android/view/WindowInsets.java)：默认可见类型排除 IME，非可见来源返回零 Insets。
- [旧来源窗口实验](../research/experiment-display_semantics.md)：与本轮 WCT 是不同路径，保留各自证据边界。

### 复现与清理

源码及操作边界见 独立输入法诊断（已移至工作区 Scratches/ConnectorX/RepositoryStandard-1.0.0/diagnostics/ime/README.md）。只包含临时原生控件 fixture 与 shell 探针，不参与默认产品构建。

PC 证据目录：`工作区 Scratches/ConnectorX/ime-test-20260923/`。`probe.txt` 记录真实 Binder 调用；各 `*-fixture.txt` 为专用日志 tag，`*-ime.txt` 为 IMMS 相关字段，`*-displays.txt` 为显示状态。`composing.png` 证明主屏中文候选；`second-editor-fixture.txt` 含两个输入框的实际“你”提交。截图全部通过 `adb exec-out screencap -p` 直接保存 PC。

清理 PASS：任务 Insets 已移除，探针记录 `RELEASED display=66`，控制进程退出；测试 APK 卸载 Success，包查询和 pidof 无结果。最终显示列表仅保留测试前已有的 0 和 65，ConnectorX 仍为 versionCode 31。测试没有向 `/data/local/tmp` 推送文件。PC 构建中间物清理，复现源码及证据保留。

### 续验：跨 UID 输入、隐藏请求与第二次返回

同日根据用户“试试看”继续在 shennong 测试。仅修改独立探针，没有改产品代码、覆盖 ConnectorX、使用 Root、调整系统设置/SELinux 或创建提交。测试显示依次为 67/68/69，每轮结束独立释放；主屏宿主改为 `io.github.recrivenvi.connectorx.imehostfixture`，目标仍为 `io.github.recrivenvi.connectorx.imefixture`，两者不同 UID、不同进程。

| 项目 | 结果 | 证据与边界 |
| --- | --- | --- |
| 不同 UID 的主屏 IME → 虚拟屏编辑器 | PASS（受控样本） | Display 68 目标 UID 10326、宿主 UID 10328；Display 69 目标 UID 10329、宿主 UID 10330。物理键盘输入 n/i 并点候选“你”，两轮目标 field101 均记录 U+4F60；未注入文字 |
| IMMS.hideSoftInputFromServerForTest | FAIL（稳定收起目标） | shell 调用无权限异常，主屏 IME 在 00:34:18.078 隐藏，00:34:18.157 又显示；目标未退出，但不能采用为可靠隐藏实现 |
| 宿主 WindowInsetsController.hide(ime) | degraded（隐藏状态不一致） | 宿主已收到 IME 1241px/visible=true 后调用，IMMS 的 mInputShown 仍 true，不能确认目标显示请求已撤销；画面隐藏与服务/窗口状态未形成一致闭环。初次未显示时的调用不计入有效样本 |
| IStatusBarService.hideCurrentInputMethodForBubbles(displayId) | PASS（两次已显示样本） | shell UID 2000 调用成功；Display 69 上分别在 bottom/top 编辑器活动时测试，主屏 IME 隐藏且保持，目标 Activity 仍在、windowFocus=true、对应 InputConnection active=true；重新点击编辑器可再次弹出并提交中文 |
| 隐藏后独立发送第二次返回 | PASS（顺序原语） | 第二次隐藏在 00:40:18.978 完成；稳定检查后向 Display 69 注入 Back，00:40:34.721 才出现目标 DESTROY；宿主继续存活。没有实现产品 BackHandler、真实系统手势或连续返回状态机，不能宣称完整返回交互已完成 |
| WCT IME 560px + 显示可见性接口 | FAIL（当前 shell 路径） | 添加来源仍为 IME 0 / false；updateDisplayWindowRequestedVisibleTypes 对本次显示调用抛 SecurityException，要求 MANAGE_APP_TOKENS。没有提权绕过，也不推导所有 Shizuku 路线必然失败 |
| 窗口/显示尺寸 | PASS（窗口/显示层） | 主屏与目标 root 保持 1440×3200；VirtualDisplay 69 同样为 1440×3200。探针不绘制产品缩放预览，完整镜像视觉验收仍 pending |
| 焦点恢复与首次弹出 | degraded | 某次启动时 showSoftInput 因 PHASE_CLIENT_VIEW_SERVED 失败；后续可出现 accepted=true 但未显示。重新建立编辑器输入连接后恢复。focus 请求被接受仍不代表 IME 绑定已完成 |
| 其他输入法、浮动键盘、未提交候选的返回语义、长时间压力 | pending | 此轮只验证百度小米版停靠键盘、已提交的中文文本，以及有界顺序操作 |

#### 为什么系统隐藏路径不同

固定源码参考 Android `android-16.0.0_r3`：

- [StatusBarManagerService.hideCurrentInputMethodForBubbles](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/services/core/java/com/android/server/statusbar/StatusBarManagerService.java) 检查 STATUS_BAR_SERVICE 与用户，再转到 InputMethodManagerInternal.hideInputMethod。
- [InputMethodManagerService](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r3/services/core/java/com/android/server/inputmethod/InputMethodManagerService.java) 的 MSG_HIDE_INPUT_METHOD 会向当前焦点窗口客户端发送 setImeVisibility(false)，让客户端撤销显示请求。测试接口则直接调用 hideCurrentInputLocked。源码差异与“前者保持隐藏，后者迅速重弹”的实机现象一致；不是对 OEM 全部内部逻辑的证明。

接口的 displayId 用于解析 IME 用户，不构成精确任务所有权保证。诊断控制器在调用前读取 IMMS 当前 client/editor 包名与 displayId，输入目标不是测试包时拒绝；目标退出后的拒绝已验证。这个读后调用仍有竞态，不能直接作为正式产品的可靠会话归属机制。产品需要独立的输入绑定观测与隐藏确认，不采用高频 dumpsys 轮询作为最终实现。

#### 结论与后续门槛

返回已有可继续接入的 shell 隐藏原语，但虚拟 IME 高度仍没有可见来源。普通 WCT 添加来源和当前显示可见性接口均未打通，因此本轮不更改产品 IME 策略，不安装不完整功能。下一步若研究系统侧集成，应同时解决来源可见性/布局分发和输入绑定观测；单纯把进程改为 Root 不是已验证方案。

PC 证据分别在 `工作区 Scratches/ConnectorX/ime-test-20260923-back/` 与 `ime-test-20260923-back-system/`。`r2-hidden-fixture.txt` 记录测试接口重弹，`r2-insets`/probe.txt 记录权限失败，`shown-host-hide` 与 `shown-system-hide` 记录有效对照，`text-hidden-stable`/`second-back` 记录两步返回，`cleanup.txt` 记录卸载与显示清理。截图均直接写 PC。

清理 PASS：两个临时 APK 卸载成功，包查询与 pidof 为空，probe.txt 记录 RELEASED display=69。只剩原有 Display 0 与 65；用户原有 QQ Task 459、ConnectorX Task 455 保留，产品仍为 versionCode 31 / 0.11.1-virtual-ime。所有测试 Insets 随显式移除/任务及 owner 释放清理，没有向设备临时目录推送文件。PC 三个本轮构建目录内的 27 项生成物已移至回收站，控制凭据已移除，保留复现源码和证据。

### 后续决定：放弃虚拟高度，接入已验证路径

用户明确放弃虚拟 IME 高度，1.0.0 恢复主屏键盘与外层预览缩小，保留完整 VD 分辨率；不再把系统侧 Insets 注入列为本轮后续任务。返回状态机与经输入归属检查的隐藏实现已接入，交付与验收见 [LocalDisplay](../design/mechanism-local_display.md)。

Display 70 的独立 fixture 又直接编译、调用新增 LocalImeController/LocalImeBinding 产品源码：有界 Binder snapshot 解析成功，检查和请求 45ms 后主屏隐藏、目标输入连接保持；第二次调用已隐藏状态返回 false；再发送普通 Back 才销毁目标。该结果验证 runtime 原语，未覆盖安装产品，不等于 Compose 返回交互端到端验收。两个临时包、测试显示与控制进程已清理，现有用户会话保留。

## 结论

结论以原记录的证据等级和设备范围为限。当前采用的行为见[产品范围](../project/overview-product.md)、[交互](../design/principle-interaction.md)与[进度](../project/status-current.md)，不据历史记录补写新验收。
