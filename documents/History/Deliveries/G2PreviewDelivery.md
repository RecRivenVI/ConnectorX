# G2 圆角预览交付

日期：2026-09-07；版本 `0.3.2-prototype3` / versionCode 7。

| 项目 | 结果 |
| --- | --- |
| 构建 | PASS |
| 本地测试 | PASS，27 项（新增 6 项 G2 数学检查） |
| Lint | PASS，0 errors / 10 warnings |
| popsicle 覆盖安装 | PASS，versionCode 7 |
| shennong 覆盖安装 | PASS，versionCode 7 |
| 两端配对记录 | PASS，更新前后摘要一致 |
| 只读圆角查询 | PASS，popsicle 四角 170px、shennong 四角 147px，均为 system-radius-g2 / maskRenderingEnabled=false |
| 实际视觉与触摸 | pending，由用户验收 |

APK SHA-256：`BE31EC636C5B09B587123034945CA8A823B697F0B2D3202288C34F661866DEC3`。

交付文件位于 `D:\Workspaces\Artifacts\ConnectorX\Prototype3\0.3.2`，包含 APK、测试 XML、lint 文本及实际圆角查询 JSON。

当前设备镜像不解码或绘制 PNG/cutout 遮罩，不做孔洞触摸排除；活动查询只读取尺寸、方向和系统半径。未来孔洞仍需具体贴图来源。G2 模型详见 [ADR 0006](../../Decisions/0006-G2Corners.md)，不声称其等于硬件物理曲率。

本轮没有操作远控界面、截图或创建 Git 提交。安装后由用户重新打开应用、启用 Shizuku 并进入设备镜像。
