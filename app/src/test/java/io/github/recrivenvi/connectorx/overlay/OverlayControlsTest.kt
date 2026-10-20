package io.github.recrivenvi.connectorx.overlay

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
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
class OverlayControlsTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val direction=mutableIntStateOf(0)
    private val enabled=mutableStateOf(true)
    private val changes=mutableListOf<Int>()
    private var exits=0
    private var drags=0
    private var dragEnds=0
    private var dragCancels=0

    @Before fun mount() {
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.get().setContent {MiuixTheme {
            OverlayControls(direction.intValue,enabled.value,OverlayLayout.PreferredRailHeight,{direction.intValue=it;changes.add(it)},
                {direction.intValue=0},{_,_->drags++},{dragEnds++},{exits++},onDragCancel={dragCancels++})
        }}
        compose.waitForIdle()
    }
    @After fun unmount() {activity.pause().stop().destroy()}

    @Test fun handleAndExitUseIdenticalTouchSize() {
        val handle=compose.onNodeWithContentDescription("拖动悬浮窗").fetchSemanticsNode().boundsInRoot
        val exit=compose.onNodeWithContentDescription("退出悬浮窗").fetchSemanticsNode().boundsInRoot
        assertEquals(handle.width,exit.width,0.01f)
        assertEquals(handle.height,exit.height,0.01f)
    }

    @Test fun separateExitCapsuleReceivesRealTapWithoutSelectingScrollDirection() {
        compose.onNodeWithContentDescription("上滚").performTouchInput {click(center)}
        compose.runOnIdle {assertEquals(1,direction.intValue)}
        val lower=compose.onNodeWithContentDescription("下滚").fetchSemanticsNode().boundsInRoot.bottom
        val exit=compose.onNodeWithContentDescription("退出悬浮窗")
        assertTrue(exit.fetchSemanticsNode().boundsInRoot.top>=lower)
        exit.performTouchInput {click(center)}
        compose.runOnIdle {assertEquals(1,exits);assertEquals(1,direction.intValue)}
    }
    @Test fun draggingHandleStopsScrollingAndDoesNotChooseTabs() {
        compose.onNodeWithContentDescription("下滚").performTouchInput {click(center)}
        compose.runOnIdle {assertEquals(-1,direction.intValue)}
        compose.onNodeWithContentDescription("拖动悬浮窗").performTouchInput {
            down(center)
            moveBy(Offset(0f,80f),delayMillis=100)
            moveBy(Offset(0f,80f),delayMillis=100)
            up()
        }
        compose.runOnIdle {assertEquals(0,direction.intValue);assertTrue(drags>0);assertEquals(0,exits)}
    }

    @Test fun threeTabsSelectDirectionsAndActiveDirectionTapAlsoStops() {
        val up=compose.onNodeWithContentDescription("上滚")
        val stop=compose.onNodeWithContentDescription("停止")
        val down=compose.onNodeWithContentDescription("下滚")
        stop.assertIsSelected()
        up.performTouchInput {click(center)}
        up.assertIsSelected()
        up.performTouchInput {click(center)}
        stop.assertIsSelected()
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,0),changes)}
        down.performTouchInput {click(center)}
        down.assertIsSelected()
        down.performTouchInput {click(center)}
        stop.assertIsSelected()
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,0,-1,0),changes)}
        up.performTouchInput {click(center)}
        stop.performTouchInput {click(center)}
        stop.assertIsSelected()
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,0,-1,0,1,0),changes)}
    }

    @Test fun cancelledHandleDragUsesCancellationInsteadOfRelease() {
        compose.onNodeWithContentDescription("下滚").performTouchInput {click(center)}
        compose.onNodeWithContentDescription("拖动悬浮窗").performTouchInput {
            down(center)
            moveBy(Offset(0f,80f),delayMillis=100)
            moveBy(Offset(0f,40f),delayMillis=100)
            cancel()
        }
        compose.runOnIdle {
            assertEquals(0,direction.intValue)
            assertEquals(1,dragCancels)
            assertEquals(0,dragEnds)
            assertEquals(0,exits)
        }
    }

    @Test fun rapidDragPassesThroughStopAndReleaseKeepsLastTab() {
        compose.onNodeWithContentDescription("停止").onParent().performTouchInput {
            down(Offset(centerX,height/6f))
            moveTo(center,delayMillis=1)
            moveTo(Offset(centerX,height*5/6f),delayMillis=1)
            up()
        }
        compose.runOnIdle {assertEquals(-1,direction.intValue);assertEquals(listOf(1,0,-1),changes)}
    }

    @Test fun disabledToReadyAndAccessibleSelectionUseSameThreeTabActions() {
        compose.runOnIdle {enabled.value=false}
        val up=compose.onNodeWithContentDescription("上滚")
        up.performTouchInput {click(center)}
        compose.runOnIdle {assertTrue(changes.isEmpty());enabled.value=true}
        up.performTouchInput {click(center)}
        up.assertIsSelected()
        up.performClick()
        compose.runOnIdle {assertEquals(0,direction.intValue);assertEquals(listOf(1,0),changes)}
        compose.onNodeWithContentDescription("停止").performClick()
        compose.onNodeWithContentDescription("下滚").performClick()
        compose.onNodeWithContentDescription("下滚").assertIsSelected()
        compose.runOnIdle {assertEquals(listOf(1,0,-1),changes)}
    }
}
