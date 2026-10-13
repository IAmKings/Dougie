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

- [ ] AC1 JVM 测试：三档映射正确。
- [ ] AC2 双 flavor 编译 + 既有测试绿。
- [ ] AC3 真机：系统深色 + App 浅色 → 冷启动启动窗为浅色底（第二次启动起生效；首次启动完成同步）；设置内切换偏好后启动窗跟随。

## Out of scope

- API 33 以下设备（跟随系统，维持现状）；core-splashscreen 品牌化启动窗（可另立）。
