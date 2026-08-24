# Design — :cli

## Boundaries

- **`:cli`** 拥有 `main`、终端 UI / 日志降级、把 `TaskManager.task` 画到屏幕。
- **`:core:runtime`** 不改公共契约除非发现无法从 CLI 注入 dispatcher（预期不改）。
- 不把 Fake 再复制一份；用 `com.dougie.core.llm.FakeLlmProvider` 与 `com.dougie.core.tool.FakeBatteryTool`。

## Wiring

```
CliMain
  → LoopEngine(FakeLlmProvider, mapOf("battery" to FakeBatteryTool), Dispatchers.Default, stepDelayMs=短)
  → TaskManager(engine, dispatcher, scope)
  → submit("我现在手机还有多少电？") 或 CLI 参数 / 一行 stdin
  → collect task StateFlow
       mosaic Text 列：status, loopCount, toolTrace, finalAnswer
       else println
```

确认卡：本切片不注册 L2 Tool，不接 `LoopEngine.confirm()`。

Egress：Fake `isLocal=true`，不走云端。

## Gradle

- `plugins { kotlin.jvm; application }`
- `application { mainClass = "com.dougie.cli.CliKt" }`（或 `CliMainKt`）
- mosaic：`com.jakewharton.mosaic:mosaic-runtime` **0.14.0** in `libs.versions.toml`（Kotlin 2.0.20 metadata; matches repo Kotlin 2.0.21）
- **Do not use mosaic 0.18.0**: it is compiled with Kotlin 2.2; K2 2.0.21 cannot read that metadata
- 0.14 has no `NonInteractivePolicy` (that landed in 0.17+). Non-TTY / mosaic exceptions fall back to the same stdout snapshot as `--log-only`
- kotlinx-cli：写入 version catalog（`ArgType.Boolean` is a flag, `hasParameter=false`, so `--log-only` needs no `true` value）
- `:app` 不 `implementation(project(":cli"))`

## Compatibility

- macOS/Linux 开发机为验收环境；Windows cmd 走 `--log-only`。
- mosaic 与 AGP 共存：`:cli` 不用 Android plugin，避免 Compose Android 编译器打进 CLI。Apply `org.jetbrains.kotlin.plugin.compose` on `:cli` only (needed for mosaic `@Composable`). mosaic 自带 Compose Runtime for terminal，不要用 AndroidX Compose BOM 当 mosaic 依赖。

## Rollback

删除 `cli/`、`settings.gradle.kts` 的 include、catalog 条目即可；不迁数据。
