# 原型 2 交付记录

日期：2026-09-07；版本 `0.2.0-prototype2` / versionCode 4。

用户已确认原型 1 的画面、操控与多指触摸正常。本次仍遵循只安装、由用户操作的边界。

| 项目 | 结果 |
| --- | --- |
| APK 构建 | PASS |
| 本地测试 | PASS，3 项启动等待 + 5 项原控制协议 + 6 项新请求/元数据/resize/键盘测试 |
| Lint | PASS，0 errors / 6 warnings；未屏蔽错误 |
| popsicle 覆盖安装 | PASS，0.2.0-prototype2 / versionCode 4 |
| shennong 覆盖安装 | PASS，0.2.0-prototype2 / versionCode 4 |
| 配对数据 | PASS，两端安装前后 identity.xml 摘要一致 |
| 原型 2 的虚拟 App、旋转、刷新率、系统栏、快捷方式 | pending，待用户实际操作 |

APK SHA-256：`B64C0EE28EE7DA2098EB2904A7BB9BA8CE4BF3042B411D9F19CBBAD5D65F4ADE`。

交付物、测试 XML 与 lint 文本：`D:\Workspaces\Artifacts\ConnectorX\Prototype2\0.2.0`。本轮未启动或操作两端 UI，未截图，未创建提交或推送。

相较固定上游仅修改 5 个文件，15 行新增 / 5 行删除（其余 ConnectorX 代码位于自有模块）；路径与理由见 [上游记录](../../../upstream/README.md)。用户操作步骤及未验证边界见 [原型 2](../Prototype2.md)。
