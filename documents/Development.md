# 正式开发顺序

当前先把 0.3.13 作为可追溯起点，不急于同时重做 UI 和底层。

1. **锁定回归基线**：两台设备按 Prototypes.md 重跑核心路径，补充身份/设置/快捷方式兼容与进程生命周期测试。
2. **拆分原型协调器**：ConnectorEngine 逐步分离发现、配对、会话；RemoteSession 分离解码与控制队列。先测行为，再替换实现。
3. **补齐协议与能力协商**：明确当前操作集、版本兼容、错误模型、权限能力与输入取消；不直接将 scrcpy 内部协议当作永久对外契约。
4. **单应用体验**：优先真实旋转闭环、IME、Display 配置与场景兼容，再完善 Compose + Miuix 产品 UI。
5. **后续能力**：设备端宏执行/编辑、公网传输、音频/文件/剪贴板与 Windows，按独立验收推进。

## 本次整理的验收

保持版本 0.3.13 / 18；组件名称、AIDL 事务、协议编码、偏好键、快捷方式格式和上游源码不变。仅归类源码/职责、移动特权代码与测试、集中构建版本、隔离诊断入口、整理文档。

检查 normal 与 diagnostics 构建、runtime/protocol 单元测试、lint、文档链接及 manifest；不因目录整理产生提交或覆盖安装。运行结果记录在本轮交付说明中。

### 2026-09-07 整理验收

- PASS：`tools/Check.ps1` 普通构建与 `-Diagnostics` 构建；runtime 3 项、protocol 30 项测试通过。
- PASS：两个 Android 模块 lint 无错误。app 12 条、runtime 62 条警告保留，后者包含上游代码和隐藏 API 用法，本轮未通过忽略规则掩盖。
- PASS：解析两份 APK 的 DEX class definitions，普通包不含四个探针入口，诊断包包含；稳定组件、PrivilegedRuntime 和 IRuntime 类名均存在。不能用 DEX 字符串是否出现代替类定义检查。
- PASS：迁移代码正文比对、Activity 页面拆分比对、Markdown 相对链接检查。设备探针额外增加 Android 13 入口检查/TargetApi 声明，不改变产品最低版本或控制流程。
- 未执行设备安装或端到端回归，未操作既有连接、未创建提交。

产物在 `D:\Workspaces\Artifacts\ConnectorX\DevelopmentBaseline`，有普通 debug、diagnostics APK 和 verification.json。普通包 SHA-256：`42ec59dfb5d672b1a2eeb27e183f3c599d1d02e3f2f5701153e092cf49baaa6b`；诊断包：`368780a71670fc52299d45409652eae85beb92f4eee6abf51d63cca98e214110`。

原始整理快照与移动清单：`D:\Workspaces\Scratches\ConnectorX\structure-before`；最终日志 structure-final-checks.txt、structure-diagnostics-final.txt。该快照只用于审计/恢复本轮改动，不是 Git 提交或新的开发来源。
