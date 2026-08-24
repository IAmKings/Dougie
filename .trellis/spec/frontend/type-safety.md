# Type Safety

> Kotlin types at the UI boundary. There is no TypeScript, Zod, or `@Serializable` on `AgentTask`. Feature modules consume `com.dougie.core.model` data classes and map them into UI-only types.

## Overview

- Shared domain: `:core:model` (`AgentTask`, `TaskStatus`, `ToolTraceEntry`, `RiskLevel`, `MemoryEntry`, `LlmVendors`, `UserFacingErrors`, `AndroidPermissions`).
- UI-only types live next to the screen (`ChatItem` sealed class in `ChatUiState.kt`, `IntelligenceMark` enum, `HistoryItem`, `DebugTaskSnapshot`, `SettingsFormState`, `PermissionItem`).
- JSON at the wire/tool boundary is `kotlinx.serialization.json` (`JsonObject` / `buildJsonObject`) in `:core:runtime` / `:core:tool`, not in Compose files.
- Persistence codec is hand-written `TaskSnapshotCodec` (`ignoreUnknownKeys`). Do not switch Chat to decode `snapshot_json`.

## Type Organization

| Kind | Where | Examples |
|------|--------|----------|
| Domain | `:core:model` | `AgentTask`, `TaskStatus`, `UserFacingErrors` |
| Runtime handles | `:core:runtime` | `TaskManager`, `AuditEntry` |
| Feature UI | `:feature:*` | `ChatUiState`, `ChatItem`, `DebugUiState` |
| Prefs | `:data:preferences` | `ProviderSettings` |

Sealed UI lists: `ChatItem` is `UserMessage | Thinking | ToolCard | ConfirmCard | AgentMessage`. Exhaustive `when` in Chat composables.

Enums over stringly status in UI models: `HistoryItem.status: TaskStatus` plus a Chinese `statusLabel`. Debug snapshot stores `status.name` (`"FAILED"`) because it is a display string, not a second state machine.

## Validation

Runtime validation is **not** in the UI layer:

- Tool args: `ToolCallSanitizer.sanitize` before `AgentTool.execute` (`INVALID_TOOL_ARGS` / `UNKNOWN_TOOL`).
- Cloud: `EgressGateway` throws `EgressBlockedException` / `MissingApiKeyException` before HTTP.
- Settings `maxTokens`: parse form text in the ViewModel; clamp uses `LlmVendors` 16..8192 on the provider config, not a Zod schema.
- Model download: `userConfirmed`, https-only, SHA-256 bijection in `ModelInstaller` / `ModelImporter` / `OfficialModelCatalog`.
- Empty chat submit: `TaskManager` no-op on blank trimmed input.

UI may disable controls (`inputEnabled`, `canRetry`) from mapped state; it must not re-implement sanitizer rules.

Compare user-facing errors to `UserFacingErrors.*` constants (`intelligenceMark` remote-failure sets). Do not substring-match English.

## Common Patterns

- Mapper functions as top-level Kotlin: `fun AgentTask?.toChatUiState()`, `fun AgentTask.toHistoryItem()`, `fun AgentTask.toDebugTaskSnapshot()`.
- `StateFlow` + `map` / `combine` + `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- `ViewModelProvider.Factory` unchecked cast is the existing DI style (no Hilt/Anvil in the project).
- `IntelligenceMark` is computed in `:app` from prefs + `task.lastError`, then passed into `ChatRoute` — Chat does not read EncryptedSharedPreferences.

## Forbidden Patterns

- `Any` / untyped `Map<String, Any>` as Chat item payloads. Use `ChatItem` / `ToolTraceEntry`.
- Decoding LLM HTTP or `snapshot_json` inside `:feature:*`.
- Treating `lastError` as free-form English and branching on `contains("timeout")`. Use `UserFacingErrors`.
- Passing intent-classifier readiness as `localLlmReady = true`. Comment in `MainActivity`: intent GGUF/ONNX is not a chat LLM.
- Adding `@Serializable` to UI state just to log it (Debug must not grow `input` / `resultJson` / `args` fields — `DebugUiStateTest` forbids those names).
