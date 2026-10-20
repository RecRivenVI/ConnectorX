# Android 架构

网络认证在普通应用进程终结，特权系统操作集中在独立模块；本机显示直连 Surface，跨设备显示复用 scrcpy 编码传输。

```text
app ──→ core/ui
    ├─→ core/privilege ──→ core/protocol
    └───────────────────→ core/protocol
core/privilege ──编译固定源──→ third_party/scrcpy/server
```

| 模块 | 职责与边界 |
| --- | --- |
| [app](../../app/build.gradle.kts) | Android 入口、身份/发现/TLS、会话、解码、输入适配、偏好、快捷方式、悬浮窗 |
| [core/ui](../../core/ui/build.gradle.kts) | 主题、页面、资源、四栏导航；不拥有 Socket、证书、Surface 或特权进程 |
| [core/privilege](../../core/privilege/build.gradle.kts) | AIDL、Shizuku UserService、启动、系统几何、定向输入及 fork 编译 |
| [core/protocol](../../core/protocol/build.gradle.kts) | 无 Android 依赖的消息、参数校验、输入队列与几何数学 |

UI 仍使用 Android-only KMP 源集和 Compose Resources，保留生成资源包；这是[AN-03 技术债](../project/debt-existing_code.md)，不代表恢复多平台产品。

## 生命周期

`ConnectorApp` 持有 `ConnectorEngine`，`HostService` 承载接收；发现仅提供地址候选。认证通过后才可查询或控制，见[安全边界](security-pairing.md)。

`RemoteSession` 持有网络和远端控制生命周期。Surface 是附件：分离时停止本地 decoder、持续排空媒体，重挂时请求新的 codec config/关键帧；不因此销毁远端 Display。显式退出才释放对应会话，实际传输失效仍按故障关闭。

`LocalAppSession` 通过 Binder 把 SurfaceView 的 Surface 交给 fork 中 `LocalDisplayController`。VirtualDisplay 直接输出，不经过网络、编码器或解码器。完整分辨率缓冲由系统缩放到小窗，见[本机显示机制](mechanism-local_display.md)。

`GlobalScrollService` 独立拥有悬浮窗口与动画，`GlobalScrollController` 负责主屏滚轮租约和调度；不占有镜像或本机会话。

## 稳定接口

模块移动不改变包名、组件完整类名、AIDL 编号、存储格式或快捷方式。`MainActivity` 负责平台导航和显示附件；`AndroidDevicesAdapter.kt`、`AndroidLibraryAdapter.kt` 向 UI 提供状态和动作。输入/媒体接口见[协议入口](../reference/api-control_protocol.md)。

源码改动清单分别见 [scrcpy](../reference/upstream-scrcpy.md) 与 [Mishka](../reference/upstream-mishka.md)。架构拆分尚未完成的部分登记在技术债，本轮不改变所有权或线程模型。
