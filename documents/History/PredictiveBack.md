# 远端预测性返回接入

**已撤回：0.3.13-standard-back。** 用户要求移除整个功能，产品代码、类型 100 手势协议、AIDL backEvent、runtime、A17 构造兼容、专用探针及对应测试已删除。恢复 BackHandler 在返回确认后发送普通 KEYCODE_BACK；不再执行预测进度或全局焦点校验。此文仅保留研究历史。

## 0.3.12：Android 17 事件构造修复

用户反馈 popsicle 作为被控方无效、反向有效。本次只读签名查询找到旧实现的必然失败点：

```text
popsicle API 37: BackMotionEvent(float, float, long, float, boolean, int)
shennong API 36: BackMotionEvent(float, float, long, float, boolean, int, RemoteAnimationTarget)
```

Android 17 保留时间戳，但移除末尾动画目标参数；旧代码只有七参带时间戳和六参无时间戳分支，无法构造 A17 事件，并在发送 onBackStarted 前退出。

新增 BackMotionEvents 工厂按实际签名选择并缓存构造函数，保留旧分支；生产 runtime 和诊断 probe 共用。先构造事件，再标记开始回调已发送。开始之前的不支持接口允许降级普通返回；开始之后出错取消，不补一次可能重复的返回。

验证：两台设备均以 shell 只读实例化事件，x=120/y=300/time=1234/progress=0.5/edge=1 正确保留；未在 popsicle 上调用返回、改变焦点或启动测试 UI。证据 `D:\Workspaces\Scratches\ConnectorX\back-api-fixed-popsicle.txt`、`back-api-fixed-shennong.txt`；原始签名分别在 back-api-popsicle.txt、back-api-shennong.txt。原日志没有保留具体返回异常，因此不能断言此处是所有现场问题的唯一原因。

构建、38 项测试（含四个构造兼容测试）与 lint 通过，0 errors / 12 warnings。日志 back-a17-checks.txt、back-a17-probe-build.txt。临时查询 APK 已从设备删除。沿用用户之前明确的直接安装授权，两台设备覆盖安装并确认 versionCode=17 / 0.3.12-back-a17；覆盖会重启进程，安装前明确告知。APK SHA-256：`2F79F52926E9DF731537AB46945B519B274FD95027E8099B60ED290510870BFD`。完整 A17 手势链路仍待用户复测，未创建提交。

以下为原接入记录。

0.3.11-predictive-back / versionCode 16，2026-09-07。基于 [shennong 独立实验](../Research/PredictiveBack.md)，当前仅用于独立应用模式。

## 行为

- 控制端从系统边缘返回手势取得开始、进度、取消、确认，走独立导航通道，不把它转换为普通远端触摸。
- 支持应用内预测性返回的远端回调可驱动实际动画。TYPE_CALLBACK 不保证应用本身提供动画，普通回调仍可能只在最后返回。
- 没有适用预测性回调、其他返回类型或部分缺失接口时，在确认后向目标 VD 发送一次 BACK；取消不发送 BACK。不实现跨 Activity、跨任务、回 Launcher 的系统预测动画。
- 焦点不属于会话 display/package/task 时拒绝操作，不抢焦点，不回退为主屏返回。菜单显示最近的 runtime 状态，便于排查。
- 菜单/对话框打开时使用本机返回；镜像/P1 的普通返回保持。收起界面、尺寸改变时取消未完成手势，连接保留。

## 实现

`RemoteAppScreens` 使用 PredictiveBackHandler，耗尽 progress Flow 后确认，CancellationException 发送取消；空 Flow（普通返回按钮）也只执行一次确认。

`RemoteSession` 分配会话内递增手势 ID，开始时释放已有触点，手势期间暂停普通触摸。待发送进度只保留相同手势最新值，开始/取消/确认不合并。输入积压紧急释放同时取消返回手势。

`Wire` 增加类型 100，23 字节：type、phase、gestureId、progress、归一化 x/y、edge。严格校验范围和有限数值，仅独立应用会话可使用；沿用现有 TLS 控制连接，不为每帧创建新连接。主机在已配对授权校验后截获消息，通过 IRuntime.backEvent 转交，不把扩展类型送进 scrcpy。

`PrivilegedRuntime` 核对会话 ID 和存活 child，使用自己的目标包/Display 元数据，不接受远端任意指定主屏。`PredictiveBackRuntime` 在已有 framework HandlerThread 执行：核对焦点 display/package/task，startBackNavigation 后再核对 focusedTaskId；进度与确认也核对原任务。应用回调使用 onBackStarted/Progressed/Cancelled/Invoked，结束通知 onBackNavigationFinished；重复/旧 ID 不再次触发确认。最多保留未完成手势 30 秒，超时只取消手势，不关闭连接。

接入代码位于 ConnectorX 自有目录，没有新增 scrcpy 上游修改。控制端坐标归一化后映射远端 Display 尺寸；尚未做端到端延迟与各类 Insets/方向组合的完整测量。两端都应更新本版。

## 验证

- 构建、34 项单元测试通过，lint 0 errors / 12 warnings。新增四阶段协议往返、非独立应用拒绝、非法阶段/ID/NaN/坐标/边缘拒绝测试。日志 `D:\Workspaces\Scratches\ConnectorX\predictive-back-integration-final.txt`。
- 使用同一产品 PredictiveBackRuntime 类在 shennong Android 16、VD 142、task 1627 执行：开始、进度、取消通过；取消后 DETAIL / commits=0；确认后 HOME / commits=1；重复确认没有第二次 COMMIT；失去焦点返回 rejected。不是复制实验逻辑来代替产品 backend 验证。
- 证据 `predictive-back-runtime-integration.txt`、`predictive-back-runtime-events.txt`（同一 PC 临时目录）。临时 backfixture 已卸载、VD 已释放、设备端临时探针 APK 已删除。
- 上述实机测试调用实际 runtime 类，但未贯穿本机真实手势→TLS→Binder 的完整双设备链路；用户手势体验、网络延迟、第三方应用覆盖以及 popsicle 的系统接口兼容仍 pending。
- 按用户明确要求安装两台设备；未创建提交。

安装后打开独立远程应用，从本机屏幕边缘慢慢返回并分别取消/确认。在支持的远端页面观察预览；遇到不响应可查看“⋮”里的预测性返回状态。不要把没有动画自动判断为返回失败，应用本身可能只有普通回调。
