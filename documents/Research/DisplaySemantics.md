# 独立应用 Insets、全屏与旋转可行性

后续：[开启系统装饰并尝试隐藏栏图层](SystemDecorations.md)，仅恢复导航栏基础避让，尚未形成通用 Insets 方案。

2026-09-07，仅在 shennong `192.168.0.101:33193` / Android 16 API 36 操作；未操作 popsicle、未使用 Root、未修改系统设置。scrcpy 基线仍为 `19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac`。

## 目标与实现范围

独立应用模式本机系统栏跟随远端应用显示/全屏请求，取代始终可见要求。控制端传感器提供旋转意图，远端实际 Display 状态经过方向仲裁后驱动控制端 Activity。优先研究完整 VirtualDisplay 的远端 Insets 语义。

本次只新增 debug 诊断入口，未修改产品控制链路：

- `runtime/Android/src/debug/java/io/github/recrivenvi/connectorx/runtime/DisplaySemanticsProbe.java`：UID 2000 检查，复用 scrcpy wrapper 创建独立 1440×3200 / 560dpi 显示，无 system decorations；ImageReader 消费帧但不保存图像，60 秒释放。
- `apps/Android/src/debug/java/io/github/recrivenvi/connectorx/DisplaySemanticsActivity.kt`：Compose + Miuix，记录真实 WindowInsets / Configuration，按诊断参数请求固定方向、显示或隐藏系统栏。
- debug manifest 的测试 Activity 受 `android.permission.DUMP` 保护，无 launcher 入口，不进入 release。

最终测试 APK 仅安装 shennong，版本仍是 0.3.2；SHA-256 为 `C998E7FBB46D2E49771C0D75451BD482E6D335C0E7C910B909CC7F66224212C7`，使用哈希区分诊断构建。

## 实测矩阵

| 项目 | 状态 | 结果 |
| --- | --- | --- |
| shell 创建独立 VD | PASS | UID 2000，SELinux `u:r:shell:s0`，通过 scrcpy wrapper |
| 自由方向 → 90° / 270° | PASS | 实际 rotation 1 / 3，3200×1440，Activity Configuration 同步 landscape |
| 固定竖屏后请求 270° | PASS | 保持 rotation 0、1440×3200 |
| 固定横屏后请求 0° | PASS | 仍为横屏；本例从 rotation 3 转为 1，尺寸仍 3200×1440 |
| 恢复自由方向 | PASS | 采用之前记录的用户旋转偏好恢复 rotation 0；之后可再次请求 270° |
| shell 透明窗口提供 Insets | FAIL | openSession 抛出 `Unknown pid=12556 uid=2000`，尚未到实际 Insets source 添加阶段 |
| shell 设置远端 Insets controller | FAIL | `Access denied, requires: android.permission.MANAGE_APP_TOKENS` |
| 无装饰 VD 的栏 Insets | PASS（负面事实） | 普通模式栏 Insets 就为零、visible=false；hide/show 不提供可区分全屏的栏 Insets |
| 读取应用全屏意图 | degraded | 完整窗口 dump 可读到真实请求变化；仅诊断轮询，未实现实时事件通道 |
| 完整传感器→远端→控制端闭环 | pending | 未测试传感器、跨设备状态回传、控制端 Activity 跟随、视频和触摸同步 |
| 第三方应用避让 | pending | 来源窗口未成功，不能宣称游戏或聊天 App 已能正确避让 |

方向矩阵仅覆盖自有测试 Activity，不覆盖 LOCKED、NOSENSOR、180°、平板兼容或其他 OEM。

## Insets 阻碍

独立 app_process 不在系统应用进程表内。Android 16 AOSP Session 构造函数要求 WindowProcessController，不存在就抛出 Unknown pid。这不能靠增加 providedInsets 参数解决，也不能据此假定 Root 必然解决。

- [AOSP Session](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/services/core/java/com/android/server/wm/Session.java)
- [AOSP WindowManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r3/services/core/java/com/android/server/wm/WindowManagerService.java)

当前 Shizuku app_process 直接创建透明来源窗口这条路径不能作为已可用能力接入。后续可研究受系统管理的特权窗口宿主或系统集成，但尚未验证，没有安装模块或改 framework。备用 controller 权限实验仅向本次新建 VD 传 null，没有改其他 Display 的控制器。

## 全屏意图仍存在

VD 85 的测试窗口在 hide(systemBars) 后出现：

```text
Requested non-default-visibility types: statusBars navigationBars captionBar systemOverlays
```

这是相对默认可见类型的差异，不是这些栏当前可见。show 后该行消失。普通、全屏、恢复阶段的 mTopIsFullscreen 却一直是 false，因此不能用它判断无装饰 VD 的全屏状态。

[AOSP WindowState](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/services/core/java/com/android/server/wm/WindowState.java) 也把 requestedVisibleTypes 写入 proto。下一步可实验按 displayId + 目标窗口读取结构化 dump，测量延迟和开销；正式方案仍需研究事件通道。状态栏、导航栏应分别传递，不能压成单一 fullscreen 布尔值。

## 证据与复现

原始输出在 PC `D:\Workspaces\Scratches\ConnectorX\display-semantics`，不提交设备隐私日志：

- probe-1.txt：首版诊断从 system context 取 DisplayManager 导致 packageName/UID 不匹配；改为 scrcpy wrapper 后解决，不是产品 runtime 回归。
- probe-2.txt：来源窗口失败与初次旋转验证，VD 82。
- probe-3-matrix.txt：完整方向矩阵，VD 83。
- probe-4-policy.txt：实际是前一已安装构建的重复方向矩阵，VD 84；不作为新增权限/policy 证据。
- probe-5-policy.txt：最终构建，VD 85；包含权限拒绝、方向矩阵、三阶段 policy。
- policy5-windows-0..6.txt：VD 85 测试 Activity 段，0..3 普通，4..5 全屏请求，6 恢复。
- activity-final.txt：实际 Insets / Configuration。
- display-after.txt、activities-after.txt：没有实验 Display 或测试任务残留。
- checks.txt：27 项既有单元测试通过；首次 lint 发现诊断 API guard 问题。
- checks-final.txt：修复后 assembleDebug / lintDebug 成功，0 errors、10 既有 warnings。

安装 debug APK 后，只在指定测试设备运行：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = '192.168.0.101:33193'
$apk = (& $adb -s $serial shell pm path io.github.recrivenvi.connectorx).Trim().Substring(8)
& $adb -s $serial shell "CLASSPATH=$apk app_process / io.github.recrivenvi.connectorx.runtime.DisplaySemanticsProbe no-insets"
```

去掉 no-insets 才尝试来源窗口。探针仅向自己创建的 VD 启动测试 Activity；60 秒退出并销毁该显示上的内容。输出直接进入 PC，未保存设备端截图。

## 下一步边界

旋转可进入产品原型，但须先分离 Surface 与会话生命周期。全屏读取有诊断级证据，仍需结构化读取或事件实验。真实 Insets 代理存在已验证阻碍，不把缩小 VD 静默替代成用户选择的方案。当前诊断版不等于完成独立应用体验升级；没有创建提交。
