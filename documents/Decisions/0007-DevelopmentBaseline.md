# ADR 0007：以 0.3.13 建立正式开发结构

状态：已采用（结构整理，不新增产品功能）。

原型迭代后，特权实现混在 APK 源集、主包堆积 UI/连接代码、默认 debug 带设备探针，当前说明被历史撤回记录覆盖。

保留三个现有 Gradle 模块与组件身份：特权服务/AIDL/启动测试迁入 runtime；APK 按职责分包并抽离 Activity 中的页面；版本目录统一；探针移到 tools/diagnostics，以显式属性接入 debug。当前规范重写，历史交付和旧规范完整留存并修复相对链接。

不在整理阶段重写协议、身份、存储或 scrcpy；不引入尚未需要的 Go/Windows 工程。ConnectorEngine 与 RemoteSession 的进一步解耦另行测试驱动实施。此结构调整不等同于完成产品化验收。
