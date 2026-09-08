# TLS Read error：日志定位与 0.1.1 修复

2026-09-07 用户报告 Read error。只读两台设备日志，未操作界面、授权、配对或截图。

## 已验证原因

两端日志在 `ConnectorEngine.serve → SSLSocket.startHandshake → Conscrypt CryptoUpcalls` 阶段出现：

```text
KeyStoreException: Incompatible digest
INCOMPATIBLE_DIGEST / internal Keystore code -13
Could not find provider for algorithm: NONEwithECDSA
Could not sign message in EcdsaMethodDoSign!
Read error / connection closed
```

首版 Identity 只允许 SHA256/SHA384/SHA512，没有允许 DIGEST_NONE。Conscrypt 已自行计算 TLS 消息摘要，向 Android Keystore 请求 NONEwithECDSA 签名，被密钥授权拒绝。失败位于 TLS 握手，尚未进入配对确认或 scrcpy 视频会话。

依据：[Android setDigests 文档](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...))、[Conscrypt CryptoUpcalls](https://github.com/google/conscrypt/blob/master/common/src/main/java/org/conscrypt/CryptoUpcalls.java)。DIGEST_NONE 是允许对预计算摘要签名，并非关闭 TLS 加密或散列。

## 修改

- 新密钥追加 DIGEST_NONE；每次构建 TLS 上下文先对随机摘要进行本机签名/验签自检。
- 旧密钥授权无法靠修改生成参数追溯更新。只有没有持久配对记录时才选择新 alias 生成兼容身份；保留旧 alias，不删除密钥或清除应用数据。
- 两台设备只读检查均为 0 条持久配对记录。若其他设备有既存记录，代码保留旧身份并拒绝静默迁移。
- 新错误日志记录异常栈与 TLS/协议/配对/控制阶段，避免只显示泛化 Read error。

## 验证与交付

构建、协议测试和 lint 检查通过；APK 版本 `0.1.1-prototype1` / versionCode 2。

SHA-256：`19B767D5F4DE08CF5922132D4189613380942128AEDF9179BA73EC02859BB9FF`。

相关应用 PID 的脱敏日志位于电脑端 `D:\Workspaces\Scratches\ConnectorX\tls-read-error-popsicle.log` 和 `tls-read-error-shennong.log`。没有保存无关应用日志或设备截图。

修复后的 Keystore 自检和双端 TLS 成功仍待用户打开更新后的应用、开启接收并重新发起配对；不把编译或安装结果视为运行验收通过。未创建提交。
