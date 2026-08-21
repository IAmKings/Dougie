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
  IntelligenceAvailable.kt
feature/chat/src/main/res/drawable/dougie_logo.xml
feature/chat/src/main/res/drawable/dougie_logo_unavailable.xml
feature/chat/src/main/res/drawable/super_dougie.xml
feature/settings/src/main/kotlin/com/dougie/feature/settings/
  SettingsScreen.kt
  SettingsViewModel.kt
  OfflineModelDownloads.kt
  ExternalModelTree.kt
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
feature/debug/src/main/kotlin/com/dougie/feature/debug/
  DebugScreen.kt
  DebugViewModel.kt
  DebugUiState.kt
  DougieColors.kt
app/src/main/kotlin/com/dougie/app/
  DougieApplication.kt
  MainActivity.kt
  AppOfflineModels.kt
  AppOfflineModelProbe.kt
  ExternalModelTreeImpl.kt
  AppForegroundTracker.kt
  PermissionUsageTracker.kt
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/play/kotlin/com/dougie/app/
  ChannelHooks.kt
app/src/sideload/kotlin/com/dougie/app/
  ChannelHooks.kt
app/src/sideload/assets/models/asr/
app/src/sideload/assets/models/tts/
```

## Module Organization

| Module | Owns |
|--------|------|
| `:feature:chat` | Chat Compose UI, `ChatViewModel`, bubble mapping, Confirm Card; navigate to settings / memory / Permission Center |
| `:feature:settings` | Provider vendor preset + URL/key/model/`max_tokens`, egress consent copy, save to `PreferenceStore`; **模型目录** (`OpenDocumentTree` URI in `modelTreeUri`) + **刷新** 扫描外部树 + 离线模型三行确认下载（ASR / TTS / 意图 ONNX，写入外部树再同步 `filesDir`）+ 已安装烟测（`OfflineModelProbe` 由 `:app` 注入，非 AgentTool）；**开发者** row calls `onOpenDebug` only |
| `:feature:memory` | Local facts list/edit/delete/clear + `memoryEnabled` toggle; product copy **Dougie** |
| `:feature:permissions` | Permission Center: calendar read/write status, request runtime grants, clipboard note |
| `:feature:history` | Task History list from `TaskStore.listRecent`; bottom nav **任务** |
| `:feature:debug` | Developer page: current `AgentTask` snapshot (`taskId` / `status` / `loopCount` / `lastError`) + `AuditLog.listRecent`; no `resultJson`, prompts, or tool args |
| `:app` | `DougieApplication` builds `TaskManager` + `OpenAICompatibleProvider` + `EgressGateway` + tools + `PolicyEngine` + `RoomMemoryStore` + `DougieTaskStores` on `Dispatchers.Default`; `recoverInterrupted` + `seed`; Chat↔Settings↔Memory↔Permissions↔History↔Debug routes; SAF `OpenDocumentTree` + persistable permission + `ExternalModelTreeImpl` (DocumentFile, no `:core:tool`); `AppOfflineModelProbe` (ASR/TTS/intent JNI, TTS generate only); `ChannelHooks.seedBundledModels` (sideload copies ASR/TTS assets to `filesDir` only; play no-op) |

`:feature:*` must not call `BatteryManager`, `CalendarContract`, `ClipboardManager`, or open OkHttp. Color tokens may be duplicated once per feature (`DougieColors`); extract `:core:ui` only if more than colors is shared.

## Naming Conventions

- Screens: `ChatScreen` / `ChatRoute`, `SettingsScreen` / `SettingsRoute`, `MemoryScreen` / `MemoryRoute`, `PermissionsScreen` / `PermissionsRoute`, `HistoryScreen` / `HistoryRoute`, `DebugScreen` / `DebugRoute`
- Mapping: `AgentTask?.toChatUiState()` in `ChatUiState.kt`
- Product copy: **Dougie**, never Waku
- Chat colors: Stitch tokens `primary #3D5198`, `primaryContainer #566AB2`, `surface #F8FAF9` (`DougieColors`)
- Egress consent (fixed): `本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。`

