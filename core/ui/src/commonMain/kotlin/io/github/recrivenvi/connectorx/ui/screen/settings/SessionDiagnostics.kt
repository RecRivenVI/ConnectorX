package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.runtime.staticCompositionLocalOf

/** Display preference only; disabled until the user explicitly enables it in Settings. */
val LocalSessionDiagnostics = staticCompositionLocalOf { false }
