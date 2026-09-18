# Quality Guidelines

> How Compose feature modules are verified in Dougie. Same Gradle/JUnit stack as backend; there is still no ktlint, Compose screenshot CI, or accessibility scanner in the repo.

## Overview

UI lives in `:feature:*` (Compose BOM `2024.12.01`, Material3, `lifecycle-runtime-compose`). `:app` hosts `MainActivity` routing, DI, `DougieChatTileService`, and `TaskProgressNotifier`. Product copy is **Dougie** (never Waku) and Chinese for user-visible chrome.

Verification is JVM unit tests on **pure mapping functions** (`toChatUiState`, `intelligenceMark`, `toHistoryItem`, `toHistorySections`, `formatTaskDuration`, `formatCompletedAt`, `toDebugTaskSnapshot`, `OfflineModelDownloads`, `formatTaskNotice`, `nextChatItemEnter`, `chatConfirmCard`, `chatFeedItemsWithoutConfirm`, `nextConfirmEnter`). Screens themselves are not tested with Compose UI tests.

## Forbidden Patterns

- Running `LoopEngine` / OkHttp / `BatteryManager` / `CalendarContract` / `ClipboardManager` from a feature composable. Chat collects `TaskManager.task` and `transcript`; tools stay in `:core:tool` + `:tool:system`.
- A second `mutableStateOf(TaskStatus)` in a ViewModel. Map from `AgentTask` (see `state-management.md`).
- Chat `LazyColumn` keys that are only `"user"` / `"agent"` / `thinking-n`. Two turns in one window crash with `Key "user" was already used`. Use `ChatItem.listKey`.
- Pinning Chat to the last bubble whenever `ChatRoute` recomposes (bottom nav). Restore `LazyListState` from `ChatViewModel`; follow only via `shouldFollowChatFeed`. A pending History `focusListKey` overrides follow-to-end.
- Using `LazyItemScope.animateItem()` for Chat bubble enter (first paint of a thread would replay). Wrapping `ConfirmCard` in that fade+8dp enter, drawing Confirm in the feed and the overlay, dismissing Confirm by tapping the scrim, or covering the composer with the overlay. Resetting enter `seenKeys` when `firstKey` changes, or keeping `seenKeys` only inside `ChatFeed` (empty windows render `EmptyState`, so the first send would seed as “already seen” and skip enter).
- Showing prompts, API keys, `resultJson`, tool args, transcripts, or `snapshot_json` on Debug. `DebugUiStateTest` asserts those field names are absent. Rule E chrome is **评测意图规则 E**; `ruleEMessage` is counts/rates + relative path (or `INTENT_*` copy), never utterance, intent labels, or 「已达标」.
- Auto-scanning the SAF model tree when Settings opens; auto-download without confirm; treating intent ONNX as a chat LLM (`localLlmReady` must stay false until a local **chat** model exists on sideload; Play `ChannelHooks.localChatReady` is always false).
- Using `Noob-Dougie` as the launcher or as the default Chat avatar when a provider is usable. Mapping is `intelligenceMark(...)` in `:feature:chat`.
- English-only user chrome, “KISS”, or a lone “正在思考” without a loop number (`PRD` §11.1).
- A TileService or `TaskProgressNotifier` in `:feature:chat`, a Tile/notice that calls `TaskManager.submit`, or a `NotificationListenerService`.
- Overlay types (`DougieOverlayService`, `TYPE_APPLICATION_OVERLAY`) or `SYSTEM_ALERT_WINDOW` in play / `:feature:settings` / `app/src/main`. Play settings must not mention sideload or 上层显示. Play Permission Center must not show **无障碍** / L3 tap-swipe; that row is sideload `ChannelHooks.accessibilityPermissionItem` only. Sideload overlay: collapsed chrome is a ~48dp round disc (`#3D5198`) with white-tinted `dougie_logo` (not Super/Noob, not `app_name` text); TalkBack `contentDescription` is still `app_name` (Dougie). A non-drag tap expands a fixed Chinese menu **截取屏幕** / **打开对话** (not user prompt). **截取屏幕** hides the ball, `pinCurrentScreen(requireForeground = false)`, then `chatLaunchIntent(applyPinnedScreen = true)`. Missing MediaProjection: Toast + `openPermissions=true` (Permission Center), no fake chip. **打开对话** launches Chat without pin. Launch from the overlay uses `PendingIntent` so Dougie comes to the foreground. It must not `TaskManager.submit`. Overlay cannot add a 5th attachment (`最多附上 4 张`). Chat shortcut `screen_capture` pins composer after success; LLM `screen_capture` stays foreground-only and does not pin. Sideload Chat may show `ChannelHooks.screenShortcutHint` under the shortcut final answer; Play returns null; TTS still uses `finalAnswer` only.

## Required Patterns

- `*Route` collects `StateFlow` with `collectAsStateWithLifecycle` and forwards lambdas into a stateless `*Screen`.
- Activity-scoped ViewModels that list persisted data (`MemoryViewModel`, `HistoryViewModel`) must `refresh()` in `LaunchedEffect(Unit)` when the route is shown — `init` alone is stale after Chat writes a fact.
- Settings form is local until **保存配置**; `memoryEnabled` and `modelTreeUri` must be copied on save so they are not reset. Tree URI is also persisted immediately on folder pick.
- Offline model **测试** / download: disable other rows while probing; **取消** while in-flight; ASR/TTS timeout 90s, intent / chat 180s; probe on `Dispatchers.Default`.
- Color tokens: duplicate `DougieColors` per feature until more than colors is shared (no `:core:ui` yet).
- Chat bubble enter: `ChatScreen` remembers `seenKeys` and calls `nextChatItemEnter` even while `EmptyState` is showing; `ChatFeed` only applies `playKeys`. Confirm overlay uses `nextConfirmEnter` + `chatFeedItemsWithoutConfirm`; `ConfirmCard` is not wrapped in bubble enter. Duration scale 0 → fade only (bubbles skip translation; Confirm skips `translationY`).