## Design Decision: Chat status chain

**Context**: PRD §11.1 forbids a lone “正在思考”.

**Decision**: `toChatUiState` inserts `Thinking(loopNumber, live=false)` before each tool card, plus a **live** Thinking chip while `PREPARING`/`THINKING`. After a tool has run, that loop’s chip is not live (no “思考中”, no pulse). Copy is `思考中… [循环 n]` while live, `循环 n` after. Never “KISS”. Tool cards show `resultJson` when present. `FAILED` + `lastError` becomes `AgentMessage("任务失败：$lastError")`.

## Don't: Run LoopEngine on Main

`DougieApplication` must pass `Dispatchers.Default` (or a test dispatcher). UI only `collect`s `taskManager.task`.

## Don't: Seed bundled models from play

**Problem**: Play APK must stay light; ASR/TTS ONNX in `main` or `play` assets would leak into the store build.

**Instead**: Only `app/src/sideload/assets/models/{asr,tts}/` may hold layout files. Sideload `ChannelHooks.seedBundledModels` copies via `BundledModelSeed` into `filesDir`. Play `ChannelHooks.seedBundledModels` is a no-op. Do not use `ModelInstaller`/HTTPS for this seed. Intent ONNX stays download/scan-only (not sideload seed). Settings rows show 已安装 once `isPresent`.

## Don't: Use the sketch SVG as the launcher or default chat avatar

**Problem**: `Noob-Dougie.svg` (vector `dougie_logo_unavailable`) is humorous line art. Using it as the application icon or as the default Chat avatar implies the product is a joke, and hides whether a chat LLM is actually usable.

**Instead**: In-app Chat avatar is three marks from `intelligenceMark(...)` in `:feature:chat`: **SUPER** (`super_dougie.xml` from `design/品牌/Super-Dougie.svg`) when a remote provider is configured and usable; **LOCAL** (`dougie_logo.xml` from `Dougie-Logo.svg`) when remote is not configured but a local **chat** LLM is ready; **NOOB** (`dougie_logo_unavailable.xml` from `Noob-Dougie.svg`) when neither is configured **or** a remote call actually failed (`LLM_FAILED` / `NETWORK_FAILED` / `LLM_TIMEOUT`). Intent ONNX is not a chat LLM. Do not ship `Dougie-Logo.png` as a drawable. Adaptive **launcher** stays `dougie_logo` with 16% inset in `main` (Play and Sideload share it) — not Super or Noob.

## Don't: Let settings download without size confirm

**Problem**: Play on-demand models are hundreds of MB. A one-tap download skips traffic/storage consent; putting URLs on the LLM would fetch arbitrary files.

**Instead**: `OfflineModelDownloads` in `:feature:settings` owns three rows (ASR ~230MB, TTS ~116MB, intent ONNX ~10–20MB) plus a **模型目录** row. User picks a tree via SAF; URI is persisted immediately (`PreferenceStore.setModelTreeUri`, not tied to **保存配置**). Scan lists each pack `relativeDir` under the tree, streams to temp `File`s, then `ModelImporter` syncs `filesDir` (SHA bijection). Hash-complete rows show 已安装 with no HTTP. Missing/wrong hash → not installed; **下载** if `offer.isConfigured()` and the tree is ready. Confirm download: HTTPS to cache → stream layout names onto the tree → import cache into `filesDir`. No tree / lost persistable permission → cannot download (`请选择模型目录` / `请再次选择模型目录`). Historical `model.gguf` in the intent folder must not mark installed. `request` only opens download confirm; `confirm` calls `ModelInstaller` with `userConfirmed=true`. Installed rows show **测试**; probe is injected (`OfflineModelProbe`) and must not use system TTS as success. Unconfigured (`尚未配置下载地址`) and already-installed rows do not fetch. Cancel the job; layout `isPresent` stays false on failed hash. `:feature:settings` depends on `:core:tool` only — OkHttp, SAF, and `ContentResolver` stay in `:app` / `:tool:system`. Do not keep per-file **导入** as the primary UX.
