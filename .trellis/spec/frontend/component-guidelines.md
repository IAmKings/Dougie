# Component Guidelines

> Compose screens in Dougie. There is no React, no `:core:ui` design-system module, and no `@Preview` composables in the tree today.

## Overview

Each feature module owns one primary screen file plus a ViewModel. `:app` `MainActivity` switches an internal `AppRoute` enum (`Chat`, `Settings`, `OpenApps`, `Memory`, `Permissions`, `History`, `Debug`) — not Navigation Compose. System / gesture back uses Compose `BackHandler` plus `consumeBack` in `AppBackNav.kt` so it matches toolbar `onBack`: Chat preview closes first; OpenApps/Debug → Settings; Settings/Permissions/Memory/History → Chat; Chat with no preview returns null and the Activity finishes as before. Do not add Predictive Back custom animations.

## Component Structure

Pattern used everywhere (`ChatScreen.kt`, `SettingsScreen.kt`, `MemoryScreen.kt`, `HistoryScreen.kt`, `DebugScreen.kt`, `PermissionsScreen.kt`):

1. `FooRoute(viewModel, navigation lambdas)` — `collectAsStateWithLifecycle`, optional `LaunchedEffect` refresh, then `FooScreen(...)`.
2. `FooScreen(uiState, onEvent: ...)` — stateless UI. Local `remember { mutableStateOf }` is allowed for draft text, password visibility, dialogs, History **展开** (`remember(taskId)`), History card **删除** confirm, Chat enter `seenKeys`, Confirm overlay `initialized`/`lastKey` — not for `TaskStatus`.
3. Private helpers in the same file (bubbles, nav rail, confirm card). Do not extract a new module for a single repeated `Row`.

Chat is the dense case: `ChatRoute` → `ChatScreen` → item `when (ChatItem)` for `UserMessage` / `Thinking` / `ToolCard` / `AgentMessage` in `ChatFeed`. `ConfirmCard` is not a feed row: `chatFeedItemsWithoutConfirm` drops it and `ConfirmCardOverlay` covers the `weight(1f)` feed Box (top bar, composer, bottom nav stay uncovered) with a click-consuming scrim (empty `onClick`, not reject/cancel) plus bottom-aligned `ConfirmToolCard`. New `UserMessage` / `Thinking` / `AgentMessage` keys play PRD §11.7 bubble enter (200ms `FastOutSlowIn`, fade + 8dp up via `graphicsLayer`; `ANIMATOR_DURATION_SCALE == 0f` skips the translation). New `ToolCard` keys stay in `playKeys` but use `ToolSwitchEnterMotion` (150ms `LinearOutSlowIn` fade only, no `translationY`, no 8dp bubble enter) — after thinking and after Confirm overlay closes. Same `listKey` Thinking live→「循环 n」 and ToolCard 准备/正在调用 → 已调用/失败 use `AnimatedContent` 150ms `LinearOutSlowIn` with `sizeTransform = null` (default size spring is extra motion); first composition shows the current state (no from-blank play). Confirm overlay uses `nextConfirmEnter` (250ms `FastOutSlowIn`, fade + slide from the card’s height; duration scale 0 → fade only, no `translationY`). First `ChatRoute` composition with an existing Confirm does not play. Do **not** wrap `ConfirmCard` in bubble enter. Do **not** wrap `ToolCard` in 200ms 8dp bubble enter. Do **not** use `LazyItemScope.animateItem()` or `ModalBottomSheet`. Clicks stay enabled during enter. `ChatScreen` owns `seenKeys` and Confirm `initialized`/`lastKey`; `ChatFeed` only applies `playKeys`.

