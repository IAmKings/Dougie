# Directory Structure

> How Android UI modules are organized in Dougie.

## Overview

User-facing screens live in `:feature:*` Android libraries. `:app` only hosts `MainActivity`, `Application`, and dependency injection. Features collect `StateFlow` from `:core:runtime`; they do not run the Agent loop on Main.

## Directory Layout

```
feature/chat/src/main/kotlin/com/dougie/feature/chat/
  ChatScreen.kt
  ChatViewModel.kt
  ChatUiState.kt
  DougieColors.kt
feature/chat/src/main/res/drawable/dougie_logo.xml
feature/settings/src/main/kotlin/com/dougie/feature/settings/
  SettingsScreen.kt
  SettingsViewModel.kt
  DougieColors.kt
feature/memory/src/main/kotlin/com/dougie/feature/memory/
  MemoryScreen.kt
  MemoryViewModel.kt
  DougieColors.kt
feature/permissions/src/main/kotlin/com/dougie/feature/permissions/
  PermissionsScreen.kt
  PermissionsViewModel.kt
  DougieColors.kt
feature/history/src/main/kotlin/com/dougie/feature/history/
  HistoryScreen.kt
  HistoryViewModel.kt
  HistoryItem.kt
  DougieColors.kt
app/src/main/kotlin/com/dougie/app/
  DougieApplication.kt
  MainActivity.kt
  AppForegroundTracker.kt
  PermissionUsageTracker.kt
```

## Module Organization

| Module | Owns |
|--------|------|
| `:feature:chat` | Chat Compose UI, `ChatViewModel`, bubble mapping, Confirm Card; navigate to settings / memory / Permission Center |
| `:feature:settings` | Provider URL/key/model, egress consent copy, save to `PreferenceStore` |
| `:feature:memory` | Local facts list/edit/delete/clear + `memoryEnabled` toggle; product copy **Dougie** |
| `:feature:permissions` | Permission Center: calendar read/write status, request runtime grants, clipboard note |
| `:feature:history` | Task History list from `TaskStore.listRecent`; bottom nav **任务** |
| `:app` | `DougieApplication` builds `TaskManager` + `OpenAICompatibleProvider` + `EgressGateway` + tools + `PolicyEngine` + `RoomMemoryStore` + `DougieTaskStores` on `Dispatchers.Default`; `recoverInterrupted` + `seed`; Chat↔Settings↔Memory↔Permissions↔History routes |

`:feature:*` must not call `BatteryManager`, `CalendarContract`, `ClipboardManager`, or open OkHttp. Color tokens may be duplicated once per feature (`DougieColors`); extract `:core:ui` only if more than colors is shared.

## Naming Conventions

- Screens: `ChatScreen` / `ChatRoute`, `SettingsScreen` / `SettingsRoute`, `MemoryScreen` / `MemoryRoute`, `PermissionsScreen` / `PermissionsRoute`, `HistoryScreen` / `HistoryRoute`
- Mapping: `AgentTask?.toChatUiState()` in `ChatUiState.kt`
- Product copy: **Dougie**, never Waku
- Chat colors: Stitch tokens `primary #3D5198`, `primaryContainer #566AB2`, `surface #F8FAF9` (`DougieColors`)
- Egress consent (fixed): `本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。`

## Design Decision: Chat status chain

**Context**: PRD §11.1 forbids a lone “正在思考”.

**Decision**: `toChatUiState` inserts `Thinking(loopNumber)` before each tool card, plus a live Thinking chip while `PREPARING`/`THINKING`. Tool cards show `resultJson` when present. `FAILED` + `lastError` becomes `AgentMessage("任务失败：$lastError")`.

## Don't: Run LoopEngine on Main

`DougieApplication` must pass `Dispatchers.Default` (or a test dispatcher). UI only `collect`s `taskManager.task`.
