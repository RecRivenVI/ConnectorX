# 界面重排首轮

## 目标

先对齐 Miuix 正式发布件，再落实外部评审第64号指令的“调整”项。四栏顺序、持久配对、会话和已确认控制行为保持；真实图标、画质预设、上次在线时间不做。

## 范围

### 三处主要风险

1. Miuix 正式版的导航、模糊、主题或组件 API 差异：先单独升级并对比基线构建、lint、原有测试；真机核对可覆盖的画面，未验证变体如实记录。
2. 弹窗改整页后返回栈、预测/横移返回及控制触摸冲突：沿用本地导航承载和会话所有权，返回只关本地子页；不把本地手势覆盖到控制画面。
3. 权限/设备状态映射遗漏：枚举当前真实状态及动作，用界面映射测试覆盖；离线不是删除配对，原始内部详情只放诊断或详情。

### 逐项落点

路径以下以 Kotlin 文件名指代，源目录沿现有包；新文件按页面职责拆分，不改协议或特权模块。

| 要求 | 现有位置 | 本轮调整 |
| --- | --- | --- |
| Miuix 正式版 | gradle/libs.versions.toml；core/ui 与 app 构建 | 固定正式发布件，必要 API 适配；主题正文及用户控件不重设计 |
| 术语分层/状态 | AndroidDevicesAdapter、AndroidLibraryAdapter、RuntimeConnection 状态、LibraryModel/DevicesModel | UI 类型与文案映射；包名/地址/身份进详情，原因加下一步动作；不改认证流程 |
| 诊断默认关闭 | AppShell、SettingsPage、StreamQuality、LocalApplicationPage、RemoteAppScreens、DeviceMirrorPage、ControlPage | 独立本地显示偏好；原诊断仅在开启后可见，不增加新采集 |
| 应用继续使用 | LibraryPages、LibraryModel | 横向会话卡，显示应用/设备/类型，复用 resume |
| 设备 TabRow | LibraryPages、AndroidLibraryAdapter | 本机及全部配对设备，状态点；移除设备选择弹窗 |
| 自动读取/刷新 | LibraryPage 与现有 showLocalApps/showApps | 按选中来源自动读取，下拉刷新；失败/空列表原因与重试 |
| 常用与目录 | LibraryPages | 四列首字母占位网格，搜索和点击打开，右侧更多 |
| 应用底部弹层 | LibraryPages、ApplicationSettingsDialog、AppLayoutPreferences | WindowBottomSheet；打开、常用、已有快捷方式、原两项下沉设置、包名详情 |
| 本机状态卡 | DevicesPage、AndroidDevicesAdapter | 名称/这台设备、接收摘要、权限说明与动作；地址仅接收摘要/详情 |
| 已配对卡/空态 | DevicesPage、DevicesModel | 用户状态、现有会话、打开应用/镜像/重试；空态添加设备 |
| 设备详情整页 | DevicesPage 的旧详情弹窗 | 名称状态、离线原因、操作/会话、现有地址与短身份；红色解除及指定确认 |
| 添加设备整页 | DevicesPage 的旧添加弹窗、MainActivity 配对确认 | 两角色 TabRow、发现进度与候选、手动地址、接收/新配对开关、放大校验码、结果卡 |
| 自动化 | LibraryPages.AutomationPage、AndroidLibraryAdapter、MainActivity.globalScrollModel | 缩略图和唯一状态动作；目标选择/开始方向/进行中；删除未来宏与长说明 |
| 设置分组 | SettingsPage、AppShell | 个性化/远程控制/手势/高级/关于；版本从 BuildConfig 注入，外观页不动 |
| 视频与声音基础项 | MediaSettingsPage、MediaSettingsModel | 新会话提示；分辨率、帧率、Mbps；声音来源用户名称，外放可用条件 |
| 高级折叠 | MediaSettingsPage | 视频编码/音频编码/缓冲分组；单位转换仅 UI，原值/范围/格式/校验保持；恢复默认 |
| 远程会话布局 | RemoteAppScreens、DeviceMirrorPage、ControlPage、LocalApplicationPage | 本机已确认布局为参照；远端顶部 ConnectorX/标题/功能/退出，底部同套 AOSP 三键；现有滚轮能力范围内共用右控件 |
| 功能底部弹层 | 上述远端页面旧 WindowDialog | 只迁移该类型原有能力：发送文本/显示选项/重开/诊断；退出确认，回主界面保留会话 |
| 文案资源 | core/ui 的 Compose Resources 与 app UI 引用 | 修改页面文案同步 values、values-zh-rCN；未改页硬编码记债务 |
| 回归/截图 | app/src/test 与 validations | 码率/声音/状态/权限映射测试，shennong 看图；双机、变体及手感人工清单 |

### 数据与能力边界

- 不新增图标、时间、系统版本、型号或配对日期的协议字段；占位首字母保持。
- “附近”只表示现有发现候选，不能承诺远端权限就绪或网络认证成功；重试复用已有发现/目录动作。
- 搜索进度只呈现本次发现/读取活动，不编造已在线；失败原因由当前操作结果映射。
- 远程会话没有现成持续滚轮 API 时，不改运行时或协议补造；先核实已有客户端滚轮能力，无法等价迁移则在结果与人工清单明确说明。
- 不在 shennong 创建/撤销配对，不改系统设置或 Shizuku 授权，不操作其已有会话。只退出本轮打开的会话/悬浮控制，恢复本轮改动的应用设置。
- 用户后续决定本轮暂不使用 Android 模拟器：已停止自建实例，不再执行模拟器验证；深浅色、最大字号、宽屏等无法在当前真机设置下覆盖的项如实标为未验证，不改 shennong 系统设置。

## 步骤

1. 完成：保存 e858ec6 基线，Miuix 正式版构建通过、原有142项用例及结果一致；lint无新增告警，少10条候选版升级提示；以第一个Update检查点保存并推送。外观兼容尚待真机检查。
2. 待办：术语映射及显示会话诊断开关，补对应单元测试。
3. 待办：设备、详情、添加与校验码；本地返回和可覆盖状态截图。
4. 待办：应用页、设备切换与应用底部弹层；读取/失败/空态截图。
5. 待办：画面与音频分组、折叠与单位映射；测试与截图。
6. 待办：远端会话布局/功能/退出确认；本机已确认控件保持。
7. 待办：自动化和设置；补完真机可覆盖页面状态，其他显示变体如实记录。
8. 待办：冻结候选全量验证，持锁认出 shennong、核签名、前后文件对比与真机检查；保存 debug 包和人工清单，提交推送第二个 Update。

## 结果

进行中。截图、运行结果、设备文件清单与安装包只放工作区，不提交证据或临时探针。本轮不动 scrcpy 子模块。
