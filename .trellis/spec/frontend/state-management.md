# State Management

> Chat and task UI state in Dougie.

## Overview

Single source of truth is `TaskManager.task: StateFlow<AgentTask?>`. Compose collects it via `ChatViewModel.uiState`.

Provider settings are a separate store: `PreferenceStore.settings: StateFlow<ProviderSettings>`. Settings form is local until **保存配置**; the next `submit` reads current `allowCloud` / key / `maxTokens` via lambdas on `EgressGateway` and `OpenAICompatibleProvider`. Choosing a vendor preset fills `baseUrl` / `model` / `maxTokens` and keeps the API key; OpenCode Go is an optional preset (`opencode-go`) and does not change the OpenAI install default; DeepSeek’s preset model is `deepseek-v4-flash`. Editing the URL off a preset switches `vendorId` to `custom`. `memoryEnabled` (default `true`) is toggled on the Memory screen via `PreferenceStore.setMemoryEnabled`; Settings **保存配置** must copy the current flag so it is not reset.

## Pattern

```kotlin
val uiState: StateFlow<ChatUiState> = taskManager.task
    .map { it.toChatUiState() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())
```

`ChatItem`: `UserMessage` | `Thinking(loopNumber, live)` | `ToolCard` | `ConfirmCard` | `AgentMessage(text, memorySources)`.

`Thinking.live` is true only for the current `PREPARING`/`THINKING` chip. Chips before already-run tools are not live: copy `循环 n`, no pulse, never “KISS” or lingering “思考中”. Live copy is `思考中… [循环 n]`.

When `status == COMPLETED`, the Final `AgentMessage` copies citation labels from `AgentTask.retrievedMemories`: each entry’s `source` field, skip blanks, keep order, drop exact duplicates. Never put fact `content` on the bubble (blank `source` is omitted, not replaced by `content`). Streaming and `FAILED` `AgentMessage`s stay source-less. Empty `retrievedMemories` or all-blank sources → no citation UI. Chat maps from `AgentTask` only; do not open SQLite in `:feature:chat`.

Debug maps `TaskManager.task` to `DebugTaskSnapshot` (`taskId`, `status`, `loopCount`, `lastError`) plus `AuditLog.listRecent` rows — never a second `TaskStatus` store, never `resultJson` / prompt / tool args.

`inputEnabled` is false while the task is busy (not COMPLETED/FAILED/IDLE), including `AWAITING_CONFIRMATION`. `canRetry` is true only when `status == FAILED`. Chat **重试** calls `TaskManager.submit` with the same `input` (new `taskId`). Bottom nav **任务** opens `:feature:history`, which lists `TaskStore.listRecent`. `:feature:memory` must `refresh()` whenever `MemoryRoute` is shown (`LaunchedEffect`), not only in `ViewModel.init` — the Activity-scoped ViewModel otherwise keeps an empty list after Chat ingests a fact.

When `status == AWAITING_CONFIRMATION`, map the last `ToolTraceEntry` to `ConfirmCard(toolName, argsJson, riskLevel, toolCallId)` instead of a `ToolCard`. `ChatViewModel.confirm()` / `reject()` call `TaskManager` and must not store a second confirmation flag.

If `streamingText` is not blank and status is not COMPLETED/FAILED, append `AgentMessage(streamingText)` even while `THINKING` (no citations). After complete, show `finalAnswer` only (`streamingText` is null), with `memorySources` when memories were retrieved.

Tool cards: `battery` → 电池工具, `time` → 时间工具, `calendar_query` / `calendar_create` / `clipboard_*` use Chinese labels, else the raw `toolName`. Do not hardcode “电池” for every tool.

Egress / timeout / network / cancel failures are not a separate UI type: they are `AgentMessage` text from `lastError`.

## Don't: Duplicate loop status in ViewModel

Do not keep a second `mutableStateOf` copy of `TaskStatus`. Map from `AgentTask` only so Fake tests and the real provider stay consistent.
