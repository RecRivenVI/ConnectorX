# 落实仓库规范

## 目标

在当前含全部未提交变动的工作树上落实仓库规范，不改变产品行为。先保全内容与验证基线，再迁移、整理、验证，最后分别提交并推送 fork 与父仓库检查点。

## 范围

### 三处主要风险

1. 模块、子模块迁移导致构建、资源、测试引用失效：迁移前后运行同一组 Gradle 命令，对比测试用例集合、应用元数据和 lint 结果。
2. 探针/流水混有有效产品实现或输入：只移出已确认的实验与门禁，src/main/src/test 产品实现完整保留；原文件归档，不永久删除。
3. 旧规则丢失：不可变标识、会话、触摸、IME、媒体和几何约束转入项目规范及当前设计；原 AGENTS 和全部旧文档在工作区 Artifacts 留存。

### 目录与文件去向

下表前缀覆盖该目录内每个文件，保留相对后缀。工作区外部位置均相对工作区根，不把机器绝对路径写进仓库。未列出的源文件不改变行为；旧 Windows 删除属于已有工作树的一部分，保留删除。

| 分类 | 当前路径 | 去向 |
| --- | --- | --- |
| 保留 | gradle/、gradlew、gradlew.bat、gradle.properties、.gitignore、.editorconfig、.gitattributes | 原位；集中版本、工具链、文本格式和忽略规则 |
| 保留 | app 内稳定包/组件、AIDL、所有现有产品源码及142项测试 | 仅随模块移动，稳定标识不变 |
| 移动或改名 | apps/Android/** | app/** |
| 移动或改名 | shared/ui/** | core/ui/**；暂保留 Compose Resources/KMP 源集，AN-03 差异登记为技术债 |
| 移动或改名 | runtime/Android/** | core/privilege/** |
| 移动或改名 | protocol/** | core/protocol/** |
| 移动或改名 | scrcpy/** | third_party/scrcpy/**；先在当前 master 提交并推送，再以 git mv 迁移，保留独立 checkout |
| 移动或改名 | licenses/{Apache-2.0.txt,Mishka-GPL-3.0.txt,Mishka-NOTICE.md,AOSP-NOTICE.md} | third_party/licenses/；APK内必要署名/许可资源保留 |
| 移出仓库 | licenses/Mishka-sources.json、upstream/** | Artifacts/ConnectorX/History/RepositoryStandard-1.0.0/；有效来源改写到 reference/upstream-*.md，gitlink作为fork快照来源 |
| 移出仓库 | documents/History/**、FourTabsDelivery.md | Artifacts/ConnectorX/History/；保留原文与目录后缀 |
| 移出仓库 | 旧根 AGENTS、README、当前规格与模块 README 的原始副本 | Artifacts/ConnectorX/History/RepositoryStandard-1.0.0/，有效内容转写到下表 |
| 移出仓库 | tools/Check.ps1、VerifyApk.py、tools/README.md | Artifacts/ConnectorX/History/RepositoryStandard-1.0.0/tools/；按 T-06 撤下聚合门禁与自建检查器，改用原生 Gradle任务 |
| 移出仓库 | tools/diagnostics/** | Scratches/ConnectorX/RepositoryStandard-1.0.0/diagnostics/；均为已有调查探针/临时APK，不保留生产诊断源集 |
| 移动或改名 | 各模块 README | 结论合并到 design/architecture-android.md、development/workflow-android.md；原稿归档 |
| 删除 | 新结构中空的 apps/、runtime/、shared/、tools/、upstream/、licenses/ 等旧壳 | 确认归空后移入回收站；不永久删除，不清理归属不明内容 |
| 保留 | 第三方原项目内部结构、CI、许可证 | G-09/C-11 原样；父仓库没有自己的 CI，不为此改写上游 |

### 文档新名称

| 当前文件（documents/ 内） | 去向／有效结论 |
| --- | --- |
| CurrentState.md | project/status-current.md |
| Product.md | project/overview-product.md |
| Architecture.md | design/architecture-android.md |
| Experience.md | design/principle-interaction.md、usage/guide-control.md |
| Media.md | reference/configuration-media.md、usage/configuration-media.md |
| Pairing.md | design/security-pairing.md、usage/guide-pairing.md |
| LocalDisplay.md | design/mechanism-local_display.md、usage/feature-local_display.md；过程原文归档 |
| GlobalScroll.md | design/mechanism-global_scroll.md、usage/feature-global_scroll.md；过程原文归档 |
| Frontend.md | design/principle-interface.md、reference/upstream-mishka.md；过程原文归档 |
| Development.md | development/workflow-android.md、project/backlog-product.md；旧整理结果归档 |
| Prototypes.md | validations/scenario-android_control/validation.md 等场景定义 |
| README.md | 由根 README 文档入口取代，原文归档 |
| Decisions/0001-Foundation.md | design/adr-1-foundation.md |
| Decisions/0002-Prototype1.md | design/adr-2-persistent_control.md |
| Decisions/0003-BinderBridge.md | design/adr-3-binder_bridge.md |
| Decisions/0004-VirtualApplications.md | design/adr-4-virtual_applications.md |
| Decisions/0005-PhysicalMirror.md | design/adr-5-physical_mirror.md |
| Decisions/0006-G2Corners.md | design/adr-6-g2_corners.md |
| Decisions/0007-DevelopmentBaseline.md | design/adr-7-development_baseline.md |
| Decisions/0008-SecureWindowCapture.md | design/adr-8-secure_window_capture.md |
| Decisions/0009-WindowsController.md | design/adr-9-windows_controller.md；保留已弃用状态 |
| Research/DisplaySemantics.md | research/experiment-display_semantics.md |
| Research/LocalImeFocusInsets.md | research/experiment-local_ime.md |
| Research/Permissions.md | research/audit-runtime_permissions.md |
| Research/PopsicleMaskLocation.md | research/investigation-popsicle_mask.md |
| Research/PredictiveBack.md | research/experiment-predictive_back.md |
| Research/ScreenGeometryDevices.md | research/investigation-screen_geometry.md |
| Research/SystemDecorations.md | research/experiment-system_decorations.md |
| Research/TlsReadError.md | research/incident-tls_signature.md |
| Research/EvidenceTemplate.md | development/procedure-research.md；旧模板归档 |
| History/ 内19篇、FourTabsDelivery.md | 全部外部归档，有效约束已在项目规范及当前设计保留 |

### 旧规则去向

| 旧规则主题 | 位置 |
| --- | --- |
| Android-only、框架和版本、主导航 | 项目规范、overview、interface、构建配置 |
| 稳定身份/组件/AIDL/存储/快捷方式 | 项目规范 X-02/X-03、security-pairing |
| 退出/后台、Surface/会话所有权 | X-04、architecture、interaction |
| 触点起点/越界/CANCEL、多指 | X-05、interaction、protocol接口文档 |
| LOCAL IME、返回、装饰VD锁屏 | X-07、local_display、原始研究记录 |
| G2/精确挖孔与失效降级 | X-09、screen_geometry、ADR 5/6 |
| 六点/并列/有限牵引/滚轮租约 | X-10、global_scroll；撤回的参数流水不再作为规则 |
| 媒体、音频、SurfaceView、指标边界 | X-08/X-11、configuration-media |
| FLAG_SECURE、宏、公网等未完成范围 | X-12、overview、backlog、ADR 8 |
| Git、许可、证据、设备操作 | 新根规范、X-13、来源记录；本轮提交按新授权执行 |

## 步骤

1. 完成：保存原始文件与移出清单，记录整理前构建/测试；根规范逐字同步，创建项目规范。
2. 完成：移动模块、移出探针/门禁、集中事实来源，修复构建引用，不改产品行为。
3. 完成：重组七类文档，保留用户设计与研究原文，登记存量代码/界面技术债。
4. 完成：在冻结候选上运行与整理前相同的 Gradle 构建、测试和lint，对照用例集合与元数据。
5. 完成：fork当前master与父仓库main分别以Update保存检查点，正常推送各自origin；以当前Git提交及远程分支为准，不强推、不创建tag或PR。

## 结果

目录、事实来源和文档整理完成。构建及原有测试用例/结果一致，应用标识与版本保持；lint无错误，应用新增依赖/工具升级提示，存量代码告警保持。格式化器、KMP源码集和存量代码/界面差异已登记债务。未新增产品行为或测试，未安装、操作设备或更改管理台账；1.0.23吸附手感不在本轮。检查点为本次整理终点，后续功能另行启动。
