# 控制接口

协议常量和格式的唯一实现入口为 [Wire](../../core/protocol/src/main/java/io/github/recrivenvi/connectorx/protocol/Wire.java)、[MediaOptions](../../core/protocol/src/main/java/io/github/recrivenvi/connectorx/protocol/MediaOptions.java) 和 [IRuntime](../../core/privilege/src/main/aidl/io/github/recrivenvi/connectorx/IRuntime.aidl)。本页说明边界，不另维护一套字节偏移或事务编号表。

| 层 | 入口与含义 |
| --- | --- |
| TLS 请求 | ConnectorEngine 处理 PAIR/PAIR2、PROFILE、CAPABILITIES、APPS、媒体会话、AUDIO、元数据与重启 |
| 设备角色 | DeviceProfile；只接受 Android 控制/被控角色，发现资料不能覆盖已认证身份 |
| 目标应用 | AppRequest；结构化包名、Display Profile 和装饰选择，禁止任意命令 |
| 网络输入 | Wire 校验后桥接 scrcpy control；触点准入在控制端按 DOWN 决定 |
| 本机批量输入 | LocalTouchFrame、LocalInputQueue；不改变网络输入协议，边界事件有序 |
| 特权 Binder | IRuntime；稳定事务用于启动/停止、Surface、输入、IME、全局滚轮，调用者身份受限 |

MediaOptions 当前格式兼容旧格式读取；非默认高级参数必须确认对方能力，不能静默忽略。不根据 IP/名称认证。媒体白名单及尺寸/长度边界在对象和 runtime 两端执行。

普通网络会话拥有独立 sessionId；音频连接必须与当前控制方指纹、sessionId 匹配且只能附着一次。本机输入和全局滚轮不经媒体链路；后者另有 Binder 所有权和租约。

兼容的稳定编号与数据结构保留；旧身份/偏好迁移分支尚需按[技术债](../project/debt-existing_code.md)重新审视，不能在整理时擅自删去用户数据路径。
