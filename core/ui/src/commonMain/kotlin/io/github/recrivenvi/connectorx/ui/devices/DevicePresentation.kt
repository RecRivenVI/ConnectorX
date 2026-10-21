package io.github.recrivenvi.connectorx.ui.devices

import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.StringResource

enum class DevicePresence { Session, Nearby, Saved }

fun devicePresence(hasSession: Boolean, nearby: Boolean): DevicePresence = when {
    hasSession -> DevicePresence.Session
    nearby -> DevicePresence.Nearby
    else -> DevicePresence.Saved
}

enum class PermissionAction { OpenService, Reconnect, Wait }
enum class PermissionKind {
    Ready, Connecting, Denied, RequestFailed, Stopped, Disconnected, ConnectionFailed, NotRunning, Unknown
}

data class PermissionPresentation(val kind: PermissionKind, val action: PermissionAction)

/** Maps the existing client's statuses; raw UID/identity and exception text stay out of primary UI. */
fun permissionPresentation(serviceAvailable: Boolean, status: String): PermissionPresentation {
    val kind = when {
        status.startsWith("权限连接失败") || status.startsWith("Shizuku 连接失败") -> PermissionKind.ConnectionFailed
        serviceAvailable -> PermissionKind.Ready
        status.startsWith("正在连接") -> PermissionKind.Connecting
        status.startsWith("未获 Shizuku 授权") -> PermissionKind.Denied
        status.startsWith("Shizuku 授权请求失败") -> PermissionKind.RequestFailed
        status.startsWith("Shizuku 已停止") -> PermissionKind.Stopped
        status.startsWith("特权服务已断开") -> PermissionKind.Disconnected
        status.startsWith("请先在 Shizuku 中启动") || status.startsWith("被控端请先启用") -> PermissionKind.NotRunning
        else -> PermissionKind.Unknown
    }
    val action = when (kind) {
        PermissionKind.Connecting, PermissionKind.Ready -> PermissionAction.Wait
        PermissionKind.ConnectionFailed, PermissionKind.Disconnected, PermissionKind.Unknown -> PermissionAction.Reconnect
        else -> PermissionAction.OpenService
    }
    return PermissionPresentation(kind, action)
}

fun permissionReason(kind: PermissionKind): StringResource = when (kind) {
    PermissionKind.Ready -> Res.string.ui_permission_ready
    PermissionKind.Connecting -> Res.string.ui_permission_connecting
    PermissionKind.Denied -> Res.string.ui_permission_denied
    PermissionKind.RequestFailed -> Res.string.ui_permission_request_failed
    PermissionKind.Stopped, PermissionKind.NotRunning -> Res.string.ui_permission_stopped
    PermissionKind.Disconnected -> Res.string.ui_permission_disconnected
    PermissionKind.ConnectionFailed -> Res.string.ui_permission_failed
    PermissionKind.Unknown -> Res.string.ui_permission_unknown
}

fun deviceStatus(presence: DevicePresence): StringResource = when (presence) {
    DevicePresence.Session -> Res.string.ui_in_use
    DevicePresence.Nearby -> Res.string.ui_nearby
    DevicePresence.Saved -> Res.string.ui_not_connected
}

fun sessionTypeLabel(type: SessionType): StringResource = when (type) {
    SessionType.LocalApplication -> Res.string.ui_session_type_local
    SessionType.RemoteApplication -> Res.string.ui_session_type_app
    SessionType.DeviceMirror -> Res.string.ui_session_type_mirror
    SessionType.ScreenControl -> Res.string.ui_session_type_control
}
