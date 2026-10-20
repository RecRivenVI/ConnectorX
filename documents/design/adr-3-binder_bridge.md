# ADR 0003：同权限域 socket 与 Binder 描述符桥接

## 状态

已接受

## 背景

本记录保留当时的产品决策原文，当前规格见[架构](architecture-android.md)及[交互](principle-interaction.md)。

## 决策

日期：2026-09-07；版本 0.1.2；代码修正已落地，真机媒体与输入待用户重试。

### 已观察问题

两端 0.1.1 日志均在 12:15:48 保存了对端配对，之后 scrcpy 子进程启动，但 `LocalSocket.connect()` 遇到 `Permission denied`。SELinux audit 明确记录 shell → untrusted_app 的 `unix_stream_socket connectto` 被拒绝。重复请求出现“已有远程控制会话”，表明旧启动等待没有及时释放。

### 修正

- UserService 创建 Unix listener，子 server 在同一 shell 域反向连接；server 校验桥接者 UID/PID。连接完成后将视频/控制描述符以 `ParcelFileDescriptor[]` 经受调用方 UID 校验的 Binder 返回 APK。
- APK 不创建 Unix listener，不执行跨域 connect；继续负责 TLS 和持久授权检查，未新增明文 TCP 入口，也未更改 SELinux 策略或权限。
- listener 设置非阻塞，启动用 poll 等待，两个通道共用 10 秒截止时间；每轮检查子进程是否退出。异常关闭 listener/通道并终止子进程，API 29 被控启动明确拒绝。
- 跨 Binder 的返回描述符使用生成 AIDL 的 `PARCELABLE_WRITE_RETURN_VALUE` 语义。APK 为控制通道读写分别持有描述符；结束先 shutdown 再 close，避免仅关一个副本而读线程仍挂起。
- 启动失败在 OK 之前返回具体文本，忙碌也显式回复；APK 显示连接中及 Miuix 错误弹窗。已有 serving 状态在 finally 中释放。
- 不修改 Keystore 身份或持久配对记录。

### 验证边界

新增三项本地回归测试覆盖：子进程已死时不再 poll、第二通道不重置共享截止时间、poll 失败向上传播且后续等待可成功。它们验证等待逻辑，不等同于 Android Binder/SELinux 或真实视频通道通过。

参考系统策略明确区分已连接描述符的读写和 connectto：[app.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.0/private/app.te)、[shell.te](https://github.com/LineageOS/android_system_sepolicy/blob/lineage-23.0/private/shell.te)。这些源码是设计依据，不能替代两台 Xiaomi ROM 的实测。实际桥接、解码、输入与断连回收仍由用户操作确认。

## 后果

仅在上列状态和当前规格适用范围内继续执行，不把历史实验、提议或构建证据扩展为当前端到端能力。
