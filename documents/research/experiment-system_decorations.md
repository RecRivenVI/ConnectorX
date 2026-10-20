# 系统装饰、图层透明与 Insets 实验

2026-09-07

## 问题

保留本主题在上述日期的调查问题、环境及证据边界；不作为当前版本能力声明。

## 方法

沿用原记录注明的固定源码审计、只读资料或有界实机实验方法；本轮仅迁移文档，没有重跑这些实验。

## 发现

2026-09-07，只在 shennong / Android 16 / shell UID 2000 操作。延续 [显示语义实验](../research/experiment-display_semantics.md)。没有使用 Root、修改系统设置或操作 popsicle。

### 结论

**开启装饰可恢复导航栏 Insets，但本轮没有证明“隐藏远端栏图像并完整保留 Insets”可作为通用方案。**

| 实验 | 状态 | 实测 |
| --- | --- | --- |
| VD 增加 SHOULD_SHOW_SYSTEM_DECORATIONS | PASS（导航栏） | 底部 navigationBars=56px、visible=true；状态栏仍为 0 / false |
| 应用 hide(systemBars) / show(systemBars) | PASS | 导航栏当前 Insets 56→0→56；忽略可见性的 Insets 保留 56 |
| 实际 Compose 内容避让 | PASS | SAFE BOTTOM 标记随上述状态上下移动，完整图像直接从 ImageReader 流出到 PC |
| 公共 Insets 动画接口 + shownStateInsets、alpha=0 | FAIL（保留当前 Insets） | 动画期间应用收到 navigationBars=0 / false，WMS source 也为 false |
| 直接对栏 leash 应用 SurfaceControl alpha | pending / 入口失败 | 获取动画实现 mControls 字段发生 NoSuchFieldException，未拿到 leash，未实际执行 setAlpha |
| 任意第三方应用透明系统栏 | pending | 自有测试 Activity 的动画权限不能推广为 runtime 对任意 App 的控制权限 |
| 顶部状态栏避让、控制端栏高度同步 | pending | 本 ROM 的装饰虚拟屏没有状态栏 source；未测试自定义高度 |

公共动画实验使用 controlWindowInsetsAnimation(navigationBars)，onReady 中调用 setInsetsAndAlpha(shownStateInsets, 0, 1)，6.5 秒后 finish(true)。**该失败不能推导为 SurfaceControl 单独改 alpha 必然改变 Insets**：动画控制协议本身也会改变可见状态，直接图层路线未执行成功。

直接实验尝试复制动画控制器的栏 SurfaceControl，finish(true) 后再改该副本的 alpha。入口反射失败，可能涉及 ROM 实现或非 SDK 访问限制，未进一步证明具体原因。没有关闭全局 hidden API 检查，没有进入 SystemUI 进程修改图层。

### 画面证据的边界

该 ROM 在测试 VD 正常状态下就没有明显导航条图像，不能用“截图没有导航条”当作隐藏成功证据。蓝绿色背景覆盖完整画面，SAFE BOTTOM 标记证明普通与全屏模式存在实际布局差异。顶部文字贴顶，与 statusBars Insets 缺失一致。

1440×3200 原始 PNG 中，底部标记的深色像素纵向范围分别为正常 3082–3127、全屏 3138–3183、恢复 3082–3127，即准确下移/恢复 56px。测量仅分析已有图像，没有修改图像。

截图来自临时 VD 的 ImageReader，PNG 在内存生成并以 Base64 经 adb 标准输出到 PC 后解码；**Android 文件系统没有保存图片**。这种路径用于读取虚拟显示原始输出，不是主屏截图。帧年龄写在输出中，静态画面最后一帧可能已有数秒。

### 实现与证据

只扩展既有 debug 探针和 Compose + Miuix 测试 Activity，产品 runtime/UI 不变。

探针参数：

- `decorations`：装饰 VD，完整方向矩阵、应用全屏切换；VD 86。
- `decorations-alpha`：公开动画 alpha 实验；VD 87。
- `decorations-direct`：直接 leash 入口实验及底部标记；VD 88。

使用 DisplaySemantics.md 中命令替换最后一个参数即可。每轮 60 秒自动销毁本次显示及任务。取消/失败后恢复动画 shown 状态；直接实验未取得任何图层句柄。

PC 原始资料：`工作区 Scratches\ConnectorX\decorations`：

- `baseline.txt`、`alpha.txt`、`direct.txt`：三轮标准输出，包括 policy 与帧时间。
- `activity.txt`、`direct-activity.txt`、`final-activity.txt`：实际 Insets 与入口失败堆栈。
- `direct-normal.png`、`direct-fullscreen.png`、`direct-shown-again.png`：普通、全屏、恢复的布局证据；不是直接 alpha 已生效的证据。
- `direct-sf.txt`、`direct-sf-during.txt`：SurfaceFlinger 诊断快照，不能因存在图层记录就推断取得可操作句柄。
- `after-display.txt`、`after-activities.txt`：测试显示和任务已清理。
- `checks-final.txt`：assembleDebug / lintDebug 成功，0 errors、10 既有 warnings。本次没有新增或重复无关单元测试。

最终诊断 APK 只安装 shennong，SHA-256：`13BE2D61EAAEAB5EA6ED310EA293E5B98B2592DECAD9144D816D1F77B9D39AC4`。此后仅修改 Java 注释及文档，不影响实验行为。没有创建提交。

### 后续判断

这条路线目前只能作为导航栏兼容实验基础，不能整体替代用户要求的 Insets 代理。继续追求完整语义需要解决顶部来源，以及 runtime 对系统栏图层的合法句柄访问/系统集成；普通动画接口不是长期隐藏远端栏的通用机制。

参考：[WindowInsetsAnimationController](https://developer.android.com/reference/android/view/WindowInsetsAnimationController)、[WindowInsetsController](https://developer.android.com/reference/android/view/WindowInsetsController)。设备结论以本页实验为准，不从 API 存在推导可用。

## 结论

结论以原记录的证据等级和设备范围为限。当前采用的行为见[产品范围](../project/overview-product.md)、[交互](../design/principle-interaction.md)与[进度](../project/status-current.md)，不据历史记录补写新验收。
