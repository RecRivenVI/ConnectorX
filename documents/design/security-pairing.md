# 身份、配对与特权边界

产品配对不依赖 ADB 身份或设备当前地址。普通 APK 保存长期身份并终结网络；Shizuku 独立服务只接收经过应用认证和结构校验的特权请求。

## 身份与网络

当前实现由 [Identity](../../app/src/main/java/io/github/recrivenvi/connectorx/connection/Identity.kt) 使用 Android Keystore 建立设备证书身份。首次配对双方核验校验码并确认，保存指纹及对方资料；以后每次建立双向 TLS 会话，不因地址变化、离线或权限失效删除配对。

NSD 发现和手动地址只确定候选地址，不授予信任。控制、媒体与元数据查询必须通过身份检查。音频子连接另绑定当前控制方指纹及 sessionId，不能仅凭发现资料附着。

重新安装或密钥丢失产生的新身份不能凭同名自动继承授权。撤销在本地立即拒绝旧身份；不能承诺离线对端同步删除。备份迁移、细分权限和完整安全负向矩阵尚待实现或验收。

## 运行身份

普通 APK 没有任意输入注入/可信虚拟显示权限。`PrivilegedRuntime` 经已运行且用户授权的 Shizuku UserService 运行，实际 shell/Root 身份、SELinux 和 OEM 检查分别记录，不宣称跨 ROM 等价。

网络会话由 runtime 创建本地已连接 FD，scrcpy server 以安装 APK 为 CLASSPATH 经 app_process 运行；桥接检查对端身份。本机 VirtualDisplay 在特权进程直接调用 scrcpy wrappers。Binder 入口检查调用者并校验包名、输入帧和媒体白名单，不接受网络提供的任意 shell 命令。

协议与参数入口见[接口定义](../reference/api-control_protocol.md)。控制中的停止、CANCEL 和所有者死亡必须释放输入。全局滚轮还受[租约与锁屏规则](mechanism-global_scroll.md)限制。

## 不属于保证

首次启用 Shizuku 及其系统授权仍需用户配合；设备重启后特权失效可能需要重新激活，但不要求产品重新配对。无障碍、普通 APK、shell、Root 的能力不能互相推定。安全窗口研究不涉及 DRM 或受保护媒体。

权限审计的历史证据见[固定源码研究](../research/audit-runtime_permissions.md)，当前跨设备安全验证定义见[场景](../../validations/scenario-android_control/validation.md)。
