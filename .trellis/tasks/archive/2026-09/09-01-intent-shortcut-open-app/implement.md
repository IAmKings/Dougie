# Implement: L2 开 App 短路径

## Checklist

1. `UserFacingErrors.APP_INTENT_NOT_ALLOWED`；`AppIntentAllowlist`/`AppIntentTool` 注入允许包名；改 tool 单测。
2. `PreferenceStore` 名单 CRUD；JVM 测 JSON 边界（空、重复 package）。
3. `IntentRouteAnswers`：`open_app`→`app_intent`；`parseShortcutArgs` 吃 alias 表；模板 `已打开{别名}。`
4. `LoopEngine` 注入 alias 表；确认短路径单测 + 未登记 package Halt。
5. `OpenApps` 页 + 设置入口；`:app` 启动器查询注入；`DougieApplication` 把 prefs 接到 Tool 与 LoopEngine。
6. 更新 `.trellis/spec/backend/directory-structure.md`、`error-handling.md`、`frontend/directory-structure.md`、`state-management.md`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:runtime:test :data:preferences:test :feature:settings:testPlayDebugUnitTest :app:checkChannelLeak
```

（若 `:data:preferences` 无 test 源集则只跑有测试的模块。）

## Risky files

- `AppIntentTool` 默认空名单会打破现有 package 单测。
- `PreferenceStore.save` 误把名单放进 `ProviderSettings` 会在保存配置时丢数据。
- Play manifest 不要加 `QUERY_ALL_PACKAGES`。
