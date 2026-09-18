# Hook Guidelines

> Dougie is Jetpack Compose + ViewModel, not React. There are no `use*` hooks and no Retrofit/Room in feature modules. This file is the local equivalent: how screens subscribe to state and when to refresh.

## Overview

Shared stateful logic lives in `*ViewModel` (`androidx.lifecycle.ViewModel`). Compose uses:

- `collectAsStateWithLifecycle()` on `StateFlow`
- `LaunchedEffect` for one-shot refresh / scroll. Chat feed follows to the last item only when the transcript grows, the first `listKey` changes (new/opened conversation), or the last agent text streams — unless `pendingFocusKey` is set, which overrides follow-to-end until `scrollToItem` on `{taskId}:user` (or fallback if that key never appears). After a focus scroll, `rememberFeedFollow` before `clearPendingFocus` so the follow effect does not pin to end. `send` / `retry` / `newConversation` also clear pending focus. Bottom-nav return must restore `LazyListState` from `ChatViewModel` and must not pin to end or call `requestFocus`. Bubble enter (`nextChatItemEnter`) and Confirm overlay enter (`nextConfirmEnter`) must not change follow/focus/`listKey`. ChatRoute follow/`scrollToItem` uses `chatFeedItemsWithoutConfirm` — Confirm is not a LazyColumn index.
- `remember` / `mutableStateOf` for ephemeral UI (input draft, dialog, key visibility). Chat enter `seenKeys` (`Set<String>?`, start `null`) lives on `ChatScreen` so it still updates while `EmptyState` is showing; `ChatFeed` only consumes `playKeys`. Confirm overlay `initialized`/`lastKey` also live on `ChatScreen`: first composition after `ChatRoute` mounts seeds the current confirm key with `play = false` (bottom-nav / History return must not replay the slide). Disposing `ChatRoute` (bottom-nav / History tap return) resets to `null` and seeds, so existing bubbles do not replay.

`PermissionsViewModel` is an `AndroidViewModel` because it reads `ContextCompat.checkSelfPermission`. That is the exception; Chat/Settings/Memory/History/Debug ViewModels take interfaces (`TaskManager`, `PreferenceStore`, `MemoryStore`, `TaskStore`, `AuditLog`, `ConversationTitles`) via `ViewModelProvider.Factory`.

## Custom Hook Patterns

Do **not** introduce `useChat()`-style Compose wrapper functions. The repeated pattern is:

```kotlin
@Composable
fun ChatRoute(viewModel: ChatViewModel, ...) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(uiState = uiState, onSend = viewModel::send, ...)
}
```

Factories live as inner `class Factory(...) : ViewModelProvider.Factory` with `@Suppress("UNCHECKED_CAST")`. `MainActivity` calls `viewModel(factory = ChatViewModel.Factory(app.taskManager))`.

`OfflineModelDownloads` is a helper class owned by `SettingsViewModel` (not a composable hook). It holds download/probe `StateFlow` and coroutine jobs on `viewModelScope`.

## Data Fetching

There is no React Query / SWR. Reads are:

| Data | Owner | How UI gets it |
|------|--------|----------------|
| Current agent task | `TaskManager.task` | Chat/Debug `map`/`combine` → `stateIn(WhileSubscribed(5_000))` |
| Current conversation turns | `TaskManager.transcript` | Chat `combine` with `task`; loaded via `TaskStore.listByConversation` inside TaskManager |
| Provider prefs | `PreferenceStore.settings` | Settings form seed; Chat `allowCloud` from Activity collect |
| Current conversation id | `PreferenceStore.currentConversationId()` | Independent key; **保存配置** must not write or clear it |
| Conversation titles | `PreferenceStore.conversationTitles` | Independent JSON key `conversation_titles_json`; **保存配置** must not write or clear it. History **改名** calls `setConversationTitle`; `TaskManager.deleteConversation` clears with `setTitle(id, "")`; Chat only displays |
| Memory list | `MemoryStore.list()` | `MemoryViewModel.refresh()` |
| Task history | `TaskStore.listRecent(50)` when History query is blank; `searchHistory` ∪ title hits when non-blank | `HistoryViewModel.refresh()` / `setQuery`; Activity also maps `listRecent(50)` for Chat `currentConversationTitle` and must refresh that snapshot after `deleteConversation` / `deleteTask` |
| Audit rows | `AuditLog.listRecent(50)` | `DebugViewModel.refresh()` |
| Runtime permission bits | `ContextCompat` | `PermissionsViewModel.refresh()` |

Do not open SQLite from `:feature:chat`. Chat maps `AgentTask` lists from `TaskManager` (including `retrievedMemories` → citation `source` labels).

SAF `OpenDocumentTree` stays in `:app` (`rememberLauncherForActivityResult`). Settings receives `onPickModelTree` and `setModelTreeUri`.

## Naming Conventions

- ViewModels: `ChatViewModel`, `SettingsViewModel`, …
- UI state: `ChatUiState`, `SettingsFormState`, `MemoryUiState`, `HistoryUiState`, `DebugUiState`, `PermissionUiState`
- Routes: `ChatRoute`, `SettingsRoute`, …
- Mappers: `toChatUiState()`, `toHistoryItem()`, `formatTaskDuration()` (`:core:model`, Chat + History), `formatCompletedAt()`, `toHistorySections()`, `currentConversationTitle()`, `conversationDisplayName()`, `toDebugTaskSnapshot()`, `intelligenceMark(...)`, `nextChatItemEnter()`, `chatConfirmCard()`, `chatFeedItemsWithoutConfirm()`, `nextConfirmEnter()`

Do not name Compose functions `useXxx`.

## Common Mistakes

- Refreshing Memory/History only in `ViewModel.init`. Activity-scoped ViewModels survive navigation; Chat can ingest a fact while Memory is off-screen. `MemoryRoute` and `HistoryRoute` call `refresh()` in `LaunchedEffect(Unit)`.
- Keeping confirmation as a boolean in Chat ViewModel. `confirm()` / `reject()` must call `TaskManager`; UI maps `AWAITING_CONFIRMATION` to `ConfirmCard`.
- Collecting flows without `viewModelScope` / `stateIn`, or launching probes on Main. Offline probe runs on `Dispatchers.Default` (`state-management.md`).
- Auto-collecting `PreferenceStore` into Settings fields on every emission in a way that wipes unsaved edits. Form is a local `MutableStateFlow` until **保存配置**.
- Calling `requestFocus` from bottom-nav **对话**, or clearing `pendingFocusKey` before `rememberFeedFollow` after a History tap — both pin Chat to the last bubble and undo mid-thread positioning. Enter motion must not `requestFocus` or pin follow-to-end. Confirm overlay enter must not replay when `ChatRoute` remounts onto an already-waiting Confirm.
- Keeping Chat enter `seenKeys` only inside `ChatFeed`, or resetting it when `firstKey` changes. Empty windows render `EmptyState` instead of `ChatFeed`, so the first send would seed as already seen and skip enter.
- Putting window titles in `ProviderSettings.save()` or `snapshot_json`. Custom names are `conversation_titles_json`; **保存配置** must not write or clear that key.
