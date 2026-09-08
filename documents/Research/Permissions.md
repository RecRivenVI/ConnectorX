# 权限与 runtime 研究入口

状态：固定源码初步审计已完成，完整设备能力矩阵 **pending**。本轮仅安装、不替用户授权或操作。

## 原型 1 源码事实（2026-09-07）

基线 `19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac`，源码位于 `scrcpy/`：

- `app/src/server.c`：PC 侧 server 推送、进程启动和 ADB 通道协调。
- `Server.java` 的 `main/internalMain/scrcpy`：解析参数、准备主 Looper、创建 DesktopConnection、启动 Controller/SurfaceEncoder，结束时 System.exit。`dropRootPrivileges` 在 UID 0 时尝试切到 2000；因此 Root 启动不等于 server 一直以 UID 0 运行。
- `device/DesktopConnection.java` 的 `open/connect`：按 scid 建立 Unix socket，反向模式先连视频再连控制。ConnectorX 复用该模式并追加对端 UID 检查。
- `control/ControlMessageReader.java` / `Controller.java`：输入帧解析、多指状态及坐标映射；`device/Device.java` / `wrappers/InputManager.java` 负责系统输入注入调用。
- `video/ScreenCapture.java` / `SurfaceEncoder.java`：显示捕获、MediaCodec；`device/Streamer.java` 发送 codec ID，再以 session 消息报告尺寸，config 标志位 62、keyframe 位 61。首版 decoder 依据此版本处理，未使用旧版固定 12-byte 初始头假设。
- `CleanUp` 可创建清理进程；原型使用 cleanup=false，不让嵌入式 server 删除其 APK 路径或恢复本版未修改的设备设置，生命周期由 APK 与 UserService 管理。

当前 ADB 只读预检：两台设备 shell UID 2000 / `u:r:shell:s0`，Android 17 API 37 与 Android 16 API 36，均发现运行中的 `shizuku_server`。`command -v su` 未返回路径，未尝试提权。此证据不证明 ConnectorX UserService、捕获或注入已在真机通过。

Shizuku UserService 官方文档允许在 shell/root 身份的独立进程运行 Java；其 Context 不等同普通应用 Context。原型只用它的 APK/UID 信息和 ProcessBuilder，未假定其能承载完整 Android Service 生命周期。采用方式见 [ADR 0002](../Decisions/0002-Prototype1.md)。

官方文档说明：PC 客户端推送 server，以 shell 身份通过 app_process 执行；server 使用 Android 隐藏接口，输入注入经 InputManager。该事实不证明 APK 内调用或其他启动方式具有等同能力。来源见 [固定基线开发文档](https://github.com/Genymobile/scrcpy/blob/19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac/doc/develop.md)。

## 待追踪问题

1. ADB 设备授权、push 路径/文件权限、forward/reverse、CLASSPATH 与 app_process 启动参数分别承担什么职责？
2. server 实际 UID/GID、SELinux 域、Binder 调用身份是什么？捕获、输入、虚拟显示、启动应用各有什么检查？
3. Shizuku 的 Binder 调用与独立进程启动方式如何承载完整 server？Root 下 Android framework 上下文及 ROM 限制如何影响能力？
4. 哪些运行环境仍需要首次无线调试或 USB 激活？设备重启、授权撤销后如何恢复？
5. 上游单会话退出/清理行为如何改造为长期服务？如何隔离密钥、网络解析与特权输入接口？

## 待填写矩阵

| 启动方式 | 实际身份 / SELinux | 输入 | 捕获 | 虚拟显示 / 旋转 | 指定显示启动 App | 重启恢复 |
| --- | --- | --- | --- | --- | --- | --- |
| 官方 ADB shell | pending 实测 | pending | pending | pending | pending | pending |
| Shizuku（ADB 激活） | pending | pending | pending | pending | pending | pending |
| Shizuku（Root 激活） | pending | pending | pending | pending | pending | pending |
| Root 直接启动 | pending | pending | pending | pending | pending | pending |
| 普通 APK 对照 | pending | pending | pending | pending | pending | pending |

每个单元必须附 Android API/ROM、设备、源码符号、调用结果和证据；不要把整个启动方式简单标成“权限足够”。普通 APK 对照用于界定公开 API 能力，不能默认替代特权 runtime。
