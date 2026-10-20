# 本机显示与输入

本机指定应用使用 VirtualDisplay→SurfaceView 直连，不编码、不传网络；保持完整本机分辨率和 DPI，由系统缩放预览。

## 显示与线程

SurfaceHolder 固定完整缓冲尺寸，Surface 重挂仅更新显示输出，不重建 Display/Task。本机模式请求平台跟随物理刷新，View 挂载期间监听变化并提交 Surface 提示；请求值、系统报告刷新率和实际呈现帧率不是一回事。

生命周期工作与输入队列分线程；输入队列有界，仅合并相邻 MOVE。DOWN/UP/CANCEL 和按键保持顺序；无法接纳边界事件时取消并释放，不能悄悄丢抬起。单批触摸包含全部已接管指针，在 runtime 生成一个 MotionEvent，保留源事件 uptime。恢复 Surface/重启通过异步释放屏障，不让 UI 线程等待 Binder。

## 原生输入法与返回

IME 使用 LOCAL，键盘在虚拟屏内随目标应用显示，真实 IME Insets 交给目标窗口；外层预览和 VirtualDisplay 分辨率不因键盘改变。宿主本机页临时设置 FLAG_ALT_FOCUSABLE_IM，离开时恢复原 flag 位与 softInputMode，不影响目录中的本地输入框。不主动聚焦其他 Display/Task，不注入虚拟 IME 高度。

系统返回和工具栏返回共用 LocalImeBackState。每次按需检查 IMMS 当前 UID/Display；属于目标的键盘正在显示时，通过 shell 系统接口隐藏并有界确认，本次不注入 Back。后续确认键盘已隐藏才向目标 Display 注入 Back。失败、超时、目标变化和重复回调不能误退页面；主屏 Insets 不用于判断虚拟屏键盘。

读取为有界快照，只在隐藏请求后短时确认，不后台轮询或持久化原始内容。系统接口存在外部焦点竞态，OEM 格式、中文输入与连接恢复仍需分别验收，见[研究](../research/experiment-local_ime.md)。

## 锁屏、滚轮和诊断

本机开启系统装饰时不设置 ALWAYS_UNLOCKED，让 SystemUI 完成其锁屏/解锁生命周期，避免导航栏根 View 留在 GONE。无装饰本机与网络显示 flags 不因此改变，Surface 恢复不以重建任务、padding 或 Insets 注入替代。

持续滚轮共用 [WheelTimeline](../../third_party/scrcpy/server/src/main/java/io/github/recrivenvi/connectorx/runtime/WheelTimeline.java)，UI 只下发方向。Surface 分离、重启、异常和退出停止滚动并释放输入；不承诺息屏持续运行。按钮与布局定义见[界面原则](principle-interface.md)。

“功能”打开时按需读取输入队列、Binder、滚轮调度和显示报告值，关闭停止读取。输入耗时不包含目标处理及上屏，不是触摸到显示延迟。

历史产品交互证据支持 shennong 的 LOCAL IME、宿主输入法排除和两步返回；它不替代本轮未进行的 ROM 复测。端到端与锁屏验收见[场景定义](../../validations/scenario-local_display/validation.md)。
