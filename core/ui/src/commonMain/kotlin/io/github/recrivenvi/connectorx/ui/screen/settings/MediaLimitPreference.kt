package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.Res
import io.github.recrivenvi.connectorx.shared.resources.ui_custom
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun MediaLimitPreference(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    presets: List<Pair<String, String>>,
    custom: Boolean,
    onCustomChange: (Boolean) -> Unit,
    inputLabel: String,
    modifier: Modifier = Modifier
) {
    OverlayDropdownPreference(
        title = title,
        modifier = modifier,
        items = presets.map { it.second } + stringResource(Res.string.ui_custom),
        selectedIndex = if (custom) presets.size else presets.indexOfFirst { it.first == value },
        onSelectedIndexChange = { index ->
            val selectedCustom = index == presets.size
            onCustomChange(selectedCustom)
            if (!selectedCustom) onValueChange(presets[index].first)
            else if (value.toDoubleOrNull()?.let { it > 0 } != true) onValueChange("")
        }
    )
    if (custom) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = inputLabel,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}