- Chat composer: `composerText` / schedule draft and attachment chips are hoisted in `MainActivity`. Capture, Photo Picker, and `TakePicture` run in `:app` (`ChatAttachmentSession`); `:feature:chat` only gets `ChatAttachmentUi` (id, kind, width, height) plus menu/preview callbacks. One **附件** menu: **截取屏幕 / 相册 / 拍照**. Chips: **屏幕|相册|拍照 · 宽×高**. × removes that item; send consumes the composer list. Max 4. Overlay still adds a screenshot only. Microphone is hold-to-talk only when ASR layout + JNI are ready (`asrReady`). If the ASR pack is missing, the mic stays visible (grey) and a tap sets `SPEECH_MODEL_MISSING` plus **去下载** (`onOpenSettings`); RECORD_AUDIO is not requested first. While holding, Chat shows the design-folder voice overlay (waveform + pulsing primary mic). Overlay status is `voiceOverlayStatus(holding, transcribing, partial)`: non-blank `partial` replaces **正在录音**; after release **正在进行本地识别...**; footer **音频仅在本地处理，不会离开设备**. Host (`MainActivity`) runs ~400ms Default `snapshot()` + existing OfflineRecognizer on the same Paraformer pack; skip in-flight and &lt; ~0.4s audio; partial decode throw keeps the last overlay line. PCM stays in `:app` / `:tool:system`. `composerValue` (`TextFieldValue`) is unchanged until a successful `stop()` transcript; then `insertVoiceTranscript` at the current selection (replace range if any) and leave the cursor after the spoken text. Do not add OnlineRecognizer or a second ASR catalog row. While the live task is busy (`canCancel`), the same right slot is **终止** (`Icons.Filled.Stop`, `contentDescription` 终止) and calls `onCancel` → `TaskManager.cancel()` (including `AWAITING_CONFIRMATION`; this is not Confirm **拒绝**). Host TTS after a voice send: `:app` sets `speakingReply`; when not busy, Chat swaps the send icon for **停止播报** (`onStopReply`) and shows **正在播报...** on the attachment status row (not error color). If busy and speaking overlap, **终止** also stops speech. The last completed Agent bubble shows **播报** only when TTS is ready (`ttsReady` / `isReplyTtsReady`). Failures use `语音回复暂不可用` on that same row, with **去下载** when the pack is missing. Settings **播报音色** (默认 / 音色一 / 音色二) is enabled only if the TTS row is 已安装; `ttsSpeakerId` persists immediately. No fullscreen TTS overlay.

## Props Conventions

- Navigation is `() -> Unit` callbacks (`onOpenSettings`, `onBack`, `onOpenDebug`), injected from `MainActivity`. Keep those lambdas as the product path; `consumeBack` must encode the same graph so hardware/gesture back does not `finish` while a nested route is showing.
- Domain events are method references (`onSend = viewModel::send`, `onConfirm = viewModel::confirm`, `onCancel = viewModel::cancel`).
- Pass `ChatUiState` / `SettingsFormState` / `MemoryUiState` as one data class, not dozens of scalars.
- Defaults on Route/Screen parameters (`allowCloud: Boolean = false`) exist so previews *could* be added; they are not a second source of truth for prefs. Live `allowCloud` comes from `PreferenceStore` in `MainActivity`.

## Styling Patterns

- Material3 `Text`, `IconButton`, `OutlinedTextField`, `Switch`, `AlertDialog`, `LazyColumn` — plus raw `Modifier.background` / `border` / `clip`.
- Colors: `DougieColors` object copied per feature. Stitch tokens: `primary #3D5198`, `primaryContainer #566AB2`, `surface #F8FAF9` (`feature/chat/.../DougieColors.kt`).
- Type: hardcoded `sp` / `FontWeight` / `FontFamily` in the screen file. No `Typography` theme object.
- Chat avatar drawables: `super_dougie.xml`, `dougie_logo.xml`, `dougie_logo_unavailable.xml`. Launcher in `app/src/main/res` stays `dougie_logo` with inset — not Super/Noob. Top bar is 24sp **Dougie**, then the current window display name (12sp `OnSurfaceVariant`, ellipsis, not clickable; empty unlisted window 「新对话」), then **灵魂** (`Super` / `本地` / `不可用`), then **出境策略** (`已授权云端` / `仅本地`) so the statuses do not wrap on one row. Rename only from History **改名**. Delete a non-default window only from History section **删除**. Delete one History card with that card's **删除** (default-window cards included).
- Bottom nav labels in Chat: **对话 / 记忆 / 任务 / 设置** (product copy).

## Accessibility

Current bar is light, not WCAG-audited:

