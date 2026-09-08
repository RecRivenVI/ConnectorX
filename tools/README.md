# 开发工具

`./tools/Check.ps1` 运行普通 APK 构建、app/runtime/protocol 单元测试及 app/runtime 两模块 lint；失败即返回非零。当前 app 测试源集为空，既有启动测试已归入 runtime。脚本不安装设备，不提交或推送。

`./tools/Check.ps1 -Diagnostics` 额外打开诊断源集，仅输出临时实验 APK。使用前阅读 [诊断约定](diagnostics/README.md)。依赖版本统一在 gradle/libs.versions.toml。

## Windows

`./tools/StartWindows.ps1 -Build` 测试、打包并启动 Windows x64 控制端；无需构建 Android。输出遵循 Artifacts 目录。详见 [Windows](../documents/Windows.md)。

0.5.1 起 Check.ps1 同时运行 Windows 单元测试和桌面打包（自动编译共享 UI 两个目标），不自动启动或安装。

0.6.1 起 Check.ps1 需要 Python 3，构建后运行 VerifyApk.py，检查最终 APK 的全部 DEX 类定义唯一和 Android/共享 UI 启动入口方法存在；不以方法字符串引用充当定义证据。
