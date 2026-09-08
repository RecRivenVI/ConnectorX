// Adapted from Mishka 52f2a7c687fd68fd2617cf6775e26661005e61d2; see licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.ui.navigation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

internal val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)
