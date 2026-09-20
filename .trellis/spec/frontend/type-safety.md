# Type Safety

> Kotlin types at the UI boundary. There is no TypeScript, Zod, or `@Serializable` on `AgentTask`. Feature modules consume `com.dougie.core.model` data classes and map them into UI-only types.

## Overview

- Shared domain: `:core:model` (`AgentTask`, `ConversationIds`, `ConversationHit`, `TaskStatus`, `ToolTraceEntry`, `RiskLevel`, `MemoryEntry`, `LlmVendors`, `UserFacingErrors`, `AndroidPermissions`).
- UI-only types live next to the screen (`ChatItem` sealed class in `ChatUiState.kt`, `ChatItemEnter`, `ConfirmEnter`, `ConfirmExit`, `IntelligenceMark` enum, `HistoryItem`, `HistorySection`, `HistoryToolStep`, `DebugTaskSnapshot`, `SettingsFormState`, `PermissionItem`).
- JSON at the wire/tool boundary is `kotlinx.serialization.json` (`JsonObject` / `buildJsonObject`) in `:core:runtime` / `:core:tool`, not in Compose files. Chat `prettyToolResult` in `ChatUiState.kt` pretty-prints `ToolTraceEntry.resultJson` for the ToolCallCard expand block (two-space indent, illegal JSON unchanged); it must not decode `snapshot_json` or copy `argsSummary`.
- Persistence codec is hand-written `TaskSnapshotCodec` (`ignoreUnknownKeys`). Do not switch Chat to decode `snapshot_json`.

## Type Organization

| Kind | Where | Examples |
|------|--------|----------|
| Domain | `:core:model` | `AgentTask`, `ConversationHit`, `ConversationIds`, `TaskStatus`, `UserFacingErrors` |
| Runtime handles | `:core:runtime` | `TaskManager`, `ConversationPointer`, `ConversationTitles`, `AuditEntry` |
| Feature UI | `:feature:*` | `ChatUiState`, `ChatItem`, `ChatItemEnter`, `ConfirmEnter`, `ConfirmExit`, `DebugUiState` |
| Prefs | `:data:preferences` | `ProviderSettings` |

Sealed UI lists: `ChatItem` is `UserMessage | Thinking | ToolCard | ConfirmCard | AgentMessage`. Exhaustive `when` in Chat composables. Keep `Thinking` and `ToolCard` as separate types and feed rows; do not merge them into one `ChatItem`.

Enums over stringly status in UI models: `HistoryItem.status: TaskStatus` plus a Chinese `statusLabel`. `durationLabel` / `providerLabel` / `completedAtLabel` are preformatted nullable strings (omit the meta line when all three are null). `ChatItem.AgentMessage.durationLabel` is the same preformatted nullable string from `:core:model` `formatTaskDuration` (terminal Chat only; streaming omits). Compose does not parse epoch or subtract `startedAt`/`endedAt`; `formatCompletedAt(endedAt, nowMs, zone)` owns today/昨天/date copy. `HistoryItem.steps` is `List<HistoryToolStep>` (`toolCallId`, raw `toolName`, `statusLabel` 成功/失败/进行中) mapped from `toolTrace` — never `argsSummary` / `resultJson` / risk. Debug snapshot stores `status.name` (`"FAILED"`) because it is a display string, not a second state machine. Debug Provider copy is `completionPath?.toUserLabel() ?: "无"`.

## Validation

Runtime validation is **not** in the UI layer:

- Tool args: `ToolCallSanitizer.sanitize` before `AgentTool.execute` (`INVALID_TOOL_ARGS` / `UNKNOWN_TOOL`).
- Cloud: `EgressGateway` throws `EgressBlockedException` / `MissingApiKeyException` before HTTP.
- Settings `maxTokens`: parse form text in the ViewModel; clamp uses `LlmVendors` 16..8192 on the provider config, not a Zod schema.
- Model download: `userConfirmed`, https-only, SHA-256 bijection in `ModelInstaller` / `ModelImporter` / `OfficialModelCatalog`.
- Empty chat submit: `TaskManager` no-op on blank trimmed input.

UI may disable controls (`inputEnabled`, `canCancel`, `canRetry`) from mapped state; it must not re-implement sanitizer rules.

Compare user-facing errors to `UserFacingErrors.*` constants (`intelligenceMark` remote-failure sets). Do not substring-match English.

## Common Patterns

- Mapper functions as top-level Kotlin: `fun AgentTask?.toChatUiState()`, `fun AgentTask.toHistoryItem()`, `fun formatTaskDuration()` (in `:core:model`, shared by Chat and History), `fun formatCompletedAt()`, `fun toHistorySections()`, `fun conversationDisplayName()`, `fun currentConversationTitle()`, `fun AgentTask.toDebugTaskSnapshot()`, `fun nextChatItemEnter()`, `fun ChatItem.usesBubbleEnter()`, `fun ToolTraceStatus.showsToolProgress()`, `fun chatConfirmCard()`, `fun chatFeedItemsWithoutConfirm()`, `fun nextConfirmEnter()`, `fun nextConfirmExit()`, `fun userBubbleSharedKey()`, `fun prettyToolResult()`, `fun collapsedToolResultSummary()`. Do not pass `firstKey` into `nextChatItemEnter` — empty-window send would skip enter. Do not merge `Thinking` and `ToolCard`. Do not add `stepName` to `ToolTraceEntry`; High Risk step name is `toolDisplayName` in the ToolCard title.
- `StateFlow` + `map` / `combine` + `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- `ViewModelProvider.Factory` unchecked cast is the existing DI style (no Hilt/Anvil in the project).
- `IntelligenceMark` is computed in `:app` from prefs + `task.lastError`, then passed into `ChatRoute` — Chat does not read EncryptedSharedPreferences.

## Forbidden Patterns

- `Any` / untyped `Map<String, Any>` as Chat item payloads. Use `ChatItem` / `ToolTraceEntry`.
- Decoding LLM HTTP or `snapshot_json` inside `:feature:*`.
- Treating `lastError` as free-form English and branching on `contains("timeout")`. Use `UserFacingErrors`.
- Passing intent-classifier readiness as `localLlmReady = true`. Chat soul mark uses `ChannelHooks.localChatReady` each compose (`ChatModelLayout.isPresent` on sideload only; Play is always false). Intent GGUF/ONNX is not a chat LLM.
- Adding `@Serializable` to UI state just to log it (Debug must not grow `input` / `resultJson` / `args` / conversation-hit body fields — `DebugUiStateTest` forbids those names).
- Keying Chat bubble enter off `firstKey` or `LazyItemScope.animateItem()`. Use `nextChatItemEnter` + `listKey`; Confirm is seeded into `seenKeys` but never `playKeys`. `ToolCard` stays in `playKeys` but `usesBubbleEnter()` is false (150ms fade, not 8dp). Confirm overlay uses `nextConfirmEnter` / `nextConfirmExit`; do not key either off `firstKey` or replay enter/exit on `ChatRoute` remount. Do not delay `TaskManager` for exit motion. Do not merge `ChatItem.Thinking` and `ChatItem.ToolCard`. Do not add `stepName` to `ToolTraceEntry` or a new ChatItem for progress; `showsToolProgress()` is a `ToolTraceStatus` predicate, not a DB field.
