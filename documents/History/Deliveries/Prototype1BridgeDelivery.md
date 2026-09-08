# 0.1.2 本地桥接修复交付

日期：2026-09-07。修改依据及设计见 [ADR 0003](../../Decisions/0003-BinderBridge.md)。

| 项目 | 结果 |
| --- | --- |
| `:app:assembleDebug` | PASS |
| `:app:testDebugUnitTest` | PASS，3 项启动等待回归测试 |
| `:protocol:test` | PASS，5 项协议测试，未变更部分复用通过结果 |
| `:app:lintDebug` | PASS，0 errors / 5 warnings |
| `192.168.0.13:43157` 覆盖安装 | PASS，versionCode 3 / 0.1.2-prototype1 |
| `192.168.0.101:33193` 覆盖安装 | PASS，versionCode 3 / 0.1.2-prototype1 |
| 两端配对数据 | PASS，更新前各 1 条，更新前后 identity.xml 摘要一致 |
| 新桥接的媒体、触摸及设备端回收 | pending，按用户要求不代为操作 |

APK SHA-256：`2C88CFE6BF43E633E245EF430567EA2695DD0FBC975E3355F576529DBA972F2C`。

APK、测试 XML 和 lint 文本保存到 `D:\Workspaces\Artifacts\ConnectorX\Prototype1\0.1.2`。没有清除应用数据、修改身份、截图、改动 SELinux 或创建提交。

下一步由用户重新打开两端应用，开启接收、启用 Shizuku，从已配对设备直接点远程控制。无需重新配对；启动期间显示连接中，失败会弹出具体原因。安装通过不代表新通道已完成真机验证。
