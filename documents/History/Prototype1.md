# 原型 1：安装与操作

原型 1 独立交付版本 `0.1.2-prototype1`，后续能力合入同一应用 `0.2.0-prototype2`，包名 `io.github.recrivenvi.connectorx`。用户已确认原型 1 画面、操控、多指触摸均正常；音频未接入。历史安装见 [0.1.2 交付记录](Deliveries/Prototype1BridgeDelivery.md)，新功能见 [原型 2](Prototype2.md)。

初版交付见 [交付记录](Deliveries/Prototype1Delivery.md)。用户实测后发现 TLS 密钥摘要授权错误，修正版见 [Read error 排查](../Research/TlsReadError.md)；本轮构建与检查通过，修复后的双端交互等待用户重试。

0.1.1 的用户操作日志已确认双方持久配对成功。随后本地 Unix socket 被 SELinux 拒绝；0.1.2 改成同 shell 域连接及 Binder 描述符桥接，详见 [ADR 0003](../Decisions/0003-BinderBridge.md)。此次不修改身份或配对记录，更新后重新打开应用、开启接收、启用 Shizuku，直接点已配对设备的远程控制，无需解除配对。按钮显示连接中，启动失败会弹出原因。

## 操作

1. 两台设备连接同一 Wi-Fi，打开 ConnectorX，分别点 **开启接收**。通知权限如弹出，请允许。
2. 被控端点 **启用 Shizuku** 并允许权限，等待“权限已就绪”。两台都要被控制时，两台都做。
3. 被配对端点 **允许新配对**，入口有效两分钟。
4. 另一端点 **刷新发现** 并点击附近设备；找不到时手动输入对方 IP，再点 **发起配对**。
5. 核对两端显示的六段校验码完全一致，**两端都点“一致，允许配对”**；不一致请取消。
6. 控制端在“已配对设备”下点 **远程控制**。被控端保持接收开启、Shizuku 就绪；可以用本机 Home 返回桌面，再由另一端控制。
7. 视频区域支持触摸/滑动；本机返回映射远端 Back，本机 Home 保留本机行为。控制页“桌面”是远端 Home，“断开”回到 ConnectorX。

| 设备 | 应用内手动 IP | 电脑安装用无线调试地址 |
| --- | --- | --- |
| 2509FPN0BC / popsicle | `192.168.0.13` | `192.168.0.13:43157` |
| 23116PN5BC / shennong | `192.168.0.101` | `192.168.0.101:33193` |

应用内不填无线调试端口，使用独立 TLS 端口 45873。IP 变化后通过发现或手动输入新地址连接，仍须匹配已保存的身份。

## 验证持久关系

首次配对后断开、关闭/重开应用、开启接收，再从已配对列表连接；不再开放新配对。反向控制交换角色即可。停止接收不删除配对。Shizuku 停止/设备重启后可能需要恢复 Shizuku，但配对应保留；清除应用数据会删除身份，不能用于测试普通重连。

解除配对本地撤销授权。离线对端可能仍显示旧记录，旧授权不得连入；再次连接应重新确认。

## 范围与限制

- 完整主屏视频与触摸，最长边 1600、H.264、编码上限 60fps、6Mbps；尚无实测延迟指标。
- UI 为 Compose + Miuix，参考 Demo 分组卡片、标题和弹窗；视觉与双设备适配待用户确认。
- 文本先测英文/数字；完整中文输入法、物理键盘尚未验收。
- 自动发现取决于网络/NSD；有手动 IP 入口。VPN、AP 隔离或系统局域网限制可能导致连接失败。
- 黑屏/不能触摸时先确认被控端解锁且权限已就绪，查看两端状态错误；安全内容可能无法捕获。
- Shizuku 子进程、TLS、画面、触摸、多指、旋转、断连恢复在此 APK 上均尚未实机交互验收。
- Root 直接入口、单应用虚拟显示、实体圆角、音频、剪贴板及宏不属于本版完成范围。

## 构建与参考

Gradle 9.7.1、AGP 9.3.2、Compose 编译插件 2.4.10；应用 compileSdk 37 / targetSdk 36 / minSdk 29，runtime compileSdk 36。0.1.2 被控端启动要求 Android 11+（使用非阻塞 fcntl/poll），低版本仅保留控制端候选能力。minSdk 是安装声明，不是低版本兼容性验收结论。Miuix `0.9.4-rc01` 为预发布版本，Shizuku API/provider `13.1.5`。

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :protocol:test :app:lintDebug
```

APK：`apps/Android/build/outputs/apk/debug/app-debug.apk`。电脑端项目临时目录与未来截图：`D:\Workspaces\Scratches\ConnectorX`。禁止截图先写 Android 再拉取。安装命令只用 `adb -s <地址> install -r <apk>`，不包含启动或授权。

MiuixDemo 本地参考提交 `7cc339c28616708b1e417224a8e9a7e05f32f253`：`example/shared/src/commonMain/kotlin/App.kt`、`example/android/src/main/kotlin/top/yukonga/miuix/uitest/MainActivity.kt` 与 `docs/demo/src/commonMain/kotlin/` 下 Scaffold/Card/BasicComponent/WindowDialog 示例。沿用组件与页面结构，没有复制 Demo 页面代码。

权限启动参考 [Shizuku 官方 API](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md) 的 UserService 和授权说明；scrcpy 来源见 [上游记录](../../upstream/README.md)。
