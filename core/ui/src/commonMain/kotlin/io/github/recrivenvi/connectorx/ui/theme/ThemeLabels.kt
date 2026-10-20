// Adapted from Mishka 52f2a7c687fd68fd2617cf6775e26661005e61d2; see third_party/licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.ui.theme

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import io.github.recrivenvi.connectorx.shared.resources.*
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

// 主题枚举 → 用户可见名称的唯一映射，避免多处 when 漂移

/** colorMode 的取值，顺序即选项列表顺序：0 跟随系统 / 1 浅色 / 2 深色 */
val ThemeColorModes = listOf(0, 1, 2)

@Composable
fun themeColorModeLabel(colorMode: Int): String = stringResource(
    when (colorMode) {
        1 -> Res.string.settings_theme_light
        2 -> Res.string.settings_theme_dark
        else -> Res.string.settings_theme_system
    },
)

@Composable
fun ThemeAccentColor.label(): String = stringResource(
    when (this) {
        ThemeAccentColor.Default -> Res.string.settings_theme_accent_default
        ThemeAccentColor.Blue -> Res.string.settings_theme_accent_blue
        ThemeAccentColor.Purple -> Res.string.settings_theme_accent_purple
        ThemeAccentColor.Pink -> Res.string.settings_theme_accent_pink
        ThemeAccentColor.Red -> Res.string.settings_theme_accent_red
        ThemeAccentColor.Orange -> Res.string.settings_theme_accent_orange
        ThemeAccentColor.Yellow -> Res.string.settings_theme_accent_yellow
        ThemeAccentColor.Green -> Res.string.settings_theme_accent_green
        ThemeAccentColor.Teal -> Res.string.settings_theme_accent_teal
    },
)

@Composable
fun ThemePaletteStyle.label(): String = stringResource(
    when (this) {
        ThemePaletteStyle.Neutral -> Res.string.settings_theme_palette_neutral
        ThemePaletteStyle.Vibrant -> Res.string.settings_theme_palette_vibrant
        ThemePaletteStyle.Expressive -> Res.string.settings_theme_palette_expressive
        ThemePaletteStyle.Rainbow -> Res.string.settings_theme_palette_rainbow
        ThemePaletteStyle.FruitSalad -> Res.string.settings_theme_palette_fruit_salad
        ThemePaletteStyle.Monochrome -> Res.string.settings_theme_palette_monochrome
        ThemePaletteStyle.Fidelity -> Res.string.settings_theme_palette_fidelity
        ThemePaletteStyle.Content -> Res.string.settings_theme_palette_content
        else -> Res.string.settings_theme_palette_tonal_spot
    },
)

@Composable
fun FloatingBottomBarStyle.label(): String = stringResource(
    when (this) {
        FloatingBottomBarStyle.Miuix -> Res.string.settings_theme_floating_bottom_bar_style_miuix
        FloatingBottomBarStyle.IosLike -> Res.string.settings_theme_floating_bottom_bar_style_ios_like
    },
)

@Composable
fun BottomBarMode.label(): String = stringResource(
    when (this) {
        BottomBarMode.IconAndText -> Res.string.settings_theme_bottom_bar_icon_and_text
        BottomBarMode.IconOnly -> Res.string.settings_theme_bottom_bar_icon_only
    },
)

@Composable
fun TopBarBlurStyle.label(): String = stringResource(
    when (this) {
        TopBarBlurStyle.Gaussian -> Res.string.settings_theme_blur_style_gaussian
        TopBarBlurStyle.Progressive -> Res.string.settings_theme_blur_style_progressive
    },
)

@Composable
fun TopBarBlurStyle.summary(): String = stringResource(
    when (this) {
        TopBarBlurStyle.Gaussian -> Res.string.settings_theme_blur_style_gaussian_summary
        TopBarBlurStyle.Progressive -> Res.string.settings_theme_blur_style_progressive_summary
    },
)
