# Android 开发工作流

使用仓库 Wrapper 构建，先初始化固定子模块。应用版本/标识取自[应用配置](../../app/build.gradle.kts)，工具与共用 SDK 取自[依赖来源](../reference/dependency-android.md)，不依赖本机其他源码仓库。

## 环境与构建

在被忽略的根 local.properties 配置 sdk.dir；签名配置和口令也只能放被忽略的本地文件。编译 JDK 须满足[根工具链定义](../../build.gradle.kts)，不能将本机绝对路径写进 Gradle。运行命令均从仓库根目录执行。

```powershell
git submodule update --init --recursive
.\gradlew.bat assembleDebug
```

APK 默认位于 app/build/outputs/apk/debug；交付副本放工作区 Artifacts/ConnectorX，不跟踪产物。应用 UI/特权/协议的字节码目标不同，由根配置统一定义；不能为了目录整理改变运行兼容性。

## 验证

日常按受影响范围运行标准任务，不维护自建门禁或 CI。

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :core:protocol:test
.\gradlew.bat lintDebug
```

结构检查点前后使用同一组合命令并对比用例集合、结果及 APK 元数据；不把测试数量等同于产品正确性。

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest test lintDebug --rerun-tasks
```

单元测试保留在各模块 src/test。需要设备或人工判断的定义在 [validations](../../validations/scenario-android_control/validation.md)；运行数据与日志在工作区 Artifacts。禁止未经当前授权安装或操作设备。结构整理不自动覆盖安装，不启动或停止已有服务。

## 修改与交付

先读根规范、项目规范和进度。改 UI 参考 MiuixDemo，Android Studio 按官方 Kotlin 风格格式化；缺少格式化器时明确记录限制，不用无关的第三方重排代替。

修改 scrcpy 直接在子模块进行，更新[来源清单](../reference/upstream-scrcpy.md)。修改 Mishka 移植同步[差异清单](../reference/upstream-mishka.md)，保留许可。提交和推送须用户授权，标题 Update、无正文；含子模块修改时先保存 fork 提交，再提交父 gitlink。

版本采用纯数字三段式并递增 versionCode；结构/文档整理本身不自动形成应用新版本。研究流程见[研究规程](procedure-research.md)。
