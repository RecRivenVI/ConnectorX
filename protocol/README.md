# 平台无关协议与算法

纯 Java 17 库，不引用 Android/Compose/Binder。

- Wire：现有认证连接内的控制帧/元数据编码与校验；返回仍是普通键，类型 100 预测手势已撤回。
- AppRequest：包名、尺寸、DPI、刷新请求、可选远端系统装饰；扩展通过 APP_OPTIONS 操作承载。
- TouchOrigins：逐指起点准入；Wire 允许 MOVE/UP 的有符号越界坐标，DOWN 限画面内部。
- ScreenMath、G2Corners：布局/坐标/自有圆角计算，不是物理屏幕曲率数据库。

当前操作包括 PAIR、APPS、APP/APP_OPTIONS、MIRROR、DEVICE、SESSION_INFO、GEOMETRY、RESTART_APP；网络分发仍在 app 的 ConnectorEngine。控制帧包含键/文本/触摸、flex resize 及 reset video。身份与 TLS 实现在 APK，不能把本模块称为完整独立网络服务。

测试：:protocol:test。对外协议稳定化、能力协商及版本升级策略属于后续开发。来源见 [架构](../documents/Architecture.md)。
