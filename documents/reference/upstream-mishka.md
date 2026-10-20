# Mishka 移植来源

固定来源为 [RecRivenVI/Mishka](https://github.com/RecRivenVI/Mishka/tree/52f2a7c687fd68fd2617cf6775e26661005e61d2)，不随其当前工作树自动更新。以下路径相对该提交；本仓库只读参考，不修改来源仓库。

## 文件与差异

| 来源文件 | 本仓库文件 | 差异 |
| --- | --- | --- |
| `app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/ThemeSettingsScreen.kt` | [ThemeSettingsScreen.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/screen/settings/ThemeSettingsScreen.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/theme/ThemeConfig.kt` | [ThemeConfig.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/theme/ThemeConfig.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/theme/ThemeLabels.kt` | [ThemeLabels.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/theme/ThemeLabels.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/theme/AppThemeLocals.kt` | [AppThemeLocals.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/theme/AppThemeLocals.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/GroupedCardItems.kt` | [GroupedCardItems.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/GroupedCardItems.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/AdaptiveTopAppBar.kt` | [AdaptiveTopAppBar.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/AdaptiveTopAppBar.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/blur/BlurExt.kt` | [BlurExt.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/blur/BlurExt.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/util/WindowSize.kt` | [WindowSize.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/util/WindowSize.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/navigation/PagerNavigationSpring.kt` | [PagerNavigationSpring.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/navigation/PagerNavigationSpring.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/liquid/CombinedBackdrop.kt` | [CombinedBackdrop.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/liquid/CombinedBackdrop.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/liquid/InnerShadow.kt` | [InnerShadow.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/liquid/InnerShadow.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/liquid/Lens.kt` | [Lens.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/liquid/Lens.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/liquid/LiquidGlassNavigationBar.kt` | [LiquidGlassNavigationBar.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/liquid/LiquidGlassNavigationBar.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/liquid/Vibrancy.kt` | [Vibrancy.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/liquid/Vibrancy.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/animation/DampedDragAnimation.kt` | [DampedDragAnimation.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/animation/DampedDragAnimation.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/component/animation/InteractiveHighlight.kt` | [InteractiveHighlight.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/component/animation/InteractiveHighlight.kt) | 包名、导入、资源引用与来源注释替换；主体保留 |
| `app/src/main/kotlin/top/yukonga/mishka/App.kt` | [ConnectorTheme.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/theme/ConnectorTheme.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/navigation/AppNavigation.kt` | [AppShell.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/navigation/AppShell.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/navigation/AppNavigation.kt` | [MainPagerState.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/navigation/MainPagerState.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |
| `app/src/main/kotlin/top/yukonga/mishka/ui/navigation/AppNavigation.kt` | [FloatingNavigationItem.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/ui/navigation/FloatingNavigationItem.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |
| `app/src/main/kotlin/top/yukonga/mishka/MishkaApplication.kt` | [LocalBackSettings.kt](../../app/src/main/java/io/github/recrivenvi/connectorx/platform/LocalBackSettings.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |
| `app/src/main/kotlin/top/yukonga/mishka/platform/PlatformStorage.kt` | [PlatformStorage.kt](../../core/ui/src/commonMain/kotlin/io/github/recrivenvi/connectorx/platform/PlatformStorage.kt) | 选取片段并适配生命周期/存储/导航，非整文件相同 |

## 文案

| 来源资源 | 本仓库资源 | 保留范围 |
| --- | --- | --- |
| `app/src/main/res/values/strings.xml` | [对应资源](../../core/ui/src/commonMain/composeResources/values/strings.xml) | 指定主题/通用键共 60 项，文案保留 |
| `app/src/main/res/values-ru/strings.xml` | [对应资源](../../core/ui/src/commonMain/composeResources/values-ru/strings.xml) | 指定主题/通用键共 60 项，文案保留 |
| `app/src/main/res/values-zh-rCN/strings.xml` | [对应资源](../../core/ui/src/commonMain/composeResources/values-zh-rCN/strings.xml) | 指定主题/通用键共 60 项，文案保留 |
| `app/src/main/res/values-zh-rTW/strings.xml` | [对应资源](../../core/ui/src/commonMain/composeResources/values-zh-rTW/strings.xml) | 指定主题/通用键共 60 项，文案保留 |

“外观与主题”正文、选项顺序、条件展开及对应文案依固定基线保留。当前四栏导航为本产品的任务入口；Theme/导航片段的应用适配不是 Mishka 整文件复制。Android 偏好存于 connectorx_appearance，不改变配对和逐包名设置。

原来源哈希清单归档于工作区 Artifacts/ConnectorX/History/RepositoryStandard-1.0.0/licenses/Mishka-sources.json；当前映射以本页为准，不维护第二份机器清单。

## 许可证

Mishka 项目采用 GPL-3.0，显式 Apache-2.0 文件保留原头、compose-miuix-ui 与 Kyant0/AndroidLiquidGlass 署名。完整文本与通知见 [third_party/licenses](../../third_party/licenses/Mishka-NOTICE.md)，构建从该处生成 APK assets。移植不替未注明许可的本项目原创部分擅自选择许可证。
