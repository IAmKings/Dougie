# 明暗双主题

## Goal

App 可读的明暗色（stitch 紫蓝，非青绿）。设置可选手动 **跟随系统 / 浅色 / 深色**，默认跟随系统，立即生效。

## User value

夜间可读；也可在系统浅色时强制深色，或反过来。

## Background

- 色板已落地：`DougieColors.Light` / `Dark` + `@Composable` getter；`MainActivity` `MaterialTheme`；`values-night`。亮色 `#3D5198`，暗色 `#B4C5FF` / `#191C1C`。
- 规划曾选「只跟系统、设置不加开关」。验收时用户改口：要常见三选，默认跟随系统。
- `ttsSpeakerId` 已是立即写入、`save()` 必须抄回，避免 **保存配置** 冲掉。主题用同一模式。
- 无 AppCompat。不要抽 `:core:ui`。用 `ThemePreference`（`:core:model`）+ Activity `attachBaseContext` 包一层 `Configuration`（浅/深覆盖 `uiMode`；跟随系统不包），使现有 `isSystemInDarkTheme()` getter 与 `values-night` 仍正确。改选项后 `recreate()`。
- 悬浮球仍硬编码 `#3D5198`。

## Requirements

- R1 设置 **主题**：跟随系统 / 浅色 / 深色。默认跟随系统。立即生效，不必保存配置。`save()` 必须抄当前值。
- R2 跟随系统时与系统深色一致。浅色/深色覆盖系统。
- R3 亮色 stitch `#3D5198`；暗色既有配对。不改成 PRD 青绿。
- R4 各屏与对话框走当前 `DougieColors` / `MaterialTheme`。
- R5 悬浮球 `#3D5198`。不抽 `:core:ui`。不加 AppCompat。不改打字机、动效、版本号。

## Out of scope

- 改亮色品牌为 `#006A6A`。
- `:core:ui`、AppCompat、等宽/ANSI、Compose UI 测试、商店上架、规则 D。
- 悬浮球随主题换色。

## Technical notes

- `ThemePreference` + `isDark(systemDark)` + `fromStored` 放 `:core:model`，JVM 测。存储字符串 `system` / `light` / `dark`。
- `PreferenceStore.setThemePreference` 立即写；`ProviderSettings.themeMode`；`save()` 从 store 抄回。
- `:app` `wrapThemeContext` + `MainActivity.attachBaseContext`；`DougieApplication` 冷启动不必再设全局 night API。
- 设置区块文案对齐播报音色：「立即生效，不必保存配置。」

## Acceptance Criteria

- [x] AC1 跟随系统 + 系统浅色：Chat / 设置 / 任务 现网浅底 + `#3D5198`。
- [x] AC2 跟随系统 + 系统深色：各屏深底浅字，紫蓝主色，确认/删除可辨。
- [x] AC3 设置有 **跟随系统 / 浅色 / 深色**（默认跟随系统，立即生效）。系统深色时选浅色 → App 浅色；系统浅色时选深色 → App 深色。悬浮球仍 `#3D5198`。
- [x] AC4 `ChatUiStateTest` 与打字机/进入动效不变；`DougieColorsTest` + `ThemePreference` JVM 断言过。
