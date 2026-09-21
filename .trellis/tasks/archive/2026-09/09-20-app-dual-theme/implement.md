# Implement: 明暗双主题（设置三选）

色板 / `MaterialTheme` / `values-night` 已在。本步只加手动主题。

## Order

1. `:core:model` `ThemePreference` + JVM：`fromStored` 空/垃圾 → SYSTEM；`isDark` 三态；`stored` 圆整。
2. `ProviderSettings.themePreference`（默认 SYSTEM）+ `PreferenceStore` 读写 `theme_preference`；`setThemePreference`；`save()` 抄回（与 `ttsSpeakerId` 相同）。
3. `:app` `wrapThemeContext`；`MainActivity.attachBaseContext` 用 Application 的 store；Settings 变更后 `recreate()`。
4. Settings **主题** 区块：跟随系统 / 浅色 / 深色，立即生效。
5. `./gradlew :core:model:test :feature:chat:testDebugUnitTest`（JDK 17）

## Do not

- AppCompat、`:core:ui`、改 overlay、改色板 hex、改打字机、升版本。
- 把主题放进「保存配置」才写入。
- 未知值当 LIGHT/DARK。