## Testing Requirements

| Module | What exists | Command (JDK 17) |
|--------|-------------|------------------|
| `:feature:chat` | `ChatUiStateTest` (incl. unique `listKey`s across merged turns, `shouldFollowChatFeed` skip after bottom-nav return, pending focus skips follow-to-end on firstKey change, `nextChatItemEnter` first-frame seed / empty then send plays / Confirm excluded / same `{taskId}:agent` does not replay, `chatConfirmCard` / `chatFeedItemsWithoutConfirm`, `nextConfirmEnter` first-frame seed does not play / null→new key plays / same key does not replay / null then new key plays, `voiceOverlayStatus` partial vs 正在录音, `insertVoiceTranscript` at selection, `citationSources` memory then conversation `sourceLabel`, streaming/FAILED omit citations, terminal/past `AgentMessage.durationLabel` from shared `formatTaskDuration` when COMPLETED/FAILED have both timestamps, streaming/missing timestamps null, `canCancel` true for PREPARING / THINKING / `TOOL_PENDING` / `TOOL_EXECUTING` / `AWAITING_CONFIRMATION` and false for COMPLETED / FAILED / IDLE / null; no Compose UI test for bubble enter, Confirm overlay, or the composer **终止** slot), `IntelligenceAvailableTest` | `./gradlew :feature:chat:testDebugUnitTest` |
| `:feature:settings` | `OfflineModelDownloadsTest` (confirm/tree/hash/probe) | `./gradlew :feature:settings:testDebugUnitTest` |
| `:feature:history` | `HistoryItemTest` (incl. `toHistorySections` two-window grouping, 「默认会话」/「对话 2」, most-recently-active first; `canDelete` false for default / true for extra; dropping a sibling extra renumbers `currentConversationTitle`; `durationLabel` wiring to `:core:model` `formatTaskDuration` (missing either timestamp → null, FAILED 「2秒」; buckets live in `AgentTaskTest`); `providerLabel` from `completionPath?.toUserLabel()`; `formatCompletedAt` today/昨天/same-year/cross-year + seconds truncated, `completedAtLabel` from `endedAt`; `steps` from `toolTrace` including empty trace, 成功/失败/进行中, no args/`resultJson`; title-hit search merge includes all terminal cards of that window; custom title hit when window is absent from recent 50; store text hit does not pull the whole window; no Compose UI test for **展开** / section **删除** / card **删除** / search field) | `./gradlew :feature:history:testDebugUnitTest` |
| `:feature:debug` | `DebugUiStateTest` (no prompt/`resultJson` leak; no conversation-hit body/`sourceLabel` fields; Rule E copy 评测意图规则 E, no 已达标) | `./gradlew :feature:debug:testDebugUnitTest` |
| `:app` Tile / notice / leak | `ChatLaunchTest`, `TaskNoticeTest`, `PlayShortcutCopyTest`, `OverlayCopyTest`, `ChatAttachmentSessionTest`, `ShortcutScreenPinTest`, `AppBackNavTest`; no Compose UI test for Tile, shade, overlay, or bubbles | `./gradlew :app:testPlayDebugUnitTest` and `./gradlew :app:checkChannelLeak` |

`:feature:memory` and `:feature:permissions` currently have **no** unit tests. Do not invent Compose UI tests as a bootstrap requirement. If a mapping function is added there, follow the chat/history style (JUnit on the mapper).

Play/Sideload asset leaks are an `:app` concern: `./gradlew :app:checkChannelLeak`.

## Code Review Checklist

- [ ] UI only `collect`s runtime/preference flows; no Agent loop on Main
- [ ] Failed tasks render `任务失败：$lastError` with `UserFacingErrors` copy
- [ ] Confirm Card appears only for `AWAITING_CONFIRMATION` as a feed overlay (not a list row); confirm/reject go to `TaskManager`; scrim clicks do not dismiss. Composer **终止** (`canCancel`) cancels the round and is not Confirm **拒绝**; text/mic stay `inputEnabled`; TTS **停止播报** only when not busy
- [ ] Debug/History do not dump tool args or fact `content` as citations (Chat citations use `source` only). History **展开** is `toolName` + 成功/失败/进行中 only — never `argsSummary` / `resultJson`
- [ ] Settings download/probe/tree rules still match `directory-structure.md` “Don't: Let settings download without size confirm”
- [ ] Icons that are actions have Chinese `contentDescription`; decorative icons may be `null` (current Chat/Settings mix)
- [ ] QS Tile and task-progress notice stay in `:app`, open Chat only, shade copy is status-only, Play bubbles skip sideload, overlay stays sideload-only, and `checkChannelLeak` still requires Tile + forbids NotificationListener / overlay / `QUERY_ALL_PACKAGES` / `ChatLlmSpikeActivity` / `:tool:js` / quickjs on Play
- [ ] Chat bubble enter uses `nextChatItemEnter` (Confirm excluded); Confirm overlay uses `nextConfirmEnter` (first `ChatRoute` composition does not replay; scrim does not reject/cancel); `ChatScreen` keeps `seenKeys` across `EmptyState`; enter does not change `listKey` / `shouldFollowChatFeed` / `pendingFocusKey`; ChatRoute follow/`scrollToItem` uses `chatFeedItemsWithoutConfirm`
