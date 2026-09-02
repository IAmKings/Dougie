# Design: L2 开 App 短路径

## Boundaries

| Piece | Where |
|-------|--------|
| 名单持久化 | `:data:preferences` 独立 prefs key，`StateFlow`，不进 `ProviderSettings.save` |
| 包名闸门 | `:core:tool` `AppIntentTool` / `AppIntentAllowlist` 增加 `allowedPackages: () -> Set<String>`（默认 empty） |
| 别名匹配 | `:core:runtime` `IntentRouteAnswers` + 注入 `alias -> package` 快照（`LoopEngine` 构造期 lambda，读当前名单） |
| 列表 UI | `:feature:settings` 新 `OpenAppsRoute`/`OpenAppsScreen`；`MainActivity` 增 `AppRoute.OpenApps` |
| 启动器查询 | `:app` `PackageManager` query MAIN/LAUNCHER，注入 ViewModel |

`:cli` 不接线名单；默认空 → 无 `package:` 短路径、LLM `package:` 拒绝。

## Data

每条：`alias`（trim，1–32 字）、`package`（Android 包名正则，与 Allowlist 相同）。JSON 数组。重复 package → 覆盖别名。条数不封顶。列表与添加对话框显示系统图标，并可用文字筛选。

匹配：`IntentRouteAnswers.normalize` → 若以 `请打开`/`帮我打开`/`打开` 为前缀则去掉（长的优先）→ 剩余 `==` 某 alias。多条同别名：名单中先出现的为准（持久化数组序）。

闸门：scheme `package` 的 ssp、以及 args 里非空 `package`，都 `in allowedPackages()`。未允许 → `UserFacingErrors.APP_INTENT_NOT_ALLOWED` = `该应用未加入可打开名单。`

## Data flow

```text
设置添加 → prefs
LoopEngine shortcut: classify open_app → match alias → package:uri → confirm → 已打开{alias}。
LLM ToolCall app_intent → sanitizer → validate（scheme + 用户名单）→ confirm/execute
```

## Compatibility

- `AppIntentToolTest.geoAndPackageUrisLaunch`：package 分支改为注入含 `com.android.settings` 的集合；geo 仍无名单。
- `httpsWithPackagePassesPackageToPort`：注入含 `com.android.chrome`。
- 空集合 + `package:` → `APP_INTENT_NOT_ALLOWED`（新测）。

## Risks

- `保存配置` 不得清空名单（独立 key）。
- 确认卡 `argsSummary` 含 package 名（现网 L2 已展示 args）；审计仍不含。
