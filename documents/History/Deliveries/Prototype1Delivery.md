# 原型 1 交付记录

日期：2026-09-07；用户限定本轮只安装并提供操作说明。

| 检查 | 结果 | 证据 / 范围 |
| --- | --- | --- |
| APK 构建 | PASS | `:app:assembleDebug` |
| 控制协议单元测试 | PASS | 5 项，0 失败；覆盖消息白名单、长度、坐标和校验码绑定 |
| Android Lint | PASS（有警告） | 0 errors、6 warnings；未忽略错误或生成 baseline |
| popsicle 安装 | PASS | `192.168.0.13:43157` streamed install Success，package manager versionCode=1 |
| shennong 安装 | PASS | `192.168.0.101:33193` streamed install Success，package manager versionCode=1 |
| 固定上游对比 | PASS | 与原始 git archive 对比仅 DesktopConnection.java 新增 7 行 UID 检查 |
| 项目 Markdown 本地链接 | PASS | 排除上游原始文档和生成目录后检查 |
| 用户授权 / Shizuku runtime | pending | 未启动 ConnectorX，未操作授权 |
| 双端配对、重连、TLS 负向实验 | pending | 等待用户操作，不预写数据库 |
| 视频、触摸、多指、Miuix 视觉 | pending | 未操作 UI，未截图 |

版本：`0.1.0-prototype1`，包名 `io.github.recrivenvi.connectorx`，APK SHA-256：

```text
F7F466A1FF62BE09EEF9EA03748FBA0BABFC8A71EFFC1702D3ABF6FB7B9BBCDE
```

电脑交付目录 `D:\Workspaces\Artifacts\ConnectorX\Prototype1` 保存 APK、JUnit XML 与 lint 文本。项目临时目录 `D:\Workspaces\Scratches\ConnectorX` 保存源码核验中间资料；本轮没有设备截图。

Lint 警告保留可见：targetSdk 非最高版本、自定义证书 TrustManager、低版本 backup 规则提示、两项 KTX 风格建议、SurfaceView 无障碍点击语义。自定义 TrustManager 使用自签证书身份绑定而非公共 CA 模型，不能把 lint 无错误或本地单元测试当作 TLS 安全审计通过。无障碍远程操作未完成。

参考仓库仍在原 checkout，未创建新提交。ConnectorX 保持 `main` 无提交、无暂存，新增源码与文档未提交；未推送。
