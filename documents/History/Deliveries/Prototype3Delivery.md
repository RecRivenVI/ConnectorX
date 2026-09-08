# 原型 3 交付记录

日期：2026-09-07；最终版本 `0.3.1-prototype3` / versionCode 6。0.3.0 为同轮只读参数探针的中间构建，发现矩形路径识别问题后由此版本替换。

| 项目 | 结果 |
| --- | --- |
| APK 构建 | PASS |
| 本地测试 | PASS，21 项：原有 14 项 + 7 项几何/矩形预留区回归 |
| Lint | PASS，0 errors / 13 warnings，无屏蔽错误或 baseline |
| 两台覆盖安装 | PASS，均确认 versionCode 6 |
| 配对数据 | PASS，安装前后两端 identity.xml SHA-256 一致 |
| popsicle 同代码只读探针 | PASS 读取；outline=system-radius-approximation，cutout=unavailable，明确 degraded |
| shennong 同代码只读探针 | PASS 读取；outline=systemui-alpha-contour，cutout=systemui-alpha-mask |
| 新界面、灰框、多指、旋转 | pending，等待用户操作验收 |

APK SHA-256：`464975F8805FC656F39FB2515F3E47E79CF5A3A42C4B8E60567239C0960F3CF3`。

交付物位于 `D:\Workspaces\Artifacts\ConnectorX\Prototype3\0.3.1`：APK、测试 XML、lint 文本、两端实际几何 JSON、只读参考 APK 的路径/摘要清单。

系统 APK、资源导出及提取 PNG 位于电脑端项目临时目录 `D:\Workspaces\Scratches\ConnectorX\geometry`。Root 仅用于读取必要的系统文件，参数探针使用 shell；未修改设备设置/overlay/SELinux，未在 Android 内保存截图或探针输出，未操作远控界面。

本轮没有创建 Git 提交或推送。使用步骤见 [原型 3](../Prototype3.md)，数据精度依据见 [设备几何研究](../../Research/ScreenGeometryDevices.md)。
