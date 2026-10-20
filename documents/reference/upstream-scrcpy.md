# scrcpy 来源与修改

应用直接编译 [RecRivenVI/scrcpy 子模块](../../third_party/scrcpy/CONNECTORX.md)中的 Java/AIDL 源码，没有内嵌 PC scrcpy 可执行文件。实际集成提交由父仓库 gitlink 唯一固定，server 版本由子模块 meson.build 提供。

官方基线为 [Genymobile/scrcpy 的固定提交](https://github.com/Genymobile/scrcpy/tree/19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac)。fork 的上游结构、包名、许可证和工作流保留，父项目不复制或重写该实现。

以下路径相对 third_party/scrcpy，完整差异可用该官方提交与当前 gitlink 对比。

| 文件 | ConnectorX 修改与理由 |
| --- | --- |
| server/src/main/java/com/genymobile/scrcpy/Server.java | ConnectorX 环境开关隔离音频失败，视频/控制继续；普通上游路径不受此开关以外的行为影响 |
| server/src/main/java/com/genymobile/scrcpy/device/DesktopConnection.java | 本地桥接 UID/PID 检查，避免别的进程接入控制 FD |
| server/src/main/java/com/genymobile/scrcpy/device/Device.java | 指定 Display 启动/强制停止返回实际结果，不误报成功 |
| server/src/main/java/com/genymobile/scrcpy/wrappers/ActivityManager.java | 为上述操作保留系统调用结果 |
| server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayManager.java | 创建真实 VD 时委托 hook，支持刷新率请求 |
| server/src/main/java/com/genymobile/scrcpy/video/NewDisplayCapture.java | 真实虚拟显示就绪后回调应用启动与元数据 |
| server/src/main/java/io/github/recrivenvi/connectorx/runtime/VirtualAppHooks.java | 请求刷新率、目标 Display 启动与确认、显示状态；无 ConnectorX 环境时保留通常路径 |
| server/src/main/java/io/github/recrivenvi/connectorx/runtime/LocalDisplayController.java | 本机 Surface 直连、定向多指/按键/滚轮、LOCAL IME 与生命周期；不依赖父协议源码 |
| server/src/main/java/io/github/recrivenvi/connectorx/runtime/LocalImeController.java | 按需 IMMS 快照、系统隐藏 IME、有界确认；不注入 Insets、不保存原始 dump |
| server/src/main/java/io/github/recrivenvi/connectorx/runtime/LocalImeBinding.java | 解析和核验目标 UID/Display 的输入绑定 |
| server/src/main/java/io/github/recrivenvi/connectorx/runtime/WheelTimeline.java | 单调绝对期限、迟到跳过及有界追赶，应用内与全局滚轮共用默认速度 |
| CONNECTORX.md | fork 自身的集成说明与归属 |

没有构建时补丁覆盖，也没有第二份 VirtualAppHooks。

特权服务与 TLS/身份/UI 留在父仓库。未来改 fork 须先独立提交和更新父 gitlink；未提交的子模块内容无法由 gitlink保存。只向 RecRivenVI fork 推送，不向 Genymobile 推送。

上游 [LICENSE](../../third_party/scrcpy/LICENSE) 和源码头保留。本机 IME/锁屏设计依据另见[本机机制](../design/mechanism-local_display.md)，请求刷新率不等于实际呈现帧率。
