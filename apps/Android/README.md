# Android 应用

Compose + Miuix APK，applicationId / namespace io.github.recrivenvi.connectorx。稳定组件保留根包；其余按 connection、runtimeclient、session、input、display、preferences、shortcuts、ui 分组。

特权实现/AIDL 已移至 runtime/Android，本模块只保留权限与 FD 客户端。默认 debug 不包含实验 Activity，探针须显式 -PconnectorxDiagnostics=true。源码地图见 [Architecture](../../documents/Architecture.md)，当前行为见 [CurrentState](../../documents/CurrentState.md)。
