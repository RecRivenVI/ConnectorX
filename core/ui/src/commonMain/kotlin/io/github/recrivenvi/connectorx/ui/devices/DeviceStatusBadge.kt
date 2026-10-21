package io.github.recrivenvi.connectorx.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceStatusBadge(presence: DevicePresence, modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.surfaceContainerHigh,
        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    ) {
        Text(text = stringResource(deviceStatus(presence)), style = MiuixTheme.textStyles.body2)
    }
}
