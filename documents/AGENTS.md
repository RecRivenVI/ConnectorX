# 项目规范

本文件对整个仓库生效，补充根目录的[仓库规范](../AGENTS.md)，不放宽或改写它。

## 项目信息

| 项 | 值 |
| --- | --- |
| 产品显示名、标识与应用版本 | [应用构建配置](../app/build.gradle.kts)，不在其他配置重新定义 |
| 一句话 | 让已配对的 Android 设备成为本机的扩展应用运行环境 |
| 核心路径 | 首次双方确认配对 → 再次认证连接 → 打开远端应用或设备镜像 → 触摸控制 → 明确退出对应会话 |
| 技术栈附则 | Android；特权服务通过 Shizuku、Binder 和 scrcpy 派生运行时接入 |
| 支持的平台 | 只开发 Android ↔ Android；最低系统、SDK 取自[版本目录](../gradle/libs.versions.toml) |
| 设计语言与组件库 | Compose + Miuix，参照 MiuixDemo；主题移植依据见[来源记录](reference/upstream-mishka.md) |
| 测试框架 | JUnit、Robolectric、Compose UI Test；版本取自版本目录 |
| 文档主语言 | 简体中文 |
| 首页语言 | 简体中文 |
| 上游 | [scrcpy fork 与官方来源](reference/upstream-scrcpy.md)；[Mishka 移植](reference/upstream-mishka.md) |
| 常用测试设备或环境 | shennong；设备安装和操作仍须当前任务授权 |

## 技术栈

| 组成 | 版本 | 唯一来源 |
| --- | --- | --- |
| Kotlin、Compose、Miuix、AGP、Shizuku、测试依赖及共用 SDK | 不在本页抄写 | [版本目录](../gradle/libs.versions.toml) |
| Gradle | 不在本页抄写 | [Wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) |
| 编译 JDK 与字节码目标 | 不在本页抄写 | [根构建配置](../build.gradle.kts) |
| scrcpy server | 不在本页抄写 | 子模块 gitlink 与其 meson.build；接入见来源记录 |
| SDK 路径、个人工具路径与签名配置 | 不入库 | 根目录被忽略的 local.properties 或本地签名配置 |

## 补充规则

- **X-01** 保留已验证的 scrcpy 控制能力，不重写底层、不批量更名上游包。第三方子模块遵循 G-09；官方桌面源码、工作流和原许可证不因父仓库整理而改写。仅对本项目修改逐项登记。
- **X-02** applicationId、组件 MainActivity、ConnectorApp、HostService、PrivilegedRuntime、IRuntime 的完整类名，以及 AIDL 事务、偏好格式、快捷方式与配对格式保持兼容。模块迁移不授权改变这些标识。
- **X-03** 配对身份与地址、ADB、Shizuku 状态分离。权限失效不删除配对；未认证或未授权连接不得进入控制/媒体。shell 与 Root 跨 ROM 不等价，不据 UID 推断能力已完成。
- **X-04** 显式“退出”释放对应会话；后台、页面切换、Surface 重建、停止新接收不隐式结束既有会话。实际传输失败按故障处理，不能伪装为仍连接。
- **X-05** 触点按各自 DOWN 决定接管。画面外/本机系统栏起手不转发；画面内起手越界后继续 MOVE/UP，不钳制或提前抬起。CANCEL、断连及失效布局释放输入。
- **X-06** 独立应用的逐包名布局、远端系统装饰、强制重开边界见[交互规格](design/principle-interaction.md)。不恢复远端 Insets 代理、视频边缘模糊、远端预测返回或主动显示焦点覆盖。
- **X-07** 本机 VD 使用完整分辨率、Surface 直连和 LOCAL IME；宿主 FLAG_ALT_FOCUSABLE_IM 只在本机页生效。返回先检查并隐藏属于目标的键盘，再由后续返回注入 Back。显示、输入和兼容边界见[本机显示](design/mechanism-local_display.md)。
- **X-08** 网络视频使用有界压缩帧队列、MediaCodec callback、SurfaceView；Surface 不存在时保留远端会话、停止本地解码，恢复请求关键帧。不恢复轮询 codec、无界积压、后台全分辨率读回或 TextureView 视频管线。
- **X-09** 圆角是系统半径加自有 G2 造型，不宣称真实物理曲率。只有可信厂商圆心和框架宽度齐备才绘制并排除挖孔触摸，缺少信息不猜测。来源见[屏幕几何](design/mechanism-screen_geometry.md)。
- **X-10** 全局滚轮与本机/远控会话独立；锁屏、拖动开始、租约过期、所有者死亡停止，不自动续滚。六点停靠与手感以[悬浮控制规格](design/mechanism-global_scroll.md)为准；构建/组件测试不代替实机手感验收。
- **X-11** 媒体设置只影响新会话，音频失败不结束视频/控制。指标不得把 payload 吞吐、解码输出或相对积压标为实际刷新率或端到端延迟，见[媒体配置](reference/configuration-media.md)。
- **X-12** 当前只做 Android；Windows/Flutter、完整宏、公网、文件/完整剪贴板等未完成范围见[产品范围](project/overview-product.md)。FLAG_SECURE 仅为默认关闭的待研究能力，排除 DRM/保护缓冲，不创建假可用开关。
- **X-13** 安装不等于允许操作设备。截图如获授权，必须 exec-out 直接到工作区 Scratches，不在 Android 存图；不改系统设置或 SELinux。结果/隐私日志不入库。
- **X-14** 应用版本采用纯数字三段式、versionCode 递增；结构整理不自动升级应用版本。新协议需能力校验，不静默忽略高级参数。
- **X-15** 同包 Kotlin 文件不能生成重名 JVM facade；Android 设备适配入口仍为 AndroidDevicesAdapter.kt。已确认的稳定入口约束属于架构，不依赖自建发布门禁维持。

存量不符合项及暂不重写的原因见[技术债](project/debt-existing_code.md)，并非放宽规则。日常命令见[工作流](development/workflow-android.md)，当前状态见[进度](project/status-current.md)。
