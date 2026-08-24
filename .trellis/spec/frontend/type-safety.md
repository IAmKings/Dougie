# Type Safety

> Kotlin on Android. There is no TypeScript and no Zod/Yup.

## Overview

UI models are Kotlin `data class` / `sealed class` in the feature package. Domain types live in `:core:model`. JSON at tool/LLM boundaries is `kotlinx.serialization.json`, parsed in `:core:tool` / `:core:runtime`, not in Compose.

Reference files:
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatUiState.kt` (`ChatItem` sealed)
- `core/model/src/main/kotlin/com/dougie/core/model/AgentTask.kt`
- `core/runtime/src/main/kotlin/com/dougie/core/runtime/ToolCallSanitizer.kt`
- `core/tool/src/main/kotlin/com/dougie/core/tool/IntentClassifierTool.kt`

## Type Organization

| Layer | Types |
|-------|--------|
| `:core:model` | `AgentTask`, `TaskStatus`, `ToolDescriptor`, `UserFacingErrors`, vendor ids |
| `:core:tool` | `OfflineModelOffer`, `IntentHit`, layout objects (`IntentModelLayout`) |
| `:feature:chat` | `ChatUiState`, `ChatItem`, `IntelligenceMark` |
| `:feature:settings` | `SettingsFormState`, `OfflineModelRowUi`, `ProbeResult` |

Map `AgentTask?` → `ChatUiState` in `toChatUiState()`. Debug uses `DebugTaskSnapshot`, never a second status enum.

## Validation

- LLM tool args: `ToolCallSanitizer` + `AgentTool.validateArguments`. Unrepairable fields → `INVALID_TOOL_ARGS`.
- Settings numbers: `maxTokens` parsed in the ViewModel; invalid override URL/SHA → offer `isConfigured() == false` (no fetch).
- Model bytes: SHA-256 hex via `SHA256.matches`, not a JSON schema library.
- Intents: `IntentJsonParser` / labels file; blank logits → `INTENT_FAILED`.

Do not validate SAF URIs with regex in Compose. Persist the tree URI; `ExternalModelTreeImpl` reports missing grant as Chinese errors.

## Common Patterns

- `sealed class ChatItem` with `UserMessage` / `Thinking` / `ToolCard` / `ConfirmCard` / `AgentMessage`.
- `StateFlow<T>` + `stateIn(WhileSubscribed(5_000), …)`.
- `@Suppress("UNCHECKED_CAST")` only on `ViewModelProvider.Factory.create`.

## Forbidden Patterns

- `Any` / untyped `Map<String, Any>` in UI state when a data class exists.
- Opening `org.json` in `:feature:*` to parse `resultJson` for display beyond the mapped card fields.
- Casting payload fields from the LLM in Compose. Sanitizer + tools own that boundary.
- TypeScript/Zod examples in new code. This module is Kotlin.
