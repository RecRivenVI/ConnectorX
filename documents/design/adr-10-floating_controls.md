# ADR 10：悬浮控制与停靠

## 状态

已接受

## 背景

用户逐轮确认的悬浮窗与应用内滚动交互，以当前实现为基线；本记录保留现行设计原文，手感仍由用户验收。

## 决策

- 纯图标上滚/停止/下滚三段按钮直接复用应用内的 LocalScrollControl，布局、图标、Miuix 配色和交互均使用同一实现。1.0.17 适度缩短并加粗：32dp 轨道、23.04dp 内胶囊、48dp 触摸宽，目标长 204dp、最低 156dp；外/内端部半径为 16dp/11.52dp，沿用 Miuix 圆点 0.72 比例。1.0.19 上下速度翻倍为固定 320 原生滚轮单位/秒，悬浮窗与本机应用模式相同。
- 点击或按住滑过三个选项直接选择，滑动松手保持；再次点击当前正在滚动的方向则停止，点击另一方向直接换向，中间停止按钮保留。当前方向的点击在正常松手时停止，拖动与系统取消手势不能误触发关闭；无障碍点击使用相同行为。快速跨项使用手势内状态去重，不漏过中间停止。应用内镜像根据实时窗口与显示尺寸等比例动态最大化，在上下独立按钮区和侧边留白约束下整屏横纵居中。
- 顶部拖动与底部退出按钮为直径 32dp 的圆形，触摸区域均为 48×48dp，与主轨道各隔 8dp。拖动开始停止滚动，过程中允许跟手移动，松手或取消后停靠到最近固定点；不再保留任意自由位置。
- 五个图标直接使用固定 Miuix 0.9.4-rc01 图标库的 Regular 变体：拖动为横向 More，上滚为旋转 90° 的 ChevronBackward，停止为 Pause，下滚为旋转 90° 的 ChevronForward，退出为 Close。仅对库内图标施加旋转和主题 tint，不自绘路径；文字语义仍为拖动、上滚、停止、下滚、退出。应用内三段按钮同步使用同样的方向/停止图标。
- 固定六个点：左上、左中、左下、右上、右中、右下。左右各三个，整组中心对应完整屏幕高度的 1/3、1/2、2/3，窗口顶部为该中心减去窗口高度的一半。默认点位为右上（右侧 1/3）；已有点位记录仍优先恢复。两侧可见胶囊边距均为 12dp，计算时扣除触摸留白；系统栏/挖孔和极小窗口仍做安全限位，空间不足时点位可向内移。应用内控件继续纵向整屏居中，横向保持镜像右框与屏幕右缘之间居中。
- 首次开启将旧自由位置迁移到最近固定点，旧默认位置对应右上；之后只保存点位名称 dock_slot，不保存任意坐标。重开或旋转仍使用原点位并重算坐标。
- 停靠和磁吸选择首先按距拖动位置最近裁决；并列时依次优先原点本身、到原点距离更近的候选、原点所在侧。上述条件仍全部相同才用稳定的枚举顺序兜底；后一级不能覆盖前一级，侧别权重不会压过实际距离。原点固定为这次拖动开始时的停靠点，不因途中临时吸附而改变。
- 开始拖动暂时排除原点，首次离开其 48dp 范围后允许重新进入吸附。其他点仍为 32dp 进入、48dp 脱离；牵引随距离和手势速度连续变化，240dp/s 以下完整启用、900dp/s 以上不吸附，中间平滑减弱。牵引偏移最多 12dp，窗口不会完全固定在锚点，原始位置仍直接跟手。
- 磁吸偏移使用现有 Compose FloatSpringSpec：吸入刚度 2400、拖离 6000，阻尼均为 1，拖离比吸入更快恢复。单个 Choreographer 回调在显示帧上推进双轴状态，移动只改目标、不重启动画；静止并收敛后不持续申请帧。松手按原始位置选定最近点，再用刚度 750、阻尼 1 的弹簧准确停靠，继承有界的屏幕坐标运动速度；速度不改变点位裁决。系统 CANCEL 仍停靠但不继承甩动速度。系统关闭动画时直接到位；旋转、退出或再次开始拖动移除旧帧与完成回调。
- 下方分体小胶囊显示退出图标，点击停止并关闭全局悬浮窗。不会关闭任何镜像或远控会话。自动化页还提供停止、退出入口，前台通知提供停止操作。
- 悬浮窗持续显示，取消闲置淡出计时、动画和唤醒逻辑；使用普通 FrameLayout 记录原始触摸坐标，系统窗口 alpha 保持 1。输入未就绪时的禁用样式仍由共享控件决定。
- 收到息屏通知立即发送停止，保留窗口、位置和服务；解锁不会自动恢复滚动。系统锁屏可能遮住应用悬浮窗，这与 ConnectorX 销毁窗口不同，不承诺覆盖安全锁屏。
- 控件不取得键盘焦点，不把自己的触摸转给底下应用。原生滚轮落点为主屏中央，目标是否响应、每单位滚动距离由前台应用决定；不模拟手指滑动。

## 后果

应用内与悬浮窗共用控件；窗口动画不得进入特权滚轮调度，不能由组件测试推断实机手感。

算法参考（仅借鉴交互策略，未复制实现或资产）：AOSP [MagnetizedObject](https://android.googlesource.com/platform/frameworks/base/+/1963708d366eaf301a6b6c29d9509d4b1dc7eff7/libs/WindowManager/Shell/src/com/android/wm/shell/common/magnetictarget/MagnetizedObject.kt) 的速度门控，AOSP [StackAnimationController](https://android.googlesource.com/platform/frameworks/base/+/702ca7cf63dc58a468de1ef4a0a2ac8932fe48cf/packages/SystemUI/src/com/android/systemui/bubbles/animation/StackAnimationController.java) 的拖离追赶与弹簧停靠，[FloatingView](https://github.com/recruit-lifestyle/FloatingView/blob/dae0fb7d8c4f6bfc233de21a5998096606b38053/library/src/main/java/jp/co/recruit_lifestyle/android/floatingview/FloatingView.java) 的手势初速度，以及 [Floating-Bubble-View](https://github.com/dofire/Floating-Bubble-View/blob/740588051acffb0382cf7c33bcd54e7ccb87820b/FloatingBubbleView/src/main/java/com/torrydo/floatingbubbleview/service/expandable/ExpandableBubbleService.kt) 的拖动/松手职责分离。ConnectorX 使用已有 CMP 1.12.0 的弹簧计算，没有新增 UI 框架或 DynamicAnimation 依赖。
