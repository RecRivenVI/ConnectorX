// Shell layout adapted from Mishka AppNavigation.kt, 52f2a7c; see third_party/licenses/Mishka-NOTICE.md.
package io.github.recrivenvi.connectorx.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.shared.resources.*
import io.github.recrivenvi.connectorx.platform.*
import io.github.recrivenvi.connectorx.ui.component.blur.*
import io.github.recrivenvi.connectorx.ui.component.liquid.IosLiquidGlassNavigationBar
import io.github.recrivenvi.connectorx.ui.devices.DevicesPage
import io.github.recrivenvi.connectorx.ui.devices.DevicesModel
import io.github.recrivenvi.connectorx.ui.library.*
import io.github.recrivenvi.connectorx.ui.screen.settings.ThemeSettingsScreen
import io.github.recrivenvi.connectorx.ui.screen.settings.*
import io.github.recrivenvi.connectorx.ui.theme.*
import io.github.recrivenvi.connectorx.ui.util.rememberIsWideScreen
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data object MediaRoute : NavKey
private data object MainRoute : NavKey
private data object ThemeRoute : NavKey

@Composable
fun AppShell(devices: DevicesModel, storage: PlatformStorage, theme: ThemeConfig,
             updateTheme: (ThemeConfig) -> Unit, predictiveBack: ((Boolean) -> Unit)?,
             library:LibraryModel,automation:AutomationModel,media:MediaSettingsModel?=null,
             backHandler:@Composable (Boolean,()->Unit)->Unit = { _,_-> }) {
    val stack=rememberSaveable(saver=Saver<androidx.compose.runtime.snapshots.SnapshotStateList<NavKey>,List<String>>(
        save={ it.map { route -> if(route==MediaRoute) "media" else if(route==ThemeRoute) "theme" else "main" } },
        restore={ mutableStateListOf<NavKey>().apply { addAll(it.mapNotNull { name -> when(name) {
            "media" -> if(media!=null) MediaRoute else MainRoute
            "theme" -> ThemeRoute
            "catalog" -> null
            else -> MainRoute
        } }) } }
    )) { mutableStateListOf<NavKey>(MainRoute) }
    val pager=rememberPagerState { 4 }
    val wide=rememberIsWideScreen()
    val main=rememberMainPagerState(pager,animatePageChanges=!wide)
    var handledRequest by rememberSaveable {mutableIntStateOf(library.request)}
    LaunchedEffect(library.request) {
        if(library.request!=handledRequest){handledRequest=library.request;main.animateToPage(0)}
    }
    LaunchedEffect(pager.currentPage) { main.syncPage() }
    var swipe by remember { mutableStateOf(storage.getString(StorageKeys.SWIPE_DISMISS,"true")=="true") }
    val direction=if(!swipe) null else if(LocalLayoutDirection.current==LayoutDirection.Rtl) NavSwipeDirection.RightToLeft else NavSwipeDirection.LeftToRight
    backHandler(stack.size==1 && main.selectedPage!=0) { main.animateToPage(0) }
    NavDisplay(backStack=stack,onBack={ if(stack.size>1) {
        stack.removeAt(stack.lastIndex)
    } },
        effects=NavDisplayEffects(cornerClipRadius=rememberNavSystemCornerRadius())) {
        entry<MainRoute> {
            MainTabs(devices,library,automation,main,theme,storage,wide,if(media!=null) {{stack.add(MediaRoute)}} else null) { if(stack.last()!=ThemeRoute) stack.add(ThemeRoute) }
        }
        entry<MediaRoute>(swipeDismiss=direction) {media?.let {MediaSettingsPage(it) {if(stack.size>1) stack.removeAt(stack.lastIndex)}}}
        entry<ThemeRoute>(swipeDismiss=direction) {
            ThemeSettingsScreen(storage,theme,updateTheme,onPredictiveBackChange=predictiveBack,
                swipeDismissEnabled=swipe,onSwipeDismissChange={ swipe=it;storage.putString(StorageKeys.SWIPE_DISMISS,it.toString()) },
                onBack={ if(stack.size>1) stack.removeAt(stack.lastIndex) })
        }
    }
}

