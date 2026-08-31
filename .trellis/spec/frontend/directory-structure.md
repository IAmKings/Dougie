# Directory Structure

> How Android UI modules are organized in Dougie.

## Overview

User-facing screens live in `:feature:*` Android libraries. `:app` hosts `MainActivity`, `Application`, DI, the Quick Settings `TileService`, and the task-progress notifier. Features collect `StateFlow` from `:core:runtime`; they do not run the Agent loop on Main.

## Directory Layout

```
feature/chat/src/main/kotlin/com/dougie/feature/chat/
  ChatScreen.kt
  ChatViewModel.kt
  ChatUiState.kt
  ScreenAttachUi.kt
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
  ChatLaunch.kt
  ChatAttachmentSession.kt
  ChatImageCodec.kt
  DougieChatTileService.kt
  TaskNotice.kt
  TaskProgressNotifier.kt
  AppOfflineModels.kt
  AppOfflineModelProbe.kt
  ExternalModelTreeImpl.kt
  AppForegroundTracker.kt
  PermissionUsageTracker.kt
  ReplyPlayback.kt
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/play/kotlin/com/dougie/app/
  ChannelHooks.kt
app/src/play/res/values/strings.xml
app/src/sideload/kotlin/com/dougie/app/
  ChannelHooks.kt
  DougieOverlayService.kt
  OverlayController.kt
  OverlayPrefs.kt
app/src/sideload/AndroidManifest.xml
app/src/sideload/assets/models/asr/
app/src/sideload/assets/models/tts/
```

## Module Organization

| Module | Owns |
|--------|------|
| `:feature:chat` | Chat Compose UI, `ChatViewModel`, bubble mapping, Confirm Card; navigate to settings / memory / Permission Center |
| `:feature:settings` | Provider vendor preset + URL/key/model/`max_tokens`, egress consent copy, save to `PreferenceStore`; **模型目录** (`OpenDocumentTree` URI in `modelTreeUri`) + **刷新** 扫描外部树 + 离线模型三行确认下载（ASR / TTS / 意图 ONNX，写入外部树再同步 `filesDir`）+ 已安装烟测（`OfflineModelProbe` 由 `:app` 注入，非 AgentTool）；**开发者** row calls `onOpenDebug` only |
| `:feature:memory` | Local facts list/edit/delete/clear + `memoryEnabled` toggle; product copy **Dougie** |
| `:feature:permissions` | Permission Center: calendar / location / mic / screen capture; API 33+ **通知** row (`POST_NOTIFICATIONS`); clipboard note |
| `:feature:history` | Task History list from `TaskStore.listRecent`; bottom nav **任务** |
| `:feature:debug` | Developer page: current `AgentTask` snapshot (`taskId` / `status` / `loopCount` / `lastError`) + `AuditLog.listRecent`; no `resultJson`, prompts, or tool args |
| `:app` | `DougieApplication` builds `TaskManager` + `OpenAICompatibleProvider` + `EgressGateway` + tools + `PolicyEngine` + `RoomMemoryStore` + `DougieTaskStores` on `Dispatchers.Default`; `recoverInterrupted` + `seed`; Chat↔Settings↔Memory↔Permissions↔History↔Debug routes; `DougieChatTileService` + `ChatLaunch` (Quick Settings opens Chat, no `submit`); `TaskProgressNotifier` + `formatTaskNotice` (status-only shade, tap Chat; Play attaches `BubbleMetadata` API 29+); sideload overlay via `ChannelHooks.syncOverlay` / `DougieOverlayService` (default off); Settings `shortcutLayer` slot from `ChannelHooks.ShortcutLayerSettings` (play copy = 系统气泡 only); SAF `OpenDocumentTree` + persistable permission + `ExternalModelTreeImpl` (DocumentFile, no `:core:tool`); `AppOfflineModelProbe` (ASR/TTS/intent JNI, TTS generate only); `ChannelHooks.seedBundledModels` (sideload copies ASR/TTS assets to `filesDir` only; play no-op); `ChatAttachmentSession` + Photo Picker / `TakePicture` / FileProvider camera JPEG (longest edge ≤ 1280); `CAMERA` in the main manifest is not an overlay leak |

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

## Don't: Put TileService in `:feature:chat`

**Problem**: Quick Settings is an exported system service and must start `MainActivity`. Putting it in a feature module still merges into the APK but splits Activity launch from routing.

**Instead**: `DougieChatTileService` and `ChatLaunch` live in `:app`. Play and sideload share `app/src/main` (not a flavor split).

## Scenario: Quick Settings Tile opens Chat

### 1. Scope / Trigger
System QS tile is a new Android component (`TileService`) that must not call `TaskManager.submit` or skip L2 confirm cards.

