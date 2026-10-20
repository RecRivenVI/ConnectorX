# 有界研究规程

## 适用范围

适用于系统接口、权限、ROM 几何和输入行为研究。当前任务未授权设备实操时，只做静态审计，不据旧授权推定能操作在用设备。

## 步骤

1. 明确问题、预期、固定源码提交与允许操作的设备/权限范围。
2. 在工作区 Scratches/ConnectorX 创建独立探针，产品 APK 不加入临时 Activity 或诊断源集。
3. 优先只读；Root 只在明确授权范围内使用，不改系统设置或 SELinux。
4. 记录设备系统、实际 UID/SELinux、方法和失败边界；敏感原始日志及媒体仅存工作区 Artifacts/ConnectorX。
5. 如需截图，以 adb exec-out screencap -p 直接写入 PC 的 Scratches/ConnectorX，不在 Android 存图片。
6. 清理自己创建的测试文件与进程，不动用户已有服务或连接。
7. 将研究写入 research，包含日期及“问题、方法、发现、结论”；采纳的行为写入 ADR 或当前机制，原研究不事后改写。

## 验收

能区分源码事实、合成实验、产品端到端和用户视觉/手感证据；用 pending/PASS/degraded/FAIL/deferred 表示范围，不能把构建或安装等同于端到端通过。可重复场景只留验证定义，数据和产物不入库。
