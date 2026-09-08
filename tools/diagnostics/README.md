# Android 诊断源集

此目录保存可复现探针源码，不是产品功能。默认 debug/release 都不编译它们。

```powershell
./tools/Check.ps1 -Diagnostics
```

等价开关 `-PconnectorxDiagnostics=true`，仅注入 debug 源集：

- android/app/AndroidManifest.xml 与 java/：DisplaySemanticsActivity、TouchContractActivity、SessionRetentionProbe。
- android/runtime/java/：DisplaySemanticsProbe。
- DisplaySemanticsProbe 使用 Android 13+ API，入口显式检查版本；不因此提高普通 APK 的最低系统版本。

诊断 APK 的包名/签名与普通包一致，覆盖安装会重启应用，不能在未授权时中断现有连接。实际设备测试默认只用 shennong。图像只输出 PC，禁止保存 Android 文件；不修改系统设置或 SELinux。探针可能新建/销毁自己的 VD 或启动专用测试包，先检查源码参数和清理路径。

历史证据见 documents/Research 和 documents/History；已撤回的预测性返回专用探针不恢复。部分历史命令需补上本页的构建开关。正常构建前不传开关即可，勿把探针包当作正式验收包。
