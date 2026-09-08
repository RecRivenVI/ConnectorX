# ADR 0008：可选 FLAG_SECURE 支持的范围

2026-09-08。状态：产品边界已确认，技术可行性 pending，未实现或启用。

用户希望远控期间可选择不受 FLAG_SECURE 截图限制影响，并明确排除 DRM、受保护缓冲区与安全媒体链路。

## 范围

- 默认关闭，不作为普通远控的必备条件；只有完成实际能力检测的设备才允许启用。
- 只针对普通应用安全窗口的显示/捕获限制，不尝试提取 DRM 内容或绕过硬件保护缓冲区。
- 优先限定于本次已认证远控会话/目标显示的捕获路径，避免永久更改应用或全局关闭设备保护。
- 退出会话或关闭选项时清理该会话的适配；应用进入后台但连接仍存在时，仍属于同一活动远控会话。
- 限制不支持时保持连接并明确报告，不能把黑屏直接判定为受保护，也不能为此自动重启目标应用。

## 待验证技术路径

Shizuku 的实际身份取决于启动方式：ADB/shell 与 Root 不应混为一谈，也不是两个互斥产品路径。需分别实测 UID、SELinux 和系统授权。

普通 shell 不应被假定拥有安全内容捕获能力。Root UID 也不能直接等同于“清除任意窗口 FLAG_SECURE”；需要验证 Android 服务的权限检查、Display 属性和捕获路径。先研究受限于会话的显示/捕获方案，再评估是否必须增加系统级适配，不直接决定使用全局 Hook 或要求安装模块。

独立应用 VD 与主屏镜像分别验收。用自建普通页面/FLAG_SECURE 页面验证权限和清理，DRM 不进入测试范围。不预先添加一个没有后端能力的可用开关。

参考：[FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)、[安全虚拟显示](https://developer.android.com/reference/android/hardware/display/DisplayManager#VIRTUAL_DISPLAY_FLAG_SECURE)。这些是平台限制说明，不是 ConnectorX 已突破限制的证据。
