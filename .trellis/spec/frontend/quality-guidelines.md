# Quality Guidelines

> How Compose feature modules are verified in Dougie. Same Gradle/JUnit stack as backend; there is still no ktlint, Compose screenshot CI, or accessibility scanner in the repo.

## Overview

UI lives in `:feature:*` (Compose BOM `2024.12.01`, Material3, `lifecycle-runtime-compose`). `:app` hosts `MainActivity` routing, DI, and `DougieChatTileService`. Product copy is **Dougie** (never Waku) and Chinese for user-visible chrome.

Verification is JVM unit tests on **pure mapping functions** (`toChatUiState`, `intelligenceMark`, `toHistoryItem`, `toDebugTaskSnapshot`, `OfflineModelDownloads`). Screens themselves are not tested with Compose UI tests.

## Forbidden Patterns

- Running `LoopEngine` / OkHttp / `BatteryManager` / `CalendarContract` / `ClipboardManager` from a feature composable. Chat collects `TaskManager.task`; tools stay in `:core:tool` + `:tool:system`.
- A second `mutableStateOf(TaskStatus)` in a ViewModel. Map from `AgentTask` (see `state-management.md`).
- Showing prompts, API keys, `resultJson`, tool args, transcripts, or `snapshot_json` on Debug. `DebugUiStateTest` asserts those field names are absent.
- Auto-scanning the SAF model tree when Settings opens; auto-download without confirm; treating intent ONNX as a chat LLM (`localLlmReady` must stay false until a local **chat** model exists).
- Using `Noob-Dougie` as the launcher or as the default Chat avatar when a provider is usable. Mapping is `intelligenceMark(...)` in `:feature:chat`.
- English-only user chrome, “KISS”, or a lone “正在思考” without a loop number (`PRD` §11.1).
- A TileService in `:feature:chat`, or a Tile that calls `TaskManager.submit` / logs secrets.

## Required Patterns

- `*Route` collects `StateFlow` with `collectAsStateWithLifecycle` and forwards lambdas into a stateless `*Screen`.
- Activity-scoped ViewModels that list persisted data (`MemoryViewModel`, `HistoryViewModel`) must `refresh()` in `LaunchedEffect(Unit)` when the route is shown — `init` alone is stale after Chat writes a fact.
- Settings form is local until **保存配置**; `memoryEnabled` and `modelTreeUri` must be copied on save so they are not reset. Tree URI is also persisted immediately on folder pick.
- Offline model **测试** / download: disable other rows while probing; **取消** while in-flight; ASR/TTS timeout 90s, intent 180s; probe on `Dispatchers.Default`.
- Color tokens: duplicate `DougieColors` per feature until more than colors is shared (no `:core:ui` yet).

## Testing Requirements

| Module | What exists | Command (JDK 17) |
|--------|-------------|------------------|
| `:feature:chat` | `ChatUiStateTest`, `IntelligenceAvailableTest` | `./gradlew :feature:chat:testDebugUnitTest` |
| `:feature:settings` | `OfflineModelDownloadsTest` (confirm/tree/hash/probe) | `./gradlew :feature:settings:testDebugUnitTest` |
| `:feature:history` | `HistoryItemTest` | `./gradlew :feature:history:testDebugUnitTest` |
| `:feature:debug` | `DebugUiStateTest` (no prompt/`resultJson` leak) | `./gradlew :feature:debug:testDebugUnitTest` |
| `:app` Tile / leak | `ChatLaunchTest`; no Compose UI test for the Tile | `./gradlew :app:testPlayDebugUnitTest` and `./gradlew :app:checkChannelLeak` |

`:feature:memory` and `:feature:permissions` currently have **no** unit tests. Do not invent Compose UI tests as a bootstrap requirement. If a mapping function is added there, follow the chat/history style (JUnit on the mapper).

Play/Sideload asset leaks are an `:app` concern: `./gradlew :app:checkChannelLeak`.

## Code Review Checklist

- [ ] UI only `collect`s runtime/preference flows; no Agent loop on Main
- [ ] Failed tasks render `任务失败：$lastError` with `UserFacingErrors` copy
- [ ] Confirm Card appears only for `AWAITING_CONFIRMATION`; confirm/reject go to `TaskManager`
- [ ] Debug/History do not dump tool args or fact `content` as citations (Chat citations use `source` only)
- [ ] Settings download/probe/tree rules still match `directory-structure.md` “Don't: Let settings download without size confirm”
- [ ] Icons that are actions have Chinese `contentDescription`; decorative icons may be `null` (current Chat/Settings mix)
- [ ] QS Tile stays in `:app`, opens Chat only, and `checkChannelLeak` still requires Tile + forbids NotificationListener on Play