- Action icons use Chinese `contentDescription` (`发送`, `终止`, `停止播报`, `返回`, `权限中心`, `显示密钥` / `隐藏密钥`, `编辑`, `删除`).
- Decorative / branded images often use `contentDescription = null` (Chat avatar, some status icons).
- Confirm Card is visible buttons (confirm / reject), not a system permission dialog.
- Do not dump API keys into TalkBack: the key field is a password `TextField`; toggle visibility does not log the value.

Do not add a Compose semantics test suite unless the task asks for it — none exists.

## Common Mistakes

- Putting mapping logic in the composable (`if (status == FAILED)`) instead of `AgentTask.toChatUiState()` / `toHistoryItem()`. Chat terminal `AgentMessage.durationLabel` is mapped in `toChatUiState` / `toPastChatItems` (COMPLETED/FAILED + both timestamps) via `:core:model` `formatTaskDuration`; `AgentBubble` draws it 12sp `OnSurfaceVariant` under the bubble and above 「来源：」, not monospace, not inside bubble `text`. Duration, Provider, completed-at, and expand steps on History cards come from `durationLabel` / `providerLabel` / `completedAtLabel` / `steps`, not from parsing `snapshot_json` in Compose. UI tests cannot see mapping; JVM tests can (`ChatUiStateTest`, `HistoryItemTest`, `AgentTaskTest` buckets).
- Treating History **展开** as opening Chat. Nested `TextButton` toggles `remember(taskId)` steps; the card `clickable` still `openConversation`. Expand copy is raw `toolName` + 成功/失败/进行中 — never Chat `toolDisplayName`, `argsSummary`, or `resultJson`.
- Treating History **删除** / **改名** as opening Chat. Section **删除** / **改名** live on the sticky header, consume the click, and never `openConversation`. Default section has **改名** only; other sections add **删除** (danger color) plus a confirm dialog (标题「删除会话」). Card **删除** sits next to **展开** (`DougieColors.Error`), opens confirm 「删除任务」 / 「将删除这一轮，且无法恢复。」, and never `openConversation`. Default-window cards can delete. There is no Chat top-bar delete.
- Hardcoding tool label “电池” for every `ToolCard`. Chat maps known ids to Chinese (`battery` → 电池工具, `js_eval` → 运行脚本, `py_eval` → 运行 Python, `sms_compose` → 发短信, `phone_dial` → 打电话) and otherwise shows raw `toolName`. `confirmToolBody("js_eval")` is 隔离运行脚本，不读写文件、不上网 — do not reuse 写入设备数据. L4 JS confirm uses 按完整脚本运行，结果取最后一次表达式. `confirmToolBody("py_eval")` is 可用沙箱文件处理数据，不能上网或读应用外文件. `confirmToolBody("sms_compose")` / `phone_dial` is 将打开系统短信或拨号并填入内容，需你再按发送或呼叫 — do not reuse 写入设备数据.
- Using the sketch SVG as the default avatar regardless of `IntelligenceMark`.
- Forgetting IME/nav padding (`imePadding`, `navigationBarsPadding`, `statusBarsPadding`) on new full-screen columns — Chat and Settings already do this.
- Treating leftover `previewImage` as a back intercept on Settings/Debug. Preview is a Chat overlay only: `consumeBack` closes it when `route == Chat`; other routes still pop even if the bitmap is still in Activity state.
- Wrapping `ConfirmCard` in bubble enter, wrapping `ToolCard` in 200ms 8dp bubble enter, drawing Confirm in the feed and the overlay, driving enter with `LazyItemScope.animateItem()` / a `firstKey` reset, or dismissing Confirm by tapping the scrim. Resetting enter `seenKeys` when `firstKey` changes, or keeping `seenKeys` only inside `ChatFeed` (empty windows render `EmptyState`, so the first send would seed as “already seen” and skip enter). Confirm overlay must not cover the composer **终止** slot. Merging Thinking and ToolCard into one row, or replaying live→「循环 n」 / tool-label switch when opening an existing window. Leaving `AnimatedContent` default `sizeTransform` on status switch (size spring is extra motion; set `sizeTransform = null`).
