# Implement: 终端风换肤

## Order

1. `ProviderSettings.terminalTheme` 默认 false + `PreferenceStore.setTerminalTheme`；`save()` 抄回。
2. Settings **终端风** Switch（主题卡附近），立即生效，不必 `recreate()`。
3. `ChatRoute(terminalTheme: Boolean)`：开则 Chat 表面用终端色板 + 正文等宽。
4. `./gradlew :feature:chat:testDebugUnitTest`；需要的话 `:core:model:test`。

## Do not

- mosaic、ANSI escape 解析、全 App 换肤、AppCompat、`:core:ui`。
- 改打字机、`listKey`、主题三选、overlay、版本号。
- `task.py start` 前未获规划批准。
