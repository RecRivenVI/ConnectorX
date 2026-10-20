# 屏幕几何

圆角使用系统半径驱动自有 G2 曲线；挖孔只有在可信宽度与厂商精确圆心同时存在时才绘制，不猜测物理位置。

[ScreenGeometryCollector](../../core/privilege/src/main/java/io/github/recrivenvi/connectorx/ScreenGeometryCollector.java) 只读取得自然尺寸、方向、四角半径与 DisplayCutout 等资料。框架 cutout bounding rect 可能包含保留区，不能当作摄像头真实圆心。厂商 SystemUI 精确值缺失时，不显示挖孔，也不排除相应触摸。

G2 曲线定义保留在 [ADR 6](adr-6-g2_corners.md)，实现位于 [G2Corners](../../core/protocol/src/main/java/io/github/recrivenvi/connectorx/protocol/G2Corners.java)。这是自定义连续曲率造型，不是恢复真实面板曲率。PNG 圆角蒙版不参与当前渲染。

SurfaceView 上层以不透明遮罩裁出圆角，灰框只向外扩展，内缘紧贴画面。可信孔洞为实心黑色、无额外描边。几何只在尺寸/来源变化时重算；视频不经逐帧 CPU 回读。路径、孔洞和触点起点测试使用同一自然坐标到画面的变换。

逐触点越界继续规则以[交互规格](principle-interaction.md)为准，不能因 MOVE 进入非显示区域强行抬起。历史调查见[屏幕几何](../research/investigation-screen_geometry.md)及 [popsicle 资源](../research/investigation-popsicle_mask.md)。
