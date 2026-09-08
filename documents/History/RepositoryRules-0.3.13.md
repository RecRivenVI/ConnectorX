> 历史快照：不作为当前实现规范。当前入口见 [文档索引](../README.md)。

# ConnectorX 仓库约定

## 产品边界

- 名称固定为 ConnectorX，Android applicationId / namespace 固定为 `io.github.recrivenvi.connectorx`。不要批量更名 scrcpy 自有包路径。
- 先阅读 `README.md`、`documents/Product.md`、`documents/Architecture.md`、`documents/Prototypes.md` 和 `upstream/README.md`。
- 当前包含原型 1–3 代码。原型 1 画面/操控/多指已获用户确认，原型 2 整体符合预期，但通知栏/导航栏覆盖策略待定，不擅自定稿。原型 3 几何参数可只读获取，视觉与交互仍由用户验收。
- 第一优先级是局域网持久配对及特权 runtime，之后为远程单应用和完整设备镜像。触摸宏合并在同一产品内，排在后续。
- Android UI 使用 Compose + Miuix。优先复用官方 scrcpy，不用 Go 重写 Android 底层。Windows 与独立 Go 服务仅保留架构边界。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| `apps/Android/` | 普通 APK、设备发现、配对交互、解码显示、输入与快捷方式 |
| `runtime/Android/` | 特权入口、scrcpy 适配、显示/捕获/输入、未来宏执行 |
| `protocol/` | 身份认证、会话、能力协商、控制与媒体协议设计 |
| `documents/` | 产品目标、架构、交互、原型验收、研究和决策 |
| `upstream/` | 官方源码版本锁定、来源及集成说明 |
| `gradle/` | Gradle Wrapper 构建基础设施 |

根目录文件夹按用户指定的 Mine-Craft-Protocol 风格采用小写命名，文档目录使用 `documents`；此约定优先于上层工作区的组织目录大驼峰默认规则。子目录和源集沿用各自约定。

改动目录结构时同时更新本表和 README。生成物、探针输出遵循上层工作区的 Artifacts / Scratches 约定，不提交 APK、录屏、密钥或设备隐私日志。

## 实施要求

- 0.3.13 最新决定优先：整个撤回预测性返回，恢复独立应用 BackHandler → KEYCODE_BACK。预测手势协议、Binder 入口、runtime/兼容工厂与专用探针/测试均移除；以下 0.3.11/0.3.12 说明仅为历史。保留布局持久化、重启按钮、远端系统栏开关、触摸起点规则及连接保留。

- 0.3.12：BackMotionEvent 按实机签名选择构造函数，兼容 Android 17 的六参带时间戳形式（无 RemoteAnimationTarget）、Android 16 七参形式及旧六参形式。构造失败发生在开始回调之前时允许普通返回降级，不能静默吞掉返回；已发出开始回调后仍以取消清理避免重复返回。

- 0.3.11：独立应用模式接入 PredictiveBackHandler，使用已认证控制连接发送独立返回手势协议，不作为普通触摸转发。runtime 严格核对 display/package/task 焦点；应用内回调可驱动预览，其他可降级情况只在确认后发送一次 BACK，取消不返回。焦点不符拒绝，不抢占主屏。镜像仍用普通返回；跨 Activity/回桌面预测动画未接入。

- 0.3.10：已建立控制/镜像会话不因收起界面、Surface 销毁/重建、切后台、空闲超时、新会话替换或输入积压自动关闭。收起后在设备页恢复；同设备已有不同模式/应用会话时不得自动抢占，提示用户手动处理。停止接收仅停止新连接。显式断开、撤销配对，以及实际传输终止/协议损坏仍清理资源。安装更新会重启进程，存在连接时不擅自覆盖；本轮用户已明确授权直接安装。

- 0.3.9：独立应用菜单有强制重启按钮，只允许当前配对会话的当前目标包，保留 VD；强制停止失败不能报成功。新增按包名保存的远端 system decorations 开关，默认关闭，下次新建会话生效，不与本机系统栏下沉设置混淆。远端 ROM 决定实际生成哪些系统栏。

