# ADR 0001：ConnectorX 初始边界

## 状态

已被 adr-7 取代

## 背景

本记录保留当时的产品决策原文，当前规格见[架构](architecture-android.md)及[交互](principle-interaction.md)。

## 决策

- 日期：2026-09-07
- 状态：采纳产品约束；具体实现选型待后续 ADR
- 背景：基于用户产品提案创建空仓库的文档与职责骨架。

### 决策

1. 名称 ConnectorX；Android applicationId / namespace 为 `io.github.recrivenvi.connectorx`。
2. 远程控制与触摸宏为同一产品，共享设备端输入及显示能力。
3. 从官方 scrcpy 固定源码基线研究并派生 runtime，不重写成熟的 Android 底层能力。
4. 先查清权限与启动模型；Shizuku / Root 是待验证接入方向，不是已承诺的等价能力。
5. 首轮 P1 持久配对、P2 远程应用、P3 实体镜像；P0 权限研究为前置。
6. Android 前端 Compose + Miuix；Go 用于确有必要的独立服务；协议不绑定 Android UI。
7. 当前只落地文档与模块职责，不提前选择依赖/SDK 或创建假实现。

### 结果与待定

仓库可以承接源码研究与原型，但当前不能构建或运行。上游源码导入形式（保留历史的派生树或固定 submodule 加适配层）、许可证、构建工具链、认证传输、网络终结点与 Windows 产品形式均待明确记录。

## 后果

仅在上列状态和当前规格适用范围内继续执行，不把历史实验、提议或构建证据扩展为当前端到端能力。
