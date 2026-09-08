> 历史快照：不作为当前实现规范。当前入口见 [文档索引](../README.md)。

# 架构边界

0.3.10 当前连接生命周期以 [会话保留](RetainedSessions.md) 为准：Application 级会话集合、界面可重新挂载、停止新接收不关闭既有控制连接；不同设备可保留会话，同一被控设备仍限一个会话。

状态：产品架构基线。原型 1 已采用 APK TLS 终结点、Shizuku UserService 和独立 scrcpy 子进程，见 [ADR 0002](../Decisions/0002-Prototype1.md)；下方长期架构与未覆盖能力仍待验证。

```text
控制设备 apps/Android
  Compose + Miuix / 设备列表 / 快捷方式 / 解码 / 输入采集
             |
  平台无关身份、授权、认证、会话与能力协商（Protocol）
             | 加密的局域网会话
被控设备 apps/Android
  配对确认 / 身份管理 / 权限状态 / runtime 启动协调
             | 受调用方校验的 Shizuku Binder / 已连接 socket 描述符
被控设备 runtime/Android
  Shizuku 或 Root 启动适配 → scrcpy 派生控制能力
  Display / Activity / 捕获 / MediaCodec / Input Engine
                                         ↑
                              未来设备端宏调度器
```

首轮网络终结点已在 APK；0.1.2 本地通道修正见 [ADR 0003](../Decisions/0003-BinderBridge.md)。后续长期生命周期与密钥隔离继续验证，未认证流量不能进入上游控制 socket。设备可同时承担控制端和被控端角色，角色不决定永久身份。

## 模块责任

| 模块 | 负责 | 边界 |
| --- | --- | --- |
| apps/Android | UI、发现/授权流程、普通应用生命周期、解码、触摸键盘、系统返回、快捷方式 | 不因持有用户授权就假设 APK 获得 shell 权限 |
| runtime/Android | 特权启动、显示/应用控制、捕获编码、注入、能力探测、资源清理 | 尽量复用 scrcpy Java 与兼容层 |
| Protocol | 设备身份、持久授权、会话、版本/能力、控制及媒体语义 | 无 Compose、Activity、Binder 类型暴露到线上协议 |
| Upstream | 官方提交和引入记录 | 与 ConnectorX 修改区分并可追溯 |

## 可复用与待改造

优先逐项研究并复用 scrcpy server、输入/多指/键盘、显示与 rotation、捕获、MediaCodec、hidden API wrappers、剪贴板及有价值的 codec/transport 实现。首轮剪贴板完整同步和音频无需完成。

ConnectorX 需要补充稳定身份、配对授权、加密网络会话、启动适配、应用内 IPC、持久生命周期、断连回收、快捷方式及 Android 解码 UI。上游协议属于内部适配边界，不能直接作为永久兼容的公开网络协议。

## 生命周期与故障

- 将“已配对”“在线”“权限可用”“runtime 就绪”“媒体会话运行”作为独立状态。
- runtime 崩溃/权限撤销：停止接受输入，释放触点与显示资源，报告明确原因，保留配对记录。
- 断线：释放未结束触点；重连建立新认证会话，禁止补发旧点击。Display/Activity 是否保留及期限待实验。
- 并发会话：P1/P2 可先限制一个控制会话；冲突必须显式返回，不能两个控制端无序注入。
- 未来宏和实时输入共享仲裁；宏使用设备端单调时钟，取消与断线策略单独定义。
- 不可用功能通过 capability 和原因报告，不以 Root 字样替代实际能力探测。

## 暂不锁定

minSdk/targetSdk、Compose/Miuix/AGP 版本、Shizuku 入口形式、IPC、密码套件、发现方式、媒体封装、Windows 技术栈均待定。Go 只在确需独立服务时引入。
