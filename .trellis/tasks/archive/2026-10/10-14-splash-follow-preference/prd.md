# 启动窗跟随应用主题偏好

## Goal

用户问「为什么应用内浅色设置不能让本应用变更为浅色模式」——机制：启动窗由系统在进程启动前绘制，App 偏好存于私有存储系统读不到，系统只按系统深浅模式解析。修复：用 API 33+ 的 `UiModeManager.setApplicationNightMode()` 把应用偏好写入**系统级按应用深浅模式设置**——写入后系统连启动窗都按偏好渲染，冷启动黑/白底跟随应用偏好（设备 Android 16 ≥ 33 ✓）。

## Confirmed facts

- SDK android-36 确认存在 `UiModeManager.setApplicationNightMode(int)` 与 MODE_NIGHT_NO/YES/AUTO 常量（javap 验证）。
- 应用已有 `wrapThemeContext`（attachBaseContext 按 uiMode 覆写配置）使应用内全部跟随偏好；系统级覆盖写入后与其叠加——应用内不受影响（wrapThemeContext 优先），启动窗受益。
- MODE_NIGHT_AUTO 恰好对应「跟随系统」档。
- 同步时机：①偏好变更时（设置页）；②应用启动时（收敛一次，覆盖升级安装后的存量偏好）。

## Requirements

- R1 app main 新增纯映射函数 `systemNightModeFor(mode: ThemePreference): Int`：LIGHT→MODE_NIGHT_NO、DARK→MODE_NIGHT_YES、SYSTEM→MODE_NIGHT_AUTO。
- R2 `DougieApplication.syncSystemNightMode()`：SDK≥33 时经 `getSystemService(UI_MODE_SERVICE)` 调 `setApplicationNightMode(systemNightModeFor(当前偏好))`，runCatching 包裹；onCreate 调用一次。
- R3 MainActivity.onThemePreferenceChange 在 recreate() 前同步调用。
- R4 33 以下版本静默跳过（不依赖）；不动 14 条冻结契约；无新增日志。

## Acceptance Criteria

- [x] AC1 JVM 测试：三档映射正确。
- [x] AC2 双 flavor 编译 + 既有测试绿。
- [x] AC3 真机：系统深色 + App 浅色 → 冷启动启动窗为浅色底（第二次启动起生效；首次启动完成同步）；设置内切换偏好后启动窗跟随。

## Follow-up（验收失败终审：对照实验）

用户验收失败（仍黑底）。adb 取证三重确认：①冷启动后 Dougie 进程系统侧配置 = `port night`（浅色覆盖未落地）；②`dumpsys uimode` 无任何按应用覆盖记录；③`cmd uimode` 无按应用子命令。**对照实验**：同设备冷启动 Anchor（同款 setApplicationNightMode 实现），其进程配置同样 `port night`——Anchor 在此 ColorOS 上冷启动启动窗同样跟随系统、不跟随偏好。

**终审结论**：ColorOS（PJZ110, Android 16）的 UiModeManager 静默忽略 setApplicationNightMode（无持久化、无配置生效、无异常）。 Dougie 实现与 Anchor 同款、无误；启动窗跟随系统深浅为 OEM 平台行为，应用代码不可覆盖。用户对「Anchor 可跟随」的观察可能来自暖启动（进程内存活时应用内即时切换，两家均支持）或测试时系统恰为浅色。

**自助验证步骤**（供用户复核）：Anchor 偏好设为「浅色」+ 系统保持深色 + 强杀后冷启动 Anchor → 预期启动窗仍为黑色。若实际为白色，请截图，再深挖两应用差异。

**保留**：setApplicationNightMode 代码保留（原生系统有效、ColorOS 无害）；应用内体验（wrapThemeContext 跟随偏好）不受影响。

## Follow-up 2（应用户要求深挖 Anchor 代码后的终版结论）

应用户要求完整审阅 Anchor 全部代码（47 个 Kotlin 文件清单化、MainActivity 949 行主题相关全部命中、四套 styles + colors + manifest + 无 Application 类 + 无 splash 库 + 无 attachBaseContext / windowDisablePreview）：**Anchor 没有任何 Dougie 未实现的机制**。其启动窗跟随偏好依赖 setApplicationNightMode（与 Dougie 落地方式逐行等价）。

设备侧取证闭环：Dougie 与 Anchor 冷启动后进程配置均为 night（系统深色）；ColorOS 的 dumpsys uimode / cmd uimode 均无按应用覆盖接口；调用无异常但无效果——ColorOS 静默忽略该 API（两者同被忽略）。

「Anchor 跟随、Dougie 不跟随」感知差异的真实来源（按可能性排序）：
1. 暖启动观察：进程存活时切主题，Anchor 界面即时变化（configChanges=uiMode + Compose 状态），随即重开看到的是应用内画面而非系统启动窗；
2. 测试时系统恰为浅色（跟随系统与跟随偏好不可区分）；
3. 启动速度差异：Anchor 轻量启动窗一闪而过不可感知；DougieApplication 重初始化使黑底+Logo 停留数秒格外显眼——这才是 Dougie 侧真正可做的改善点（启动性能优化，独立任务）。

**终态**：启动窗配色跟随系统深浅为 OEM 平台行为，应用代码不可覆盖；Dougie 保留 setApplicationNightMode（原生系统有效）；可选改善=启动性能优化缩短启动窗可见时长；用户侧可选 ColorOS 按应用深色设置（如有）。

## Out of scope

- API 33 以下设备（跟随系统，维持现状）；core-splashscreen 品牌化启动窗（可另立）。
