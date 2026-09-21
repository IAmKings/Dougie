# Design: 终端风换肤

## Boundaries

| Module | Owns |
|--------|------|
| `:data:preferences` | `terminalTheme` Boolean，立即写，`save()` 抄回 |
| `:feature:settings` | **终端风** Switch，旁注只改对话页 |
| `:feature:chat` | `terminalTheme` 参数；开则终端色板 + 等宽 |
| `:app` | 把 prefs 传给 `ChatRoute`；不 `recreate()` |

## Contracts

```
fun ProviderSettings.copy(terminalTheme: Boolean = false)
fun PreferenceStore.setTerminalTheme(enabled: Boolean)
```

Chat 开终端风时（hex 可微调，角色不变）：

| 角色 | 色 |
|------|----|
| 背景 | `#0D0F0F`（现 `TerminalBg`） |
| 用户 | `#6EF7F6`（现 `SecondaryFixed`） |
| 助手 | `#00C853` 或接近的绿 |
| 思考/执行 | `#FFB300`（现 `StatusExecuting`） |
| 失败 | `#FF5555` |
| 次要字 | `#8E909C` |

气泡容器可用略亮的 `#1A1C1C`。正文 `FontFamily.Monospace`。顶栏/底栏/输入也走终端色，避免一条浅色底栏。

关：所有 Chat 调用保持现 `DougieColors` getter，不强制等宽（工具块维持现状等宽）。

## Compatibility

`ThemePreference` 与 `wrapThemeContext` 不动。终端风不改 `uiMode`。打字机仍切 `item.text` 前缀。

## Risks

- 只改气泡忘了 composer/顶栏：Chat 会「半终端」。
- `save()` 漏抄回会把开关打回关。
- 解析模型 ANSI 会膨胀范围；禁止。
