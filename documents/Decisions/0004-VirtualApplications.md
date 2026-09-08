# ADR 0004：同一应用内的远程单应用原型

日期：2026-09-07；版本 `0.2.0-prototype2`；代码/本地检查与设备交互验收分开。

## 已有基础

用户确认原型 1 的画面、操控和多指触摸正常。原有 MIRROR、TLS 身份、配对记录及 Shizuku Binder 描述符桥接继续复用。音频尚未接入且不纳入本轮。

## 原型 2 决策

1. UserService 复用 scrcpy `Device.listApps()` 读取可启动应用。APP 操作用结构化包名/尺寸/dpi/刷新率，不拼接 shell 命令，不允许 scrcpy 的名称搜索或 force-stop 前缀。
   Workarounds 和查询在独立 HandlerThread 上执行，避免 Binder 线程缺少 Looper；查询等待有 15 秒上限。
2. server 使用 `new_display=WxH/DPI`、`flex_display=true`、`vd_system_decorations=false`、`vd_destroy_content=true`、`display_ime_policy=local`。Display 与 Controller 映射准备后才复用 `Device.startApp`。
3. 增加返回码及目标 Activity Display 核验。最多等待 2.5 秒查询到目标包的任务位于新 Display；无法确认或系统拒绝则返回错误，不宣称打开成功。不主动 force-stop 用户应用。
4. 在支持 VirtualDisplayConfig 的系统上请求控制端当前刷新率；不支持相应 API 时保留原创建路径并报告降级。菜单分别呈现请求率、Display 实际率、解码输出率。编码上限为请求率与 120fps 的较小值，12Mbps，最长边不再按 P1 的 1600 限制。
5. 控制端取当前窗口像素和 density，远程应用视频填满窗口并延伸到本机透明系统栏后。比例不一致时等比显示，不拉伸。Miuix 菜单提供参数查看与退出，系统 Back 发往远端；Home/最近任务/音量保留本机行为。
6. 旋转研究采用上游 flex resize 修改真实虚拟 Display 的宽高，令 Activity 接收配置变化；不是单纯旋转纹理，也不宣称 Display.rotation 必然与本机传感器值相同。保留应用方向锁定，切换时结束触点、等待视频尺寸消息后恢复输入。
7. requestPinShortcut 请求系统桌面确认。快捷方式保存随机本地记录 ID；本地记录绑定稳定设备 ID 和包名，不把 IP/TLS 私钥写入 Intent。点击后尝试 NSD 发现当前地址，再用已有配对认证连接。
8. SESSION_INFO 仅允许当前控制者查询匹配的会话 ID。实际显示状态来源于当前子进程的结构化日志，旧子进程日志不会覆盖新会话。

## 当前限制

每台被控设备仍仅一个会话；退出或视频 Surface 销毁时释放 Display 和其任务。本轮每次新建 Display，没有跨会话持久恢复远端任务。系统 Back 在远端任务根部的最终退出体验仍待用户测试，必要时使用菜单退出。被控设备当前用户的启动器应用为查询范围，未覆盖工作资料/多用户。

App 可能禁止副屏或采用 singleTask 等策略；无法核对目标 Display 时会返回错误。无法保证所有应用正常布局、受保护画面可捕获或精确刷新率。满窗口系统栏下内容的可触性、锁方向应用、Surface 重建和真实旋转效果仍待真机验收。

## 源码依据

固定 scrcpy 提交的 [virtual-display.md](https://github.com/Genymobile/scrcpy/blob/19c1261d2e2cbf2b5e6a71a8b64cc1dd3ede06ac/doc/virtual-display.md)、`NewDisplayCapture.requestResize/triggerResize`、`Device.listApps/startApp`、`Controller.onNewVirtualDisplay`。

此前文档入口曾把文件名写成 virtual_display.md；固定树实际使用连字符，上面的链接已核对。刷新率使用 [VirtualDisplayConfig.Builder](https://developer.android.com/reference/android/hardware/display/VirtualDisplayConfig.Builder#setRequestedRefreshRate(float))。快捷方式依据 [Android 官方接口](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)。源码支持不等于目标 ROM 已验证。
