// Theme application adapted from Mishka App.kt, 52f2a7c; see licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.recrivenvi.connectorx.ui.component.blur.LocalBlurEnabled
import io.github.recrivenvi.connectorx.ui.component.blur.LocalTopBarBlurStyle
import top.yukonga.miuix.kmp.theme.*

@Composable
fun ConnectorTheme(themeConfig: ThemeConfig, content: @Composable () -> Unit) {
    val colorSchemeMode = when {
        !themeConfig.useMonet && themeConfig.colorMode == 1 -> ColorSchemeMode.Light
        !themeConfig.useMonet && themeConfig.colorMode == 2 -> ColorSchemeMode.Dark
        !themeConfig.useMonet -> ColorSchemeMode.System
        themeConfig.colorMode == 1 -> ColorSchemeMode.MonetLight
        themeConfig.colorMode == 2 -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val isDark = themeConfig.resolveIsDark(isSystemInDarkTheme())
    val systemSeedColor = if (themeConfig.useMonet && themeConfig.accentColor == ThemeAccentColor.Default) platformDynamicColors(isDark).primary else null
    val keyColor = when {
        !themeConfig.useMonet -> null
        themeConfig.accentColor == ThemeAccentColor.Default -> systemSeedColor
        else -> themeConfig.accentColor.seedColor
    }
    val controller = remember(themeConfig, colorSchemeMode, keyColor, isDark) {
        ThemeController(colorSchemeMode=colorSchemeMode,keyColor=keyColor,colorSpec=ThemeColorSpec.Spec2025,paletteStyle=themeConfig.paletteStyle)
    }
    val colors=controller.currentColors()
    val themedColors=remember(colors,isDark,themeConfig.pureBlack) {
        if(themeConfig.useMonet && themeConfig.pureBlack && isDark) colors.copy(background=Color.Black,surface=Color.Black) else colors
    }
    MiuixTheme(colors=themedColors) {
        val currentDensity=LocalDensity.current
        val appDensity=remember(currentDensity,themeConfig.densityScale) { Density(currentDensity.density*themeConfig.densityScale,currentDensity.fontScale) }
        CompositionLocalProvider(LocalAppDarkMode provides isDark,LocalAppMonetEnabled provides themeConfig.useMonet,
            LocalPlatformDensity provides currentDensity,LocalDensity provides appDensity,
            LocalBlurEnabled provides themeConfig.blurEnabled,LocalTopBarBlurStyle provides themeConfig.topBarBlurStyle,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,content=content)
    }
}
