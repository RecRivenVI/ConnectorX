# 存量代码与界面技术债

本轮界面重排保留底层协议与运行时。下表按用户可见风险排序，是未符合项登记，不是对根规范的豁免。

| 优先级 | 规则 | 位置 | 影响 | 建议与验证 |
| --- | --- | --- | --- | --- |
| 高 | C-03/C-07、AN-09 | app/.../connection/ConnectorEngine.kt；session/RemoteSession.kt | 连接编排、状态、媒体和输入所有权集中，异步失败难定位，前者超过约600行 | 先锁定认证/恢复/退出行为，再分离数据源与会话职责，保留连接生命周期 |
| 高 | C-02/C-09 | app/.../session/AndroidAudioPlayer.kt、RemoteSession.kt；core/privilege 隐藏接口；fork LocalImeController | 多处 runCatching/忽略异常和兼容分支，不能逐项证明所有 ROM 的回退合理；IME 存在焦点竞态 | 按具体失败保留必要清理，区分可恢复/结束/用户可操作错误，补 ROM 证据，不统一吞错 |
| 高 | U-04、AN-13/T-03 | 本机显示、媒体恢复、IME 与系统装饰路径 | 单元测试不证明跨厂商恢复、中文输入、音频/实际帧率可用 | 按 validations 与人工清单验收；当前 shennong 页面检查不能代替跨 ROM/双机矩阵 |
| 中 | AN-09/AN-10 | MainActivity.kt、ConnectorEngine.kt、LocalAppSession.kt、core/ui 页面 | 状态大量直接用 mutableStateOf，未普遍采用 ViewModel/StateFlow；部分组件缺预览、参数顺序不统一 | 小步提升状态与单向流，保留已验证的会话所有权和事件准入 |
| 中 | AN-03 | core/ui/src/commonMain 与 androidMain；core/privilege 编译 fork 源集 | Android-only UI 仍用 KMP/Compose Resources，非单一默认源码根；privilege 的额外源码来自独立上游 | UI 后续迁移默认 Android 资源前对比本地化与生成包；fork 源码按 G-09 保持原样，特权模块继续显式编译 |
| 中 | C-01/C-12、AN-08 | app/core 存量 Kotlin/Java 与 Gradle 文件 | 存在压缩单行、分号与混合格式；本环境没有 Android Studio，未执行其格式化器 | 用 Android Studio 默认格式化做独立变更，再运行现有测试；本轮只统一文本编码/换行，不声称已经格式化 |
| 中 | C-07/C-11 | core/ui/.../component/liquid/LiquidGlassNavigationBar.kt；ThemeSettingsScreen.kt | 前者超过约600行，后者长函数，但来自指定 Mishka 正文 | 保留来源；只在独立授权 UI 改造时按职责拆分，并对照固定源和文案 |
| 中 | U-01/AN-12 | app/.../ui/remote/LocalScrollControl.kt、overlay/OverlayControls.kt、core/ui 页面 | 用户确认的定制控件包含尺寸/颜色常量，尚未全部归入标准主题 tokens | 先保留手感与几何基线，再提取语义 token；不把现有用户设计当作标准 Miuix 控件 |
| 中 | U-03/U-06 | 未改动的特权提示、悬浮服务通知及少量错误来源 | 重排页面已改用资源、用户状态和诊断开关；其他既有页面/服务仍有硬编码文案 | 随相关功能维护迁移资源，不批量改写 ROM 原始错误与上游源码 |
| 中 | G-03/F-01、C-05 | PrivilegedRuntime.java 的自包保护字面量；Identity.kt 旧别名迁移；来源文件头 | 稳定包/协议标识在历史存储与源码重复，直接统一或删兼容分支可能破坏已有身份 | 构建版本、SDK、工具链已集中；剩余运行标识按真实迁移需求审计后由单源注入，不在结构整理中冒险改存储 |
| 低 | L-02/C-11 | core/ui/.../ui/util/WindowSize.kt 等保留来源包 | util 包未表达职责；包名批改可能扩大第三方差异 | 随独立组件调整迁往明确职责包，同时更新来源记录 |
| 低 | T-01/T-03、G-02 | core/protocol/.../DesktopControlsTest.java | 测试名称保留历史桌面词，实际仍校验 Android 输入帧；缺完整跨设备标准答案矩阵 | 后续按测试行为更名并增加端到端对照，不恢复桌面目标 |

## 本轮已消除的低风险项

- 移出全部一次性诊断源和临时 IME APK，删除 Gradle 的 connectorxDiagnostics 源集入口；未删除产品输入或单元测试。
- 撤下 Check.ps1、VerifyApk.py 聚合门禁，改用标准 Gradle 构建、测试和 lint；旧工具完整归档。
- 合并本机与全局滚轮默认速度来源为 fork WheelTimeline，数值与调度不变。
- SDK、依赖版本、编译 JDK 和字节码目标集中；server 版本读取 fork meson.build；产品标签由应用配置生成。
- 许可证和来源只维护 third_party/licenses 与 reference 清单，APK 资产由构建生成，不再维护旧副本。

没有为符合架构形式而重写业务、删除身份兼容或增添新测试。存量 lint 和测试框架弃用警告随相关维护处理；它们不代表运行通过或失败。整理后的 lint 还给出了依赖/构建工具升级提示；本轮没有升级版本或屏蔽检查，后续升级须独立验证。

## 当前界面开放项

- 配对安全保持原有两分钟窗口，没有提前关闭 API；“接受新的配对请求”开启后等待自动关闭，不能伪装成支持随时关闭的开关。
- 已配对设备 Tab 使用现有发现候选与会话状态点；发现候选没有“上次在线”或可靠过期数据，本轮不新增。离线说明不能作为认证失败判据。
- 本轮不使用模拟器，当前真机未覆盖深浅色切换、最大字号和宽屏；无权限/配对成功/配对拒绝等状态没有在用户设备上人为制造。
- 新远端预览、右侧客户端滚轮、触摸/坐标和退出需要双机真实验收；构建与单位测试不等于可用验收。
- Android Studio 默认格式化器不可用；新页面按 Kotlin 风格人工整理和 LF 统一，未声称执行 IDE 格式化。现有 UI 模型/回调和预览债务继续保留，不为本轮重写成新的状态架构。

- 新页面尚未补齐 IDE 预览（AN-10）；本轮用授权真机检查可达状态，未验证的显示变体单独列出。ADB 搜索键盘返回后的焦点/前台行为需人工手势复测，不能据合成按键推定所有 ROM 兼容。
