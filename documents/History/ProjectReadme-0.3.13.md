> 历史快照：不作为当前实现规范。当前入口见 [文档索引](../README.md)。

# ConnectorX

当前版本 **0.3.13-standard-back**：已按用户要求撤回整个预测性返回功能，恢复普通返回键映射。下方预测性返回版本说明仅为历史；其他功能保留。

最新修复 **0.3.12-back-a17**：适配 popsicle Android 17 的 BackMotionEvent 构造函数变化，保留 Android 16 与旧签名兼容。

最新预览 **0.3.11-predictive-back**：独立应用接入远端预测性返回，支持进度、取消、确认及焦点校验；详见 [接入说明](PredictiveBack.md)。

最新预览 **0.3.10-retained-sessions**：收起画面保留连接，设备页可恢复；移除 Surface/空闲/替换会话引起的自动断连。见 [会话保留](RetainedSessions.md)。

最新预览 **0.3.9-app-options**：独立应用菜单增加强制重启和按包名保存的远端系统栏开关，详见 [布局与触摸](AppLayoutTouch.md)。

最新版本 **0.3.8-app-layout**：已移除下面历史版本的模糊方案，恢复最低 Android 10；新增按包名保存的下沉布局及统一触摸起点规则。详见 [布局与触摸](AppLayoutTouch.md)。

让远程设备成为本机的扩展运行环境：设备明确配对一次，以后像打开本地应用一样打开远程 Android 应用，并可随时进入完整设备镜像。

- 应用名称：**ConnectorX**。
- Android applicationId / namespace：**`io.github.recrivenvi.connectorx`**。
- 当前阶段：原型 1 核心控制已获用户确认。独立应用模式保持系统栏内虚拟显示尺寸与远端无系统装饰，0.3.6 顶/底栏改用边缘像素渐变模糊。暂停远端自定义 Insets/padding 代理。原型 3 保留系统参数驱动的自有 G2 连续圆角，挖孔遮罩暂时关闭。
- 首轮方向：Android → Android；Compose + Miuix 前端，基于官方 scrcpy 的特权 runtime。
- 最新预览 0.3.7：横屏画面忽略挖孔预留；系统栏边缘延展使用 Miuix curve=3.0 渐变模糊，取样跟随 VSYNC；启动自动连接 Shizuku、开启接收。使用官方 blur 模块，APK 最低 Android 13。细节见原型 2 文档。
- 后续：同一产品内的触摸宏、完整 UI/UX、公网连接，以及待定的 Windows 扩展。独立网络服务如有必要优先 Go。

## 阅读入口

1. [产品目标](../Product.md)：需求基线与首轮范围。
2. [架构与模块](../Architecture.md)：进程、模块、权限与传输边界。
3. [权限研究](../Research/Permissions.md)：先研究权限，再确定 runtime。
4. [持久配对设计](../Pairing.md)：身份、授权、认证和失效行为。
5. [交互与屏幕几何](../Experience.md)：远程应用、镜像、系统栏与裁切。
6. [原型与验收](../Prototypes.md)：P0 前置研究及 P1 → P2 → P3。
7. [研究记录模板](../Research/EvidenceTemplate.md)：保存可复核的设备证据。
8. [初始架构决策](../Decisions/0001-Foundation.md)：已确定与待定事项。
9. [scrcpy 官方基线](../../upstream/README.md)：来源、固定提交与复用流程。
10. [原型 1 操作说明](Prototype1.md)：构建、安装、配对与远程控制。
11. [原型 2 操作说明](Prototype2.md)：远程应用、虚拟显示、旋转和桌面入口。
12. [原型 3 操作说明](Prototype3.md)：真实蒙版、灰色外框与几何降级。
13. [独立应用显示语义实验](../Research/DisplaySemantics.md)：shennong 的真实旋转、Insets 阻碍及全屏请求证据。
14. [远端预测性返回实验](../Research/PredictiveBack.md)：shennong 上应用内开始/进度/取消/确认通过，尚未接入产品。

## 目录

```text
apps/Android/          Compose + Miuix 应用、TLS 配对与局域网桥接
runtime/Android/       scrcpy Android library 构建入口
protocol/              独立 Java 控制帧编码、校验与测试
documents/             产品、架构、研究、验收与决策
upstream/scrcpy/       固定官方 server 源码与来源资料
gradle/wrapper/        Gradle Wrapper
```

根目录文件夹采用小写命名，文档目录使用 `documents`；源集与上游文件沿用生态命名。Windows、宏编辑器与独立网络服务暂不创建空项目。

构建与检查：`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :protocol:test :app:lintDebug`。Android SDK 路径写入不提交的 `local.properties`。APK 位于 `apps/Android/build/outputs/apk/debug/app-debug.apk`；依赖与限制见原型操作说明。

## 开发顺序

首先按固定 scrcpy 提交研究启动、捕获、输入和虚拟显示链路，记录权限及兼容矩阵；之后实现持久配对原型，再验证远程应用与实体镜像。所有验收项初始为 **pending**，静态文档或编译成功不等于真实设备验证通过。

原始产品意图以本仓库产品文档为基线；修改目录时同步更新本文件及 [AGENTS.md](../../AGENTS.md)。本仓库的许可证尚未选定；未来引入的上游代码必须保留对应许可证及归属说明。
