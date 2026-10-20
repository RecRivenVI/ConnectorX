package io.github.recrivenvi.connectorx.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.ui.component.*
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.util.WideContentBox
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsPage(bottomPadding:Dp,onTheme:()->Unit,onMedia:(()->Unit)?=null) {
    val scroll=MiuixScrollBehavior()
    val backdrop=rememberBlurBackdrop()
    Scaffold(topBar={ BlurredBar(backdrop) {
        AdaptiveTopAppBar(stringResource(Res.string.settings_title),color=if(backdrop!=null) Color.Transparent else MiuixTheme.colorScheme.surface,scrollBehavior=scroll)
    } }) { inner -> WideContentBox { side ->
        LazyColumn(Modifier.fillMaxSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier)
            .scrollEndHaptic().overScrollVertical().nestedScroll(scroll.nestedScrollConnection),
            contentPadding=PaddingValues(start=side,end=side,top=inner.calculateTopPadding(),bottom=bottomPadding)) {
            item { Spacer(Modifier.height(12.dp)) }
            groupedCardItems("settings",listOf(CardItem("theme") {
                ArrowPreference(title=stringResource(Res.string.settings_theme_title),summary=stringResource(Res.string.settings_theme_summary),onClick=onTheme)
            }))
            if(onMedia!=null) groupedCardItems("media",listOf(CardItem("entry") {
                ArrowPreference(title="远控画面与音频",summary="帧率、码率、视频编码、音频来源与编码",onClick=onMedia)
            }))
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    } }
}
