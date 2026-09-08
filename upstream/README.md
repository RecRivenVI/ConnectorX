# scrcpy fork 子模块

`scrcpy` 是 Git Submodule，来源 https://github.com/RecRivenVI/scrcpy.git；官方 upstream 为 https://github.com/Genymobile/scrcpy.git。
当前 gitlink 固定 fork 提交 `f8c94c3a92bc04bf4fbe4ab9a74d2bef9a41722a`（Update），其官方基线为 `19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac`，server 版本 4.1。来源记录见 [scrcpy.lock.json](scrcpy.lock.json)。

2026-09-08 将此前内嵌的 110 个文件迁为完整 fork 子模块，原有修改与配套 VirtualAppHooks 已随 Update 提交并推送到 fork。

新检出运行 `git submodule update --init --recursive` 即可取得本项目固定的源码与修改。后续在子模块工作区维护源码，先提交/推送 fork，再更新父仓库 gitlink；仍须获得用户相应授权。

runtime/Android/build.gradle.kts 直接编译根目录 scrcpy/server/src/main 的 Java/AIDL。完整桌面源码 app/ 也在子模块内，Windows 渲染优化仍暂停。

外部 `D:/Workspaces/Repositories/GitHub/RecRivenVI/scrcpy` 是另一个独立 checkout，不与此工作区自动同步。

## 本地修改

上游固定提交上的修改共 6 个文件，其余原有文件保留原内容，并新增配套 VirtualAppHooks：

- `Server.java`：仅当 ConnectorX runtime 设置 CONNECTORX_AUDIO_OPTIONAL 时，音频处理器结束只关闭音频 socket，不让音频失败终止视频/控制。原版无此环境变量时行为不变。
- `device/DesktopConnection.java`：校验 `CONNECTORX_BRIDGE_UID/PID`，对端必须是启动它的 UserService。
- `wrappers/DisplayManager.java`：虚拟显示创建委托 ConnectorX hook，以便请求刷新率。
- `video/NewDisplayCapture.java`：显示与输入映射建立后调用 hook，启动目标 App 并报告实际 Display 元数据。
- `device/Device.java`：沿用原启动流程，向调用者返回 Activity 启动结果；0.3.9 强制停止失败时终止启动并返回 -1。
- `wrappers/ActivityManager.java`：启动反射失败返回 -1；0.3.9 forceStopPackage 返回成功/失败，避免把未强制停止的普通启动当成重启成功。

ConnectorX hook 位于 `scrcpy/server/src/main/java/io/github/recrivenvi/connectorx/runtime/VirtualAppHooks.java`，原型 2 细节见 [ADR 0004](../documents/Decisions/0004-VirtualApplications.md)。环境参数由经过授权的本地 UserService 校验后设置，不接受任意命令。

ConnectorX 将 server 编入 APK，由 Shizuku UserService 使用 `CLASSPATH=<已安装 APK> app_process` 创建独立子进程。0.1.2 起监听 socket 位于 UserService 的 shell 权限域；server 在同域连接，已连接描述符通过 Binder 传给 APK。APK 完成 TLS 认证与授权后才请求启动。未开放 scrcpy 明文 TCP 端口。

许可证见 [scrcpy/LICENSE](../scrcpy/LICENSE)。保留上游版权与包名；ConnectorX 自有代码整体许可证尚未选定。升级必须复核协议和权限，不能覆盖本地修改来源。

研究见 [权限记录](../documents/Research/Permissions.md)，接入决策见 [ADR 0002](../documents/Decisions/0002-Prototype1.md)。

子模块统一放在父仓库根目录，upstream/ 仅保存来源与集成说明。
