# 保留已建立会话

版本 0.3.10-retained-sessions / versionCode 15，2026-09-07。

## 当前规则

- “收起”只离开画面，不关连接、远端 Display 或应用任务。设备页“保留的连接”可恢复画面，另有明确的“断开连接”按钮。
- 会话放在 Application 级 ConnectorEngine.sessions 中。再次打开同设备、同包名/模式恢复原会话。不同设备可保留各自连接；同一被控设备仍受原型单会话限制，切其他应用/模式时提示，绝不自动关闭旧会话。
- 首次连接即开始读取视频，不依赖页面创建 Surface。已认证媒体流无读空闲超时；配对/初始握手及独立查询 RPC 仍有超时，不把短请求当作持久控制会话。
- Surface 生命周期只执行 attach/detach。无界面时 MediaCodec 输出到持续消费的 PRIVATE ImageReader，恢复界面时 setOutputSurface。切换失败或解码故障时只恢复解码器并请求 scrcpy RESET_VIDEO，不关闭网络；菜单提供“恢复视频”。
- 输入队列过载不关连接：丢弃积压，发送相应触点/键释放，等待恢复输入。不会无限堆积。
- 停止接收只停止监听和发现广播，不关闭已接受 socket。前台服务保持运行以承载现有连接。前台服务被销毁也不主动遍历关闭会话。
- 显式断开、撤销配对仍执行用户要求。实际 EOF/传输失败、无效协议数据仍清理已失效流。不能保证 Android 杀进程、强制停止/覆盖安装、网络物理失效之后原 TCP 会话存活。

本轮曾检测到 shennong:53208 ↔ popsicle:45873 的已建立连接，最初未覆盖安装；用户随后明确说“直接安装”，才更新两台设备。这次进程重启是显式更新操作，不是新版 UI 的自动断连策略。

## 验证

构建、33 项单元测试、lint 通过，0 errors / 12 warnings。新增 RESET_VIDEO 单字节控制帧往返测试及错误长度拒绝。日志 `D:\Workspaces\Scratches\ConnectorX\retained-sessions-final-checks.txt`。

shennong Android 16 上运行无 UI、无真实网络的 SessionRetentionProbe：本地 MediaCodec 编码 H.264，经管道输入实际 RemoteSession，初始离屏解码，然后附加 ImageReader 输出、移除、重新附加；各阶段 running=true、socket.closed=false、error=null，解码约 20fps。生产者第 9 秒暂停、保持管道打开，第 32 秒仍运行，验证超过 20 秒静止不主动关闭。最后只清理测试自己的会话。原始证据：`D:\Workspaces\Scratches\ConnectorX\session-retention-probe.txt`。

此测试证明本地媒体/流生命周期，不等于完整双机网络、SurfaceView/TextureView OEM 重绑、切后台和系统杀进程矩阵均通过；这些仍 pending。没有启动测试 UI、没有触碰真实远控会话来做模拟断网。没有提交。

两台设备安装并确认 versionCode=15 / 0.3.10-retained-sessions。APK SHA-256：`D7AF8131D8291ACF74D15F00B70B44C6E0AFF840182A366E3B15A5D4EA65AD3A`。
