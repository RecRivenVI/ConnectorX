// Adapted from Mishka 52f2a7c687fd68fd2617cf6775e26661005e61d2; see licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

val LocalAppDarkMode = staticCompositionLocalOf { false }
val LocalAppMonetEnabled = staticCompositionLocalOf { false }
val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