- 0.3.8 最新规则优先于下方历史说明：移除全部边缘模糊/采样及 blur 依赖，最低 Android 10；控制端按包名保存通知栏/导航栏下沉选项，默认均关闭，横屏仍忽略 cutout。系统栏起手永不控制画面；独立应用及镜像逐触点按 DOWN 判断命中，外部起手不能中途加入，内部起手越界后继续未钳制的 MOVE/UP，不因越界抬起。系统 CANCEL、布局失效和断连仍释放触点。保留启动自动接收与 Shizuku 自动连接。

- 研究固定提交，引用具体路径/符号；引入或修改上游代码时记录来源提交、变更理由与许可证归属。
- 不假设普通 APK、Shizuku shell 或 Root 在所有 ROM 上等价；实测 UID、SELinux 域、系统版本及能力结果。
- 产品身份与 ADB 身份分离；权限失效不删除配对；未认证连接不得进入输入、启动应用或媒体通道。
- 独立应用模式按用户最新决定：暂停远端 padding / Insets 代理，远端虚拟显示尺寸直接采用本机系统栏以内的可用区域；系统栏区域暂为纯黑，本机栏保持可见。只在本机读取栏/cutout 边界确定可用区域，不在远端注入 Insets。全屏跟随策略暂不接入。普通管理页及完整镜像不因此改变。
- 0.3.4 开启远端系统装饰的对比实验已按用户要求撤回；0.3.5 恢复 vd_system_decorations=false，保留此前可用区域尺寸及本机黑色栏区域。
- 0.3.7 最新修正：横屏画面与初始 VD 尺寸忽略 cutout 预留，只避让系统栏；模糊区域边界不变。Miuix progressive curve=3.0，边缘取样由 Choreographer 每个显示帧驱动，取消固定 50ms 间隔；PixelCopy 在途时不堆积副本，不将调度频率当作实测完成帧率。
- 0.3.6 用户更新：可用画面尺寸不变，顶/底系统栏改为对应视频最外一行像素沿竖向延展，叠加 Miuix progressive blur，靠画面 1dp、外侧 10dp。首帧之前及左右保留黑色。直接使用 miuix-blur 0.9.4-rc01，此预览 APK 最低 Android 13。启动 MainActivity 自动连接已有 Shizuku 并开启接收；授权未授予时请求授权，Shizuku 服务本身未启动时仍需用户启动。持久配对认证不取消。
- 独立应用旋转以控制端传感器为意图、远端实际 Display 状态为权威，再驱动控制端 Activity；方案尚未接入产品。可行性操作测试仅使用 shennong，不创建提交。
- 不把 Surface 拉伸当作真实 Display 旋转，不把视频 FPS 上限当作显示刷新率，不把近似圆角当作真实几何。
- 当前用户选择：设备镜像圆角使用系统四角半径参数 + 自有 G2 曲线，暂时禁用挖孔绘制及挖孔触摸排除。以后挖孔仍需具体贴图，不通过圆角算法或矩形预留区推测。此决定取代此前圆角保留 PNG 的方案。
- 实时输入与未来宏共用设备端 Input Engine；宏按单调时钟调度。
- 验证按 pending / PASS / degraded / FAIL / deferred 明确记录。PASS 必须附证据和适用设备范围。
- 当前工作不隐含提交或推送授权；保留无关用户改动。
- 用户明确要求前，不创建任何提交。
- 用户允许为原型 3 只读获取两台设备参数和系统文件，必要时可用 Root Shell 只读获取。不修改设备设置或 SELinux。允许无 UI、无设备文件写入的几何查询入口，输出直接保存电脑端。
- 原型也必须使用 Compose + Miuix，界面规范参考官方 MiuixDemo 的 example 和组件演示。
- 截图须通过 `adb exec-out screencap -p` 直接写入电脑端项目临时目录 `D:\Workspaces\Scratches\ConnectorX`，禁止在 Android 内保存截图。
- 构建入口：`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :protocol:test :app:lintDebug`；交付与操作见 `documents/Prototype1.md`。
