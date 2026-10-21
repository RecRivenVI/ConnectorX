package io.github.recrivenvi.connectorx.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import io.github.recrivenvi.connectorx.protocol.MediaOptions
import io.github.recrivenvi.connectorx.ui.devices.*
import io.github.recrivenvi.connectorx.ui.screen.settings.MediaSettingsModel
import io.github.recrivenvi.connectorx.ui.screen.settings.MediaSettingsPage
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h900dp")
class InterfaceCorrectionsTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before
    fun createActivity() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    @After
    fun destroyActivity() {
        activity.pause().stop().destroy()
    }

    @Test
    fun customLimitRemainsEditableAtPresetAndAutomaticRestoresSentinel() {
        val defaults = MediaOptions.defaults().values()
        var saved = defaults
        activity.get().setContent {
            MiuixTheme {
                MediaSettingsPage(MediaSettingsModel(defaults, defaults) { saved = it; null }) {}
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("-1").assertDoesNotExist()
        compose.onNodeWithText("分辨率上限").performClick()
        compose.onNodeWithText("自定义").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("1280")
        // Matching a preset must not close the field while the user is typing.
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存参数"))
        compose.onNodeWithText("保存参数").performClick()
        compose.runOnIdle { assertEquals(1280, MediaOptions.fromValues(saved).videoMaxSize) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("分辨率上限").performClick()
        compose.onNodeWithText("自动（镜像 1600 px，独立应用原始尺寸）").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存参数"))
        compose.onNodeWithText("保存参数").performClick()
        compose.runOnIdle { assertEquals(-1, MediaOptions.fromValues(saved).videoMaxSize) }
    }

    @Test
    fun unlimitedFrameRateAndFractionalCustomKeepExistingStorage() {
        val defaults = MediaOptions.defaults().values()
        var saved = defaults
        activity.get().setContent {
            MiuixTheme {
                MediaSettingsPage(MediaSettingsModel(defaults, defaults) { saved = it; null }) {}
            }
        }
        compose.onNodeWithText("帧率上限").performClick()
        compose.onNodeWithText("不限").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存参数"))
        compose.onNodeWithText("保存参数").performClick()
        compose.runOnIdle { assertEquals(0f, MediaOptions.fromValues(saved).maxFps) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("帧率上限").performClick()
        compose.onNodeWithText("自定义").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("59.94")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存参数"))
        compose.onNodeWithText("保存参数").performClick()
        compose.runOnIdle { assertEquals(59.94f, MediaOptions.fromValues(saved).maxFps) }
    }

    private fun assertActionCount(title: String, count: Int) {
        compose.onAllNodes(hasClickAction() and (hasText(title) or hasAnyDescendant(hasText(title))))
            .assertCountEquals(count)
    }

    @Test
    fun offlineDetailsDisableActionsAndDiscoveryStillRespectsBusyState() {
        val presence = mutableStateOf(DevicePresence.Saved)
        val busy = mutableStateOf(false)
        activity.get().setContent {
            val peer = DeviceCard(
                "peer", "Test device", "", listOf(
                    DeviceAction("apps", "", !busy.value) {},
                    DeviceAction("mirror", "", !busy.value) {}
                ), presence.value)
            val model = DevicesModel(
                name = "Local", receiving = false, toggleReceiving = {}, enablePrivilege = {},
                pairing = false, enablePairing = {}, refresh = {}, pair = {},
                peers = listOf(peer), nearby = emptyList(), sessions = emptyList()
            )
            MiuixTheme { DeviceDetailsPage(model, "peer", {}, {}) }
        }
        assertActionCount("打开应用", 0)
        assertActionCount("设备镜像", 0)
        compose.runOnIdle { presence.value = DevicePresence.Nearby }
        assertActionCount("打开应用", 1)
        assertActionCount("设备镜像", 1)
        compose.runOnIdle { busy.value = true }
        assertActionCount("打开应用", 0)
        assertActionCount("设备镜像", 0)
    }
}
