# 当前进度

## 当前状态

产品只开发 Android 间互联。应用版本、标识和 SDK 以[应用配置](../../app/build.gradle.kts)与[版本目录](../../gradle/libs.versions.toml)为准。

| 范围 | 状态与证据边界 |
| --- | --- |
| 持久配对、画面、多指控制 | 已有用户操作确认；安全负向、长期恢复与多 ROM 矩阵仍 pending |
| 远端独立应用、真实 VirtualDisplay | 已有用户整体体验确认；旋转闭环、布局/IME 全兼容仍 pending |
| 实体外观镜像 | G2 数学和部分几何来源已验证；不宣称真实物理曲率或所有设备精确挖孔 |
| 本机 Surface 直连、LOCAL IME、返回 | 已有 shennong 产品交互证据，完整中文输入和其他 ROM 仍 pending |
| 全局滚轮与六点有限磁吸 | 代码与组件/时序回归存在；真实拖动手感 pending，本轮不验收 |
| 媒体参数与质量面板 | 序列化与边界回归存在；实际上屏、音频矩阵、功耗与端到端时延 pending |
| 仓库规范整理 | 模块、文档、来源与归档已完成；构建、测试用例及结果一致，应用标识/版本保持；无功能扩展、无设备操作 |

## 剩余工作

行为边界见[交互](../design/principle-interaction.md)、[本机显示](../design/mechanism-local_display.md)和[悬浮控制](../design/mechanism-global_scroll.md)。存量规范差异按影响登记为[技术债](debt-existing_code.md)，完整后续范围见[backlog](backlog-product.md)。

## 下三步

1. 由用户完成有限磁吸与松手停靠的手感验收。
2. 在另行授权后完成双机认证、恢复、退出和本机输入法场景矩阵。
3. 依据真实测量处理高优先级会话/媒体职责债务，不以重写 UI 或增加缓冲代替定位。

## 开放项

- Android-only UI 仍用 KMP 源集；后续迁移默认源码/资源布局需要保持资源包与本地化。
- 当前环境没有 Android Studio，未执行其默认格式化器；仅统一文本编码/换行。
- lint 无错误；应用报告增加了工具/依赖升级提示，未升级依赖或屏蔽提示，存量代码告警保持。
- 未注明许可的原创部分尚未选择许可证；第三方许可/署名已保留，需用户决定整体分发政策。
- 真正旋转闭环、完整宏与公网属于后续范围，Windows/Flutter 已停止。

本轮原始日志与归档在工作区 Artifacts/ConnectorX/RepositoryStandard-1.0.0 和 History/RepositoryStandard-1.0.0；探针在 Scratches/ConnectorX/RepositoryStandard-1.0.0，不进仓库。
