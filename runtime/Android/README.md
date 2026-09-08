# Android 特权 runtime

Android library 编译 ConnectorX 特权实现及固定 scrcpy 源码，依赖 protocol，不依赖 app。拥有 IRuntime.aidl、PrivilegedRuntime、RuntimeStartup、ScreenGeometryCollector ；VirtualAppHooks 位于 fork 子模块并由本模块编译。

前三个根包类和 IRuntime 保持 io.github.recrivenvi.connectorx 完整名称以避免 Binder/启动路径变化，模块 namespace 仍为 com.genymobile.scrcpy。Java/AIDL 由 APK 打包进同一安装文件；app_process CLASSPATH 方式不变。

测试运行 :runtime:testDebugUnitTest。诊断源集默认不编译，见 [探针说明](../../tools/diagnostics/README.md)。上游归属见 [upstream](../../upstream/README.md)。
