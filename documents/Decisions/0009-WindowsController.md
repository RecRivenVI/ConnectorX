# ADR 0009：Windows x64 仅作为控制端

2026-09-08。Windows 纳入当前开发范围；Windows 被控方向明确不开发。

## 角色与配对

沿用 ConnectorX/1、端口 45873、TLS 1.3 双向设备证书与 96 位校验码。
增加 PAIR2：名称之后交换 DeviceProfile（platform、canHost、canReceiveCast），双端确认后与证书指纹一起持久化。
Android 为 android/true/false；Windows 为 windows/false/true。拒绝 Windows 声明 canHost=true。
发现 TXT 只用于找地址，不能覆盖已认证角色或代替配对。PROFILE 为已配对设备的认证后查询。
旧 Android 配对记录按历史 Android 角色读取；服务端保留旧 PAIR 读取。新客户端主动 PAIR2 需要对端升级，已有配对的旧媒体操作保持不变。

Windows 卡片在 Android 不展示远程控制/远程应用/设备镜像入口，改为镜像到此电脑、投送本机应用。
Android 控制入口也检查 canHost；Windows 服务端不实现任何本机控制或捕获操作，未知操作明确拒绝。

## 主动投送

Android 用户选择已配对 Windows，发送 CAST + 包名（空字符串表示本机镜像）。
Windows 认证来源并确认用户接受后，向该手机建立新的认证连接，复用 MIRROR / APP。
回连验证同一手机证书；不由请求提供任意目标 IP。手机须开启接收并已连接 Shizuku。
已有会话被保留并重新显示，不用新投送抢占其他模式/应用。拒绝投送不创建 Display。

## 实现选择

桌面 UI 使用 CMP + Miuix，共享纯 Java protocol；无需 Go 重写现有 JVM 协议。
Windows 私钥通过当前用户 DPAPI 保护，保存在 LocalAppData/ConnectorX；不使用 ADB 配对文件。
FFmpeg 作为本机子进程通过匿名管道解码 H.264，无媒体明文监听端口，无磁盘截图中转。
首轮展示使用 BMP 管道，后续优化为原生解码 Surface；不把该路径宣称为零复制或硬件解码。

## 验收

参见 [Windows 开发说明](../Windows.md)。构建/协议测试与实机端到端分别记录。
