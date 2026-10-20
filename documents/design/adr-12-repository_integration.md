# ADR 12：Android 仓库职责与来源

## 状态

已接受

## 背景

用户要求在现有工作区落实共用仓库规范，并保留已验证的 Android 能力，不重写产品。

## 决策

应用置于 app，主题与页面置于 core/ui，特权与协议分别置于 core/privilege、core/protocol；scrcpy fork 置于 third_party/scrcpy。固定包名、组件、AIDL、偏好与快捷方式不变。工具链与依赖只在 Gradle 规定位置定义，研究与探针分离，不维护自建门禁或 CI。

## 后果

来源可由 gitlink 与移植清单追溯。UI 的 Android-only KMP 源集仍是待迁移债务，本轮不改变资源包和布局。历史和结果移到工作区，当前设计和场景定义留在仓库，详见[架构](architecture-android.md)。