@Composable
private fun MainTabs(devices: DevicesModel,library:LibraryModel,automation:AutomationModel,main: MainPagerState,theme: ThemeConfig,storage: PlatformStorage,
                     wide:Boolean,openMedia:(()->Unit)?,openTheme:()->Unit) {
    val items=listOf(NavigationItem("应用",MiuixIcons.AppRecording),NavigationItem("设备",MiuixIcons.Phone),
        NavigationItem("自动化",MiuixIcons.Tasks),NavigationItem(stringResource(Res.string.nav_settings),MiuixIcons.Settings))
    val content: @Composable (Modifier,androidx.compose.ui.unit.Dp)->Unit = { modifier,bottom ->
        HorizontalPager(state=main.pagerState,modifier=modifier,verticalAlignment=Alignment.Top,overscrollEffect=null) { page ->
            when(page) {0->LibraryPage(library,bottom);1->DevicesPage(devices,bottom);2->AutomationPage(automation,bottom);else->SettingsPage(bottom,openTheme,openMedia)}
        }
    }
    if(wide) {
        val rail=rememberNavigationRailState(initialValue=if(storage.getString(StorageKeys.NAV_RAIL_EXPANDED,"false")=="true") NavigationRailValue.Expanded else NavigationRailValue.Collapsed)
        LaunchedEffect(rail.currentValue) { storage.putString(StorageKeys.NAV_RAIL_EXPANDED,rail.isExpanded.toString()) }
        Scaffold { Row(Modifier.fillMaxSize()) {
            NavigationRail(state=rail) { items.forEachIndexed { index,item ->
                NavigationRailItem(selected=main.selectedPage==index,onClick={ main.animateToPage(index) },icon=item.icon,label=item.label)
            } }
            content(Modifier.weight(1f).fillMaxHeight().consumeWindowInsets(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Start))
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.End)),WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        } }
    } else {
        val backdrop=rememberBlurBackdrop(theme.blurEnabled)
        val blur=backdrop!=null
        val floatingColor=if(blur) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
        val dark=LocalAppDarkMode.current
        val floatingModifier=if(backdrop!=null) Modifier.textureBlur(backdrop,RoundedCornerShape(50.dp),25f,
            colors=BlurDefaults.blurColors(blendColors=listOf(BlendColorEntry(MiuixTheme.colorScheme.surfaceContainer.copy(alpha=0.6f)))),
            highlight=if(dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight) else Modifier
        val labels=theme.bottomBarMode==BottomBarMode.IconAndText
        Scaffold(bottomBar={
            if(theme.floatingBottomBar) {
                if(theme.floatingBottomBarStyle==FloatingBottomBarStyle.IosLike) {
                    IosLiquidGlassNavigationBar(items=items,selectedIndex=main.selectedPage,onItemClick={ main.animateToPage(it) },
                        backdrop=backdrop,isBlurActive=blur,isDark=dark,showLabels=labels)
                } else FloatingNavigationBar(modifier=floatingModifier,color=floatingColor,cornerRadius=50.dp) {
                    items.forEachIndexed { index,item -> MiuixFloatingNavigationBarItem(item,main.selectedPage==index,{ main.animateToPage(index) },labels) }
                }
            } else BlurredBar(backdrop,blur,TopBarBlurStyle.Gaussian) {
                NavigationBar(color=if(blur) Color.Transparent else MiuixTheme.colorScheme.surface,
                    mode=if(labels) NavigationBarDisplayMode.IconAndText else NavigationBarDisplayMode.IconOnly) {
                    items.forEachIndexed { index,item -> NavigationBarItem(selected=main.selectedPage==index,onClick={ main.animateToPage(index) },icon=item.icon,label=item.label) }
                }
            }
        }) { padding -> content(Modifier.fillMaxSize().then(if(backdrop!=null) Modifier.layerBackdrop(backdrop) else Modifier),padding.calculateBottomPadding()) }
    }
}