### 2. Signatures
- `fun chatLaunchIntent(context: Context, scheduleId: String? = null, applyPinnedScreen: Boolean = false): Intent` — extras `OPEN_CHAT`, optional `SCHEDULE_ID`, optional `APPLY_PINNED_SCREEN` (boolean). Never pixels or prompts.
- `object ChatLaunch` — `EXTRA_OPEN_CHAT = "com.dougie.app.extra.OPEN_CHAT"`; `EXTRA_SCHEDULE_ID` (UUID, **not** prompt); `activityFlags` = `NEW_TASK | SINGLE_TOP | CLEAR_TOP`; `requestsChat(intent): Boolean`; `scheduleId(intent): String?`
- `class DougieChatTileService : TileService` — `onClick` → `unlockAndRun` → `startActivityAndCollapse` (API 34+ `PendingIntent` `FLAG_IMMUTABLE`)

### 3. Contracts
- Manifest (`app/src/main`): `service` `.DougieChatTileService`, `exported=true`, `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, `android:label="@string/app_name"` (**Dougie**), `QS_TILE` action. `MainActivity` `launchMode=singleTop`.
- Extra `OPEN_CHAT=true` forces `AppRoute.Chat` on cold start (`savedInstanceState == null`) and `onNewIntent`. Launcher icon without extra does not reset an in-memory route.
- No API keys, prompts, `taskId`, or tool args in extras. Tile does not write `PreferenceStore`. Schedule tap may pass `SCHEDULE_ID` only; Chat loads draft from `filesDir` (`dougie_schedule_pending.txt` if the one-shot row was already removed on fire).

### 4. Validation & Error Matrix
- Missing extra / extra false → `requestsChat` false
- Play merged manifest missing `DougieChatTileService` or `QS_TILE` → `checkChannelLeak` fails
- Play merged manifest contains `NotificationListenerService` / `AccessibilityService` / `TapSwipeTool` / `SYSTEM_ALERT_WINDOW` / `DougieOverlayService` / `TYPE_APPLICATION_OVERLAY` → `checkChannelLeak` fails

### 5. Good/Base/Bad Cases
- Good: Tile click opens Chat; `taskId` unchanged until the user sends
- Base: App already on Chat; `singleTop` reuses Activity
- Bad: Tile in `:feature:chat`; Tile `submit()`; flavor-only Tile on sideload

### 6. Tests Required
- `ChatLaunchTest` — extra name stable, not `key`/`prompt`; flags match; `requestsChat(null)` is false (do not call `Intent.putExtra` on JVM stubs)
- `./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak`
- Manual: add Tile, open Chat from Settings via Tile

### 7. Wrong vs Correct
#### Wrong
```kotlin
// :feature:chat TileService
taskManager.submit("我现在手机还有多少电？")
```
#### Correct
```kotlin
startActivityAndCollapse(chatLaunchIntent(this)) // extra OPEN_CHAT only
```

## Don't: Post task progress from `:feature:chat` or use NotificationListener

**Problem**: Chat is not composed on History/Settings; a Listener would read other apps' notifications (`PRD.md` §3.2) and fail `checkChannelLeak`.

**Instead**: `DougieApplication` collects `taskManager.task` into `TaskProgressNotifier` (channel `dougie_task_progress`, id **48**; capture FGS stays `dougie_screen_capture` / **47**). Tap uses `chatLaunchIntent`. No `NotificationListenerService`.

## Scenario: Task-progress notification

### 1. Scope / Trigger
System shade shows current Agent task without a second Loop or L2 confirm in the notification.

### 2. Signatures
- `fun formatTaskNotice(task: AgentTask?): String?` — `null` means cancel
- `fun isTaskBusy(task: AgentTask?): Boolean`
- `class TaskProgressNotifier(context)` — `start(scope, tasks)`, `apply(task)`
- `AndroidPermissions.POST_NOTIFICATIONS`

### 3. Contracts
- Title **Dougie**. Body: `思考中 · 循环 n` / `工具 · 循环 n · {toolName}` / `待确认 · {toolName}` / `已完成 · 循环 n` / `任务失败 · 循环 n`. Last `toolTrace.toolName` only — never `input`, `finalAnswer`, `lastError`, `streamingText`, `argsSummary`, `resultJson`.
- IDLE/null → cancel. Busy → `ongoing`. COMPLETED/FAILED → not ongoing; keep until next task or user dismisses.
- Play (`!BuildConfig.IS_SIDELOAD`, API ≥ 29): attach `NotificationCompat.BubbleMetadata` (`setAutoExpandBubble(false)`, desired height 640, **mutable** bubble `PendingIntent` — NMS rejects `FLAG_IMMUTABLE` with a main-thread crash). Shade tap stays `FLAG_IMMUTABLE`. `notify` failures must not crash Chat (strip bubble and retry). Sideload skips bubble metadata. OEM may ignore bubbles; shade notice remains.
- API 33+: request `POST_NOTIFICATIONS` once per process on first busy task; deny → no crash, no post. Permission Center item id `notifications` only if `SDK_INT >= 33`. `MainActivity.onResume` republishes **only if** `isTaskBusy` (do not restore a swipe-dismissed COMPLETED notice) and calls `ChannelHooks.syncOverlay`. After a grant from Permission Center, `republishTaskNotice()`.
- Manifest `uses-permission POST_NOTIFICATIONS`. Channel name **任务状态**, `IMPORTANCE_LOW`.

### 4. Validation & Error Matrix
- Ungranted API 33+ → skip `notify` (do not crash)
- Play manifest contains `NotificationListenerService` → `checkChannelLeak` fails
- Play manifest contains `SYSTEM_ALERT_WINDOW` / `DougieOverlayService` → `checkChannelLeak` fails
- `POST_NOTIFICATIONS` in Play manifest is **not** a leak

### 5. Good/Base/Bad Cases
- Good: Loop updates shade with status + tool name; tap opens Chat, `taskId` unchanged
- Base: minSdk 26 posts without runtime permission
- Bad: shade shows `lastError` / prompt; Listener; posting from ChatViewModel only

### 6. Tests Required
- `TaskNoticeTest` — null/IDLE cancel; FAILED omits error/prompt; tool line has name not args; bubble PI flags are mutable on API 31
- `PlayShortcutCopyTest` — play `strings.xml` has 气泡, no `sideload` / `上层显示`
- `./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak`

### 7. Wrong vs Correct
#### Wrong
```kotlin
.setContentText(task.lastError ?: task.finalAnswer)
```
#### Correct
```kotlin
.setContentText(formatTaskNotice(task) ?: return cancel())
```

## Don't: Seed bundled models from play

**Problem**: Play APK must stay light; ASR/TTS ONNX in `main` or `play` assets would leak into the store build.

**Instead**: Only `app/src/sideload/assets/models/{asr,tts}/` may hold layout files. Sideload `ChannelHooks.seedBundledModels` copies via `BundledModelSeed` into `filesDir`. Play `ChannelHooks.seedBundledModels` is a no-op. Do not use `ModelInstaller`/HTTPS for this seed. Intent ONNX stays download/scan-only (not sideload seed). Settings rows show 已安装 once `isPresent`.

## Don't: Use the sketch SVG as the launcher or default chat avatar

**Problem**: `Noob-Dougie.svg` (vector `dougie_logo_unavailable`) is humorous line art. Using it as the application icon or as the default Chat avatar implies the product is a joke, and hides whether a chat LLM is actually usable.

**Instead**: In-app Chat avatar is three marks from `intelligenceMark(...)` in `:feature:chat`: **SUPER** (`super_dougie.xml` from `design/品牌/Super-Dougie.svg`) when a remote provider is configured and usable; **LOCAL** (`dougie_logo.xml` from `Dougie-Logo.svg`) when remote is not configured but a local **chat** LLM is ready; **NOOB** (`dougie_logo_unavailable.xml` from `Noob-Dougie.svg`) when neither is configured **or** a remote call actually failed (`LLM_FAILED` / `NETWORK_FAILED` / `LLM_TIMEOUT`). Intent ONNX is not a chat LLM. Do not ship `Dougie-Logo.png` as a drawable. Adaptive **launcher** stays `dougie_logo` with 16% inset in `main` (Play and Sideload share it) — not Super or Noob.

## Don't: Let settings download without size confirm

**Problem**: Play on-demand models are hundreds of MB. A one-tap download skips traffic/storage consent; putting URLs on the LLM would fetch arbitrary files.

**Instead**: `OfflineModelDownloads` in `:feature:settings` owns three rows (ASR ~230MB, TTS ~116MB, intent MiniRBT ONNX ~47MB fp32) plus a **模型目录** row. User picks a tree via SAF; URI is persisted immediately (`PreferenceStore.setModelTreeUri`, not tied to **保存配置**). Scan lists each pack `relativeDir` under the tree, streams to temp `File`s, then `ModelImporter` syncs `filesDir` (SHA bijection). Hash-complete rows show 已安装 with no HTTP. Missing/wrong hash → not installed; **下载** if `offer.isConfigured()` and the tree is ready. Confirm download: HTTPS to cache → stream layout names onto the tree → import cache into `filesDir`. No tree / lost persistable permission → cannot download (`请选择模型目录` / `请再次选择模型目录`). Historical `model.gguf` in the intent folder must not mark installed. `request` only opens download confirm; `confirm` calls `ModelInstaller` with `userConfirmed=true`. Installed rows show **测试**; probe is injected (`OfflineModelProbe`) and must not use system TTS as success. Unconfigured (`尚未配置下载地址`) and already-installed rows do not fetch. Cancel the job; layout `isPresent` stays false on failed hash. `:feature:settings` depends on `:core:tool` only — OkHttp, SAF, and `ContentResolver` stay in `:app` / `:tool:system`. Do not keep per-file **导入** as the primary UX.
