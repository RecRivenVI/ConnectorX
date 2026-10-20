# ConnectorX

把已配对的 Android 设备作为本机的扩展应用运行环境：直接打开远端应用、操作完整设备镜像，或在本机虚拟屏运行指定应用。

当前只支持 Android 间互联。使用 Compose + Miuix，显示和输入基于 scrcpy fork；设备权限需已运行并授权的 Shizuku。实际版本和最低系统要求见[应用配置](app/build.gradle.kts)及[依赖目录](gradle/libs.versions.toml)。

## 快速开始

已有安装包时，先看[配对](documents/usage/guide-pairing.md)和[控制说明](documents/usage/guide-control.md)。从源码构建：

```powershell
git submodule update --init --recursive
.\gradlew.bat assembleDebug
```

本机 SDK/JDK 准备、测试和产物位置见[开发工作流](documents/development/workflow-android.md)。

## 文档

- [当前进度](documents/project/status-current.md)与[产品范围](documents/project/overview-product.md)
- [本机应用](documents/usage/feature-local_display.md)、[全局悬浮控制](documents/usage/feature-global_scroll.md)、[媒体配置](documents/usage/configuration-media.md)
- [架构](documents/design/architecture-android.md)、[项目规范](documents/AGENTS.md)、[技术债](documents/project/debt-existing_code.md)
- [scrcpy 修改](documents/reference/upstream-scrcpy.md)与[Mishka 移植](documents/reference/upstream-mishka.md)

## 许可

scrcpy、Mishka、AOSP 及来源文件的许可和署名见 [NOTICE](NOTICE)。本仓库没有为未注明许可的原创部分另行指定许可证；第三方原许可证及分发义务保持有效。
