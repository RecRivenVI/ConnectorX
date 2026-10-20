# 两台设备的屏幕几何只读研究

2026-09-07

## 问题

保留本主题在上述日期的调查问题、环境及证据边界；不作为当前版本能力声明。

## 方法

沿用原记录注明的固定源码审计、只读资料或有界实机实验方法；本轮仅迁移文档，没有重跑这些实验。

## 发现

日期：2026-09-07。用户允许只读获取参数/系统文件，必要时使用 Root Shell。没有更改显示设置、overlay 启用状态或 SELinux。

### 当前取得的事实

| 项目 | popsicle / 2509FPN0BC | shennong / 23116PN5BC |
| --- | --- | --- |
| 主屏 | 1200×2608，480dpi | 1440×3200，560dpi |
| 系统四角半径 | 170px | 147px |
| DisplayCutout 预留框 | x=566..634，y=0..144 | x=678..762，y=0..168 |
| SystemUI 软件圆角 | support_round_corner=false，未找到相应蒙版 | support_round_corner=true，有设备 overlay 蒙版 |
| 实际顶部 PNG | 无可用对应资源 | 1440×290，含圆角曲线和圆形前摄孔 |
| 实际底部 PNG | 无可用对应资源 | 1440×290，含底部圆角曲线 |
| 当前精度结论 | 系统半径近似；真实孔洞几何未取得 | 可按 SystemUI 蒙版像素轮廓和 alpha 还原 |

shennong 当前资源解析选中 `res/drawable-anydpi-2400x1200-v21/screen_round_corner_top.png`。顶部 PNG SHA-256：`eb972a2603dd666a6fadc8081c63e7acd87d9c3afe1c01e6933c2071ed0d6c0a`。

alpha 样本显示：顶部中央 x=719/720 的不透明范围约 y=51..132，边缘 x=0 不透明范围延伸至 y=176。这说明矩形 cutout 预留区和系统 radius=147 不能直接代替该蒙版。这里说明的是系统软件蒙版精度，未声称测量了玻璃的物理曲率。

### 系统形状参数的限制

两台 `config_mainDisplayShape` 都给出 1080×2400、20 单位圆角的通用路径，与主屏分辨率和有效圆角不一致；没有将其直接用于高精度裁切。

DisplayCutout 路径为矩形，但从顶边中点开始且含共线顶点；实测 `Path.isRect()` 不能独自识别。修正为额外检查边界、轴向线段和面积，避免把该预留区误画成刘海。相关参数与回归测试不构成人工机型数据库，产品仍读取每台设备当前资源。

### 获取方式与边界

- `wm size/density`、`cmd overlay lookup`、`dumpsys window displays` 只读核对尺寸、资源和框架状态。
- 普通 adb pull 读取 `/product/overlay/DevicesAndroidOverlay.apk`、`DevicesOverlay.apk`、`SystemUIResCommon_Sys.apk` 及 MiuiSystemUI.apk。
- `/odm/overlay/FrameworksResTarget_Vendor.apk` 的 shell 读取被拒绝；按用户授权，通过 `adb exec-out su -c 'cat <固定路径>'` 直接流式复制到电脑。两台 `su -c id` 均实际返回 UID 0 / `u:r:ksu:s0`。
- APK 新增无 UI 的 `ScreenGeometryCollector.main` 查询入口，通过 app_process 调用同一读取代码；仅输出 JSON 到电脑，无截图和 Android 文件写入。运行此探针使用 shell，不需要 Root。
- 文件和抽出的资源图片位于 `工作区 Scratches\ConnectorX\geometry\{popsicle,shennong}`，不进入 Git 源码或应用内数据库。

后续静态调查确认 popsicle 的设备专属 `com.miui.systemui.devices.overlay` 覆盖 `com.android.systemui:dimen/cut_out_center_y=81.022980px`；`DynamicIslandController` 通过对应 Display 的资源上下文读取该值并回传 `extra_back_request_cutout_y`。框架 overlay 同时给出横向 `-34..34`、宽 68px 的 cutout 预留，因此可组合为自然方向圆心 `(600, 81.022980)`、半径 `34px`。避让矩形高度 144px 仍不代表孔径。

结论：当前设备资料能为 shennong 提供来源明确的精细蒙版，并为 popsicle 提供厂商精确圆心与框架宽度组合。该 SystemUI dimen 是小米私有资源，不承诺其他设备存在；读取失败时不得推算或绘制挖孔，仍不能仅凭 Android 公共 API 恢复所有设备的真实挖孔与连续曲率。

进一步只读追踪已定位 popsicle 主屏的 DDIC 圆角配置、DSI 指令表及对应驱动函数，尚未解码为可用形状；详见 [蒙版位置追踪](../research/investigation-popsicle_mask.md)。用户选择保留 PNG 方案，本次不因发现通用 XML 而替换。

## 结论

结论以原记录的证据等级和设备范围为限。当前采用的行为见[产品范围](../project/overview-product.md)、[交互](../design/principle-interaction.md)与[进度](../project/status-current.md)，不据历史记录补写新验收。
