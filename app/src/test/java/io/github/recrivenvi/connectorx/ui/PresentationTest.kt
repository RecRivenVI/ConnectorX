package io.github.recrivenvi.connectorx.ui

import io.github.recrivenvi.connectorx.ui.devices.*
import io.github.recrivenvi.connectorx.ui.screen.settings.*
import io.github.recrivenvi.connectorx.shared.resources.*
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun videoUnitsRoundTripWithoutTruncation() {
        for (bits in listOf("100000", "100001", "8000000", "100000000")) {
            assertEquals(bits, storedRate(displayRate(bits, 6), 6))
        }
        assertEquals("100000000", storedRate("100", 6))
        assertEquals("100001", storedRate("0.100001", 6))
        assertNull(storedRate("0.1000005", 6))
    }

    @Test fun audioUsesKilobitsAndRejectsInvalidInput() {
        assertEquals("128", displayRate("128000", 3))
        assertEquals("128000", storedRate("128.000", 3))
        assertEquals("1024000", storedRate("1024", 3))
        listOf("", "NaN", "1e3", "1.0001", "999999999999999999999").forEach {
            assertNull(storedRate(it, 3))
        }
        // Out-of-range numeric input still reaches the existing MediaOptions validation.
        assertEquals("-1000000", storedRate("-1", 6))
    }

    @Test fun soundSourcesPreserveWireValuesAndOrder() {
        assertEquals(listOf("output", "playback", "mic"), SoundSource.entries.take(3).map { it.value })
        assertEquals(11, SoundSource.entries.map { it.value }.distinct().size)
        SoundSource.entries.forEach {
            assertEquals(it, SoundSource.fromValue(it.value))
            val values = io.github.recrivenvi.connectorx.protocol.MediaOptions.defaults().values().toMutableMap()
            values["audioSource"] = it.value
            assertEquals(it.value, io.github.recrivenvi.connectorx.protocol.MediaOptions.fromValues(values).audioSource)
        }
        assertNull(SoundSource.fromValue("unsupported"))
        val expected = listOf(Res.string.ui_sound_system, Res.string.ui_sound_playback, Res.string.ui_sound_mic,
            Res.string.ui_sound_unprocessed, Res.string.ui_sound_camera, Res.string.ui_sound_recognition,
            Res.string.ui_sound_communication, Res.string.ui_sound_call, Res.string.ui_sound_uplink,
            Res.string.ui_sound_downlink, Res.string.ui_sound_performance)
        assertEquals(expected, SoundSource.entries.map(::soundSourceLabel))
    }

    @Test fun activeSessionTakesPrecedenceOverDiscovery() {
        assertEquals(DevicePresence.Session, devicePresence(true, false))
        assertEquals(DevicePresence.Session, devicePresence(true, true))
        assertEquals(DevicePresence.Nearby, devicePresence(false, true))
        assertEquals(DevicePresence.Saved, devicePresence(false, false))
        assertEquals(Res.string.ui_in_use, deviceStatus(DevicePresence.Session))
        assertEquals(Res.string.ui_nearby, deviceStatus(DevicePresence.Nearby))
        assertEquals(Res.string.ui_not_connected, deviceStatus(DevicePresence.Saved))
    }

    @Test fun everyExistingPermissionStatusHasANextStep() {
        val statuses = mapOf(
            "正在连接 Shizuku…" to PermissionKind.Connecting,
            "未获 Shizuku 授权" to PermissionKind.Denied,
            "Shizuku 授权请求失败：denied" to PermissionKind.RequestFailed,
            "Shizuku 已停止；配对关系保留" to PermissionKind.Stopped,
            "特权服务已断开，请重新启用" to PermissionKind.Disconnected,
            "Shizuku 连接失败：binder" to PermissionKind.ConnectionFailed,
            "权限连接失败：identity" to PermissionKind.ConnectionFailed,
            "请先在 Shizuku 中启动服务，再返回重试" to PermissionKind.NotRunning,
            "被控端请先启用 Shizuku" to PermissionKind.NotRunning,
            "unknown" to PermissionKind.Unknown
        )
        statuses.forEach { (message, kind) -> assertEquals(kind, permissionPresentation(false, message).kind) }
        assertEquals(PermissionAction.OpenService, permissionPresentation(false, "未获 Shizuku 授权").action)
        assertEquals(PermissionAction.Reconnect, permissionPresentation(false, "特权服务已断开").action)
        assertEquals(PermissionAction.Wait, permissionPresentation(false, "正在连接 Shizuku…").action)
        assertEquals(PermissionKind.Ready, permissionPresentation(true, "权限已就绪 · uid=2000").kind)
        assertEquals(PermissionKind.ConnectionFailed, permissionPresentation(true, "权限连接失败：identity").kind)
        assertEquals(Res.string.ui_permission_ready, permissionReason(PermissionKind.Ready))
        assertEquals(Res.string.ui_permission_connecting, permissionReason(PermissionKind.Connecting))
        assertEquals(Res.string.ui_permission_denied, permissionReason(PermissionKind.Denied))
        assertEquals(Res.string.ui_permission_request_failed, permissionReason(PermissionKind.RequestFailed))
        assertEquals(Res.string.ui_permission_stopped, permissionReason(PermissionKind.Stopped))
        assertEquals(Res.string.ui_permission_stopped, permissionReason(PermissionKind.NotRunning))
        assertEquals(Res.string.ui_permission_disconnected, permissionReason(PermissionKind.Disconnected))
        assertEquals(Res.string.ui_permission_failed, permissionReason(PermissionKind.ConnectionFailed))
        assertEquals(Res.string.ui_permission_unknown, permissionReason(PermissionKind.Unknown))
    }
}
