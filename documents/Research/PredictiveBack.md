# 远端预测性返回：独立实验

历史记录：该功能及仓库专用探针已于 0.3.13 按用户要求整个撤回，本文不代表当前产品能力。

日期 2026-09-07；只操作 shennong，Android 16 / API 36。**支持 OnBackAnimationCallback 的测试应用内返回已通过；尚未接入 ConnectorX 网络协议或本机 PredictiveBackHandler。**

## 基线与边界

- scrcpy 基线不变：`19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac`。
- AOSP Android 16 源码研究固定为 `99b01a65cc4c104933788b3143285ab6bae65827`（本次 android16-release HEAD）。涉及 ActivityTaskManagerService.startBackNavigation、BackNavigationController、BackNavigationInfo、BackMotionEvent、IOnBackInvokedCallback。
- shell UID 2000，不使用 Root；该设备 com.android.shell 已获 START_TASKS_FROM_RECENTS，入口未被权限拒绝。
- 无系统装饰的独立 VD，1080×2200 / 420dpi，沿用 scrcpy DisplayManager wrapper 和当前 VD flags。
- 测试应用独立包名 `io.github.recrivenvi.connectorx.backfixture`，Compose + Miuix。只注册自己的 OnBackAnimationCallback，将 progress 映射到 Compose offset，并记录 DETAIL/HOME 页与提交次数。
- 未覆盖安装正常 ConnectorX，未替换 SystemUI 的返回控制器或注册全局返回代理，未发送 KEYCODE_BACK 或普通触摸滑动。

## 调用链

1. 在新建 VD 启动测试 Activity。
2. 查询 getFocusedRootTaskInfo，要求 displayId 和包名属于测试对象；不符合则不调用 startBackNavigation。
3. startBackNavigation(observer, null) 获得 BackNavigationInfo，要求 TYPE_CALLBACK=4，focusedTaskId 与测试任务一致；再次核对焦点。
4. 从系统返回信息取得实际 IOnBackInvokedCallback，发送 onBackStarted 和 BackMotionEvent 进度 0.2、0.5、0.8。
5. 每一步重新检查同一个测试任务仍持有焦点。取消发送 onBackCancelled；确认发送 setTriggerBack(true) 和 onBackInvoked，最后通知 onBackNavigationFinished。
6. 仅对测试包执行停止后再尝试开始；此时必须拒绝操作主屏。

没有通过测试应用自己暴露的 Binder 直接调用伪造回调，目标回调由系统返回导航入口提供。动画由远端测试应用真实执行，控制端没有旋转/缩放视频伪装返回。

## 实测

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| 定位无装饰 VD 中的目标 | PASS | 首轮 display=140/task=1624，复核轮 display=141/task=1625，type=4，focusedTask 与预期一致 |
| 开始及进度 | PASS | 应用收到 START / PROGRESS，平台动画器生成平滑中间进度 |
| 实际 Compose 布局变化 | PASS | progress=0.8 时 x=252px，符合 120dp×2.625×0.8；起点 x=0 |
| 取消 | PASS | CANCEL 后 DETAIL，commits=0，布局回到 x=0 |
| 确认 | PASS | COMMIT 后 HOME，commits=1，没有额外返回键或重复提交 |
| 非目标焦点保护 | PASS | 停止测试包后焦点回 display=0，记录 REFUSED，未调用 startBackNavigation |
| 主屏窗口前后 | PASS（快照范围） | 首轮前后同一 launcher ActivityRecord、Window；不等同于所有多显示并发场景均已验证 |
| 网络手势转发与延迟 | pending | 本次状态序列来自本机 shell 定时器 |
| 任意第三方应用 | pending | 未推断所有 TYPE_CALLBACK 均支持动画；普通回调可能只有最终返回 |
| 跨 Activity / 跨任务 / 回桌面动画 | pending | 没有接入 BackAnimationAdapter、系统动画 runner 或 Launcher 回调 |
| Android 17 / popsicle | pending | 本轮未操作 |

结论：此前最大的疑问——shell 是否能在无系统装饰的 VD 上取得正确的应用返回回调并驱动真实进度——在本设备和测试应用范围成立。仍需严格验证焦点路由，不能凭权限存在或 displayId 就盲目发起返回。若焦点不属于当前远控目标，应拒绝/取消；不可误操作物理主屏。

## 实现、证据与清理

仓库新增 debug-only `runtime/Android/src/debug/java/io/github/recrivenvi/connectorx/runtime/PredictiveBackProbe.java`，18 秒有界执行。只有确定属于 fixture 的回调可被调用；取消、确认和退出均清理导航状态。未改产品源码/协议。

PC 临时目录 `D:\Workspaces\Scratches\ConnectorX`：

- `predictive-back-fixture/`：独立测试应用完整 Gradle/Compose/Miuix 源码与构建产物。
- `predictive-back-probe.txt`、`predictive-back-fixture-events.txt`：首轮取消/确认及布局日志。
- `predictive-back-final-probe.txt`、`predictive-back-final-events.txt`：复核轮，包含失焦拒绝测试。
- `back-before-displays.txt`、`back-after-displays.txt`、`back-final-displays.txt`：显示/焦点快照。
- `predictive-back-final-build.txt`：探针 APK 构建/lint 成功，0 errors / 12 warnings；未重复无关产品单元测试。

探针使用临时推送 APK 的 app_process 类路径，未更新已安装的 ConnectorX 0.3.10。测试完成已释放 VD、卸载 backfixture、删除设备 `/data/local/tmp/connectorx-back-probe.apk`。没有在 Android 保存截图，没有关闭已有远控连接，没有创建提交。

参考源码：[ActivityTaskManagerService](https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/wm/ActivityTaskManagerService.java)、[BackNavigationController](https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/wm/BackNavigationController.java)、[IOnBackInvokedCallback](https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/android/window/IOnBackInvokedCallback.aidl)。
