# Windows 控制端 · 0.6.0

Windows x64 以与 Android 控制端并行的正式开发模块维护，共享前端/协议，平台权限和视频实现独立。Windows 不允许被远程控制。

## 使用

1. 启动 `D:/Workspaces/Artifacts/ConnectorX/Windows-x64/ConnectorX.exe`，或运行 tools/StartWindows.ps1。
2. 手机启用 ConnectorX 接收/Shizuku，开放新配对；电脑选择附近手机或输入 IP，双方核对校验码。也可以从手机主动配对电脑。
3. 电脑设备卡片提供远程控制、远程应用、实体设备镜像。手机的 Windows 卡片支持主动镜像/投送应用，电脑接受后回连。
4. 远控窗口的会话设置可读取 Display 状态，保存逐包名参数、开关远端系统栏或跟随窗口，强制重启目标应用及恢复解码。
5. 远程应用目录可添加桌面快捷方式；快捷方式通过本机记录找到仍已配对的手机。已有同设备会话会恢复，不抢占其他模式。

局域网 TCP 45873；自动发现 mDNS。不修改系统防火墙设置。普通使用不需要 ADB。
Unicode 提交粘贴及原生滚轮需要新版 Android 0.6.0 APK 的能力协商；旧版保留基础控制并拒绝不支持的粘贴，不因此断连。本轮 APK 已构建，未代用户安装。

## 与 Android 当前控制能力的对应

| 能力 | Windows 实现 | 验收状态 |
| --- | --- | --- |
| 身份/持久配对/角色 | P-256、DPAPI、TLS 双向固定指纹；Windows 始终仅控制方 | 本地 TLS/重载测试 PASS；双机重连 pending |
| 主页/主题/导航 | 同一 shared/ui 模块、Mishka 外观与主题 | 构建 PASS；用户视觉验收 pending |
| 应用配置/重启 | 包名隔离存储，APP_OPTIONS、SESSION_INFO、RESTART_APP，保留 sessionId | 持久化/协议测试 PASS；真机重启 pending |
| 窗口匹配 | 按屏幕缩放设初始 DPI/刷新率，防抖 resize，尺寸切换取消当前输入 | 实现；OEM 实际显示/刷新 pending |
| 设备镜像 | DEVICE 元数据、G2 曲线、灰色外边框，原比例居中 | 轮廓/旋转数学测试 PASS；视觉 pending |
| 输入 | 鼠标、多指 WM_TOUCH、修饰键、IME committed text、显式文本粘贴 | 坐标/队列/控制帧测试 PASS；真实触摸与输入法 pending |
| 解码恢复 | 仅重建解码子进程，继续持有网络/VD | 合成 H.264 重建/持续会话 PASS |
| 后台/退出 | 最小化和视图重建保留；显式退出关闭对应连接 | 生命周期路径实现；长时间后台 pending |
| 快捷方式 | 本机 opaque ID + 配对校验 + 已运行实例转交 | 单实例转交测试 PASS；桌面点击 pending |
| 手机主动投送 | 认证 CAST，接受后回连，已有不同模式不被抢占 | 实现；双机用户流程 pending |

Windows 无手机通知栏/导航栏，不照搬它们的下沉选项；窗口内容保持在本机系统 UI 可用区。G2 是系统半径的自有连续曲率近似，未恢复摄像头蒙版。flex resize 不等于传感器旋转闭环。

## 证据与边界

本轮只做构建、自动测试和启动，不代用户操作界面、配对或 Android 设备。不能把“源码能力补齐”写成所有 OEM/硬件均已验证。
检查入口 tools/Check.ps1；日志 `D:/Workspaces/Scratches/ConnectorX/windows-parity-checks.txt`。测试包括实际本机 FFmpeg 生成/解码的合成 H.264 流、解码器重建保留 session、越界坐标、多触点和按键积压释放、角色/粘贴/滚轮校验、DPAPI、TLS、逐包名配置和单实例 IPC。

音频、宏、文件、持续剪贴板、公网、DRM/FLAG_SECURE 支持、真实旋转闭环没有借本轮提前宣称完成，仍按产品基线处理。

原生输入依据：[Windows Touch](https://learn.microsoft.com/en-us/windows/win32/wintouch/getting-started-with-multi-touch-messages)、[Java IME committed text](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/event/InputMethodEvent.html)。

## 本轮交付记录

2026-09-08：完整 Check.ps1 PASS，共 51 项测试（protocol 39、runtime 3、Windows 9），app/runtime lint 通过。最终窗口尺寸调整另完成 Windows 打包检查。已启动 0.6.0，读取进程确认窗口响应和 TCP 45873 监听；没有点击、配对、捕获真实画面或操作设备。

Android 配套产物：D:/Workspaces/Artifacts/ConnectorX/ConnectorX-0.6.0-debug.apk，SHA-256 F660484F196BD711154F98E9280A34A9301DEBE1ED5C3C244F63B8EF98AAD510。APK 未安装。Windows 产物保持 D:/Workspaces/Artifacts/ConnectorX/Windows-x64。未创建提交或推送 Git。
