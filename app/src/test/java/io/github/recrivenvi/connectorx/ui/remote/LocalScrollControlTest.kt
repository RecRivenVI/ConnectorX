package io.github.recrivenvi.connectorx.ui.remote

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class,qualifiers="w400dp-h900dp")
class LocalScrollControlTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val enabled=mutableStateOf(false)
    private val direction=mutableIntStateOf(0)
    private val changes=mutableListOf<Int>()

    @Before fun mount() {
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    LocalScrollControl(direction.intValue,enabled.value,{direction.intValue=it;changes.add(it)},70.dp,
                        Modifier.align(Alignment.CenterEnd).height(LocalScrollLayout.PreferredHeight).testTag("scroll-control"))
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun unmount() {activity.pause().stop().destroy()}

    @Test fun realTapsSwitchAfterSessionBecomesReady() {
        compose.runOnIdle {enabled.value=true}
        val control=compose.onNodeWithTag("scroll-control")
        control.performTouchInput {click(Offset(centerX,height/6f))}
        compose.runOnIdle {assertEquals(1,direction.intValue)}
        control.performTouchInput {click(Offset(centerX,height*5/6f))}
        compose.runOnIdle {assertEquals(-1,direction.intValue)}
        control.performTouchInput {click(center)}
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,-1,0),changes)}
    }

    @Test fun heldPointerCrossesAllThreeTabsAndReleaseKeepsLastDirection() {
        compose.runOnIdle {enabled.value=true}
        compose.onNodeWithTag("scroll-control").performTouchInput {
            down(Offset(centerX,height/6f))
            moveTo(center,delayMillis=1)
            moveTo(Offset(centerX,height*5/6f),delayMillis=1)
            up()
        }
        compose.runOnIdle {assertEquals(-1,direction.intValue);assertEquals(listOf(1,0,-1),changes)}
    }

    @Test fun disabledTouchesDoNotStartScrolling() {
        compose.onNodeWithTag("scroll-control").performTouchInput {click(Offset(centerX,height/6f))}
        compose.runOnIdle {assertEquals(0,direction.intValue);assertTrue(changes.isEmpty())}
    }

    @Test fun tappingActiveDirectionStopsAndTappingAgainRestarts() {
        compose.runOnIdle {enabled.value=true}
        val control=compose.onNodeWithTag("scroll-control")
        repeat(2) {control.performTouchInput {click(Offset(centerX,height/6f))}}
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,0),changes)}
        control.performTouchInput {click(Offset(centerX,height/6f))}
        compose.runOnIdle {assertEquals(1,direction.intValue);assertEquals(listOf(1,0,1),changes)}
    }

    @Test fun activeDirectionDragThroughStopAndBackDoesNotToggleOnRelease() {
        compose.runOnIdle {enabled.value=true;direction.intValue=1}
        val control=compose.onNodeWithTag("scroll-control")
        control.performTouchInput {down(Offset(centerX,height/6f))}
        compose.runOnIdle {assertEquals(1,direction.intValue);assertTrue(changes.isEmpty())}
        control.performTouchInput {
            moveTo(center,delayMillis=1)
            moveTo(Offset(centerX,height/6f),delayMillis=1)
            up()
        }
        compose.runOnIdle {assertEquals(1,direction.intValue);assertEquals(listOf(0,1),changes)}
    }

    @Test fun cancelledTapOnActiveDirectionDoesNotToggle() {
        compose.runOnIdle {enabled.value=true;direction.intValue=-1}
        compose.onNodeWithTag("scroll-control").performTouchInput {down(Offset(centerX,height*5/6f));cancel()}
        compose.runOnIdle {assertEquals(-1,direction.intValue);assertTrue(changes.isEmpty())}
    }

    @Test fun iconOnlyTabsRetainAccessibleClickActions() {
        compose.runOnIdle {enabled.value=true}
        compose.onNodeWithContentDescription("上滚").performClick()
        compose.runOnIdle {assertEquals(1,direction.intValue)}
        compose.onNodeWithContentDescription("停止").performClick()
        compose.runOnIdle {assertEquals(0,direction.intValue)}
        compose.onNodeWithContentDescription("下滚").performClick()
        compose.runOnIdle {assertEquals(-1,direction.intValue)}
        compose.onNodeWithContentDescription("下滚").performClick()
        compose.runOnIdle {assertEquals(0,direction.intValue)}
    }
}
