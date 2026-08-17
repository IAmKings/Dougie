# State Management

> Chat and task UI state in Dougie.

## Overview

Single source of truth is `TaskManager.task: StateFlow<AgentTask?>`. Compose collects it via `ChatViewModel.uiState`.

Provider settings are a separate store: `PreferenceStore.settings: StateFlow<ProviderSettings>`. Settings form is local until **保存配置**; the next `submit` reads current `allowCloud` / key via lambdas on `EgressGateway` and `OpenAICompatibleProvider`. `memoryEnabled` (default `true`) is toggled on the Memory screen via `PreferenceStore.setMemoryEnabled`; Settings **保存配置** must copy the current flag so it is not reset.

## Pattern

```kotlin
val uiState: StateFlow<ChatUiState> = taskManager.task
    .map { it.toChatUiState() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())
```

`ChatItem`: `UserMessage` | `Thinking(loopNumber)` | `ToolCard` | `AgentMessage`.

`inputEnabled` is false while the task is busy (not COMPLETED/FAILED/IDLE).

If `streamingText` is not blank and status is not COMPLETED/FAILED, append `AgentMessage(streamingText)` even while `THINKING`. After complete, show `finalAnswer` only (`streamingText` is null).

Tool cards: `battery` → 电池工具, `time` → 时间工具, else the raw `toolName`. Do not hardcode “电池” for every tool.

Egress / timeout / network / cancel failures are not a separate UI type: they are `AgentMessage` text from `lastError`.

## Don't: Duplicate loop status in ViewModel

Do not keep a second `mutableStateOf` copy of `TaskStatus`. Map from `AgentTask` only so Fake tests and the real provider stay consistent.
