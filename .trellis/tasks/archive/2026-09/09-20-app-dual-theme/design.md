# Design: 明暗双主题

## Boundaries

| Module | Owns |
|--------|------|
| `:core:model` `ThemePreference` | `SYSTEM` / `LIGHT` / `DARK`；`isDark`；`fromStored` / `stored` |
| `:data:preferences` | `ProviderSettings.themePreference`；立即 `setThemePreference`；`save()` 抄回 |
| `:feature:settings` | **主题** 三选 UI（跟随系统 / 浅色 / 深色） |
| `:app` | `wrapThemeContext`；`MainActivity.attachBaseContext`；改选项后 `recreate()` |
| 六份 `DougieColors` | 仍用 `isSystemInDarkTheme()`（包装后的 `uiMode`） |
| Overlay | 不改 `#3D5198` |

不建 `:core:ui`。不加 `appcompat`。

## Contracts

```
enum class ThemePreference {
    SYSTEM, LIGHT, DARK;
    val stored: String
    fun isDark(systemDark: Boolean): Boolean
    companion object {
        fun fromStored(raw: String?): ThemePreference  // else SYSTEM
    }
}

fun wrapThemeContext(base: Context, mode: ThemePreference): Context
```

- `SYSTEM` → 原 `base`（系统 `uiMode`）。
- `LIGHT` / `DARK` → `createConfigurationContext` 把 `UI_MODE_NIGHT_*` 写进 configuration。
- 未知存储值 → `SYSTEM`。
- `save()` 与 `ttsSpeakerId` 一样抄 `store.settings.value.themePreference`。

设置：独立卡片，三枚按钮（或与音色同级的明确三选），选中 `Primary`。旁注「立即生效，不必保存配置。」

改选项：`setThemePreference` 然后 Activity `recreate()`（`savedInstanceState` 已存草稿）。不要用 `AppCompatDelegate`。

## Compatibility

色板、`MaterialTheme`、`values-night`、打字机、进入动效、`listKey` 不变。冷启动无键 = 跟随系统。

## Risks

- `recreate()` 会重走 `onCreate`；草稿必须仍走现有 `savedInstanceState`。
- `attachBaseContext` 读 `DougieApplication.preferenceStore`：Activity 的 `attachBaseContext` 在 `Application.onCreate` 之后。
- 六份 getter 仍跟 `uiMode`：漏掉 `wrapThemeContext` 时浅色强制会失败。
