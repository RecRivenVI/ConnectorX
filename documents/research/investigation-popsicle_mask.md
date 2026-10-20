# popsicle 蒙版位置追踪：面板驱动路径

2026-09-07

## 问题

保留本主题在上述日期的调查问题、环境及证据边界；不作为当前版本能力声明。

## 方法

沿用原记录注明的固定源码审计、只读资料或有界实机实验方法；本轮仅迁移文档，没有重跑这些实验。

## 发现

日期：2026-09-07。用户倾向保留 PNG，要求继续查找 popsicle 的真实蒙版。此轮只读调查，没有修改应用、安装包、设备设置或 SELinux。

### 结论边界

**已定位到实际主屏型号对应的 DDIC 圆角配置和驱动调用，但尚未取得可直接用于 ConnectorX 的 PNG 或已解码轮廓。** 不能把找到控制指令表等同于找到完整蒙版，也不能断言摄像头孔洞一定包含在圆角表中。

### 设备与 SystemUI 证据

- `/sys/class/mi_display/disp-DSI-0/panel_info` 返回 `panel_name=mdss_dsi_p2_42_02_0a_prim_dsc_cmd`。
- 当前 SystemUI 资源 `support_prop_round_corner=false`、`support_round_corner=false`；`sys.display.rounded_corner_type=0`，强制软件圆角调试属性未设置。
- 从这台设备的 MiuiSystemUI.apk 定向反汇编 `ScreenDecorations`、其初始化 lambda 和 `RoundedCornerResDelegateImpl`：只有 `mSupportRoundCorner` 或强制调试标志为真时，才创建圆角 provider overlay；为假时清理/跳过对应 overlay。
- 同包内确实存在 `rounded_corner_top/bottom.xml` 及资源数组，但存在资源不等于此配置正在使用它绘制主屏。

### 已找到的具体位置

主屏型号对应的设备树目录：

```text
/sys/firmware/devicetree/base/soc/qcom,mdss_mdp@9800000/qcom,mdss_dsi_p2_42_02_0a_prim_dsc_cmd/
```

该目录中存在布尔配置 `mi,ddic-round-corner-enabled`。本次读取的一个时序节点为：

```text
qcom,mdss-dsi-display-timings/timing@wqhd_normal_60hz_index_00/
```

| 属性 | 大小 | 解析结果 |
| --- | --- | --- |
| `mi,mdss-dsi-round-corner-on-command` | 85 bytes | 6 个 DSI 数据包 |
| `mi,mdss-dsi-round-corner-off-command` | 22 bytes | 2 个 DSI 数据包 |
| `qcom,mdss-dsi-on-command` | 3542 bytes | 258 个 DSI 数据包 |

圆角开启表选择寄存器页 `F0 55 AA 52 08 07`，包含 B4、B8、C0 写入；关闭表在相同页写入 `C0 86`。面板初始化表包含同页的 C1–D2 数据组。

这批 C1–D2 数据是后续分析的候选配置，**尚未证明每组都是几何系数，更没有根据猜测生成形状**。需要对应 DDIC 的寄存器定义或等效解码依据。

### 驱动交叉核对

`/vendor_dlkm/lib/modules/msm_drm.ko` 包含上述设备树属性及函数 `mi_dsi_panel_set_round_corner_locked`。其定向反汇编显示检查功能标志后调用 `dsi_panel_tx_cmd_set`，按开关参数选择两个命令集合。这证明该配置关联面板 DSI 控制，而非 PNG 文件加载。

当前 `disp_param` 中 `ddic_round_corner[21]=0` 只作为读出的状态字段记录；未核实它是否表示硬件当前状态、缓存请求或默认值，不能据此宣布 DDIC 遮罩正开启或关闭。

另检查了 `/vendor/lib64/librcmask.so` 与 `libsdmextension.so`：存在 RC 图层压缩/JSON 支持，但本轮没有证据表明其对应一份当前使用的 popsicle 蒙版。不能因库存在就认为已找到数据来源。

### 已检查范围

- 已提取的 SystemUI 及设备 overlay；补充的 framework-res、framework-ext-res、XmsCore 和相关 framework/SystemUI overlay。
- vendor/odm 显示配置目录、命名相关资源、当前显示服务加载库，以及显示专属持久化目录的文件名。
- 实际 SystemUI 关键类、原生显示库、内核显示模块、主屏型号和设备树属性。

没有开启厂商 mask dump 属性，没有执行 panel 初始化脚本或向 MIPI/sysfs 接口写入命令。原始控制表仅复制到电脑，不发送给面板。

### 留存

- 全部调查中间文件：`工作区 Scratches\ConnectorX\geometry\popsicle\followup`。
- 关键二进制表、DSI 分包 JSON、源路径、驱动函数反汇编和参考摘要：`工作区 Artifacts\ConnectorX\PopsicleMaskInvestigation`。

当前较有依据的方向是继续解析面板侧配置，而非假定 Android 17 把 PNG 统一搬到了另一个目录。现有 PNG 方案保留，popsicle 的精度状态不因这次路径发现而提升为完整几何恢复。

## 结论

结论以原记录的证据等级和设备范围为限。当前采用的行为见[产品范围](../project/overview-product.md)、[交互](../design/principle-interaction.md)与[进度](../project/status-current.md)，不据历史记录补写新验收。
