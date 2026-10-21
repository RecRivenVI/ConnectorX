package io.github.recrivenvi.connectorx.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import org.jetbrains.compose.resources.stringResource
import io.github.recrivenvi.connectorx.shared.resources.*

@Composable
fun TaskPage(
    title: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val scroll = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    Scaffold(modifier = modifier, topBar = {
        BlurredBar(backdrop) {
            val color = if (backdrop == null) MiuixTheme.colorScheme.surface else Color.Transparent
            if (onBack == null) AdaptiveTopAppBar(title, color = color, scrollBehavior = scroll, actions = actions)
            else SmallTopAppBar(title, color = color, navigationIcon = {
                IconButton(onBack) { Icon(MiuixIcons.Back, stringResource(Res.string.ui_back)) }
            }, actions = actions)
        }
    }) { padding ->
        WideContentBox { side ->
            val list: @Composable () -> Unit = {
                LazyColumn(
                    Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                        .scrollEndHaptic().overScrollVertical()
                        .then(if (onBack == null) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier),
                    contentPadding = PaddingValues(start = side, end = side, top = padding.calculateTopPadding(), bottom = bottomPadding + 24.dp),
                    content = content
                )
            }
            if (onRefresh == null) list()
            else PullToRefresh(refreshing, onRefresh, Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding()), topAppBarScrollBehavior = scroll, content = list)
        }
    }
}

@Composable
fun StateCard(title: String, reason: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        BasicComponent(title = title, summary = reason)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}
