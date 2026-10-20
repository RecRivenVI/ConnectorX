# Android 依赖来源

依赖和共用 SDK 的唯一版本来源为 [libs.versions.toml](../../gradle/libs.versions.toml)，Gradle 取自 [Wrapper](../../gradle/wrapper/gradle-wrapper.properties)，JDK 和字节码目标取自[根构建配置](../../build.gradle.kts)。文档不重复版本数字。

| 依赖 | 用途 |
| --- | --- |
| AGP、Kotlin、Compose | Android 构建、Kotlin 编译和 Compose UI/资源 |
| Miuix UI/Preference/Blur/Icons/Nav | 统一组件、主题、外观设置与导航 |
| AndroidX Activity Compose | Android Activity、返回与 Compose 承载 |
| Shizuku API/Provider | 用户授权与独立特权服务绑定 |
| HiddenApiBypass | 已验证系统接口接入，不等于普通 APK 获得系统权限 |
| JUnit/Robolectric/Compose UI Test | 本地协议、状态机和组件回归；测试依赖不打包进产品 |

scrcpy 不是下载的桌面二进制，而是子模块固定源码参与编译，见[来源](upstream-scrcpy.md)。Mishka 移植及许可见[文件清单](upstream-mishka.md)。构建不依赖其他仓库的未提交源码。
