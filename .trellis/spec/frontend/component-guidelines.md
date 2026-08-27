# Component Guidelines

> Compose screens in Dougie. There is no React, no `:core:ui` design-system module, and no `@Preview` composables in the tree today.

## Overview

Each feature module owns one primary screen file plus a ViewModel. `:app` `MainActivity` switches a private `AppRoute` enum (`Chat`, `Settings`, `Memory`, `Permissions`, `History`, `Debug`) — not Navigation Compose.

## Component Structure

Pattern used everywhere (`ChatScreen.kt`, `SettingsScreen.kt`, `MemoryScreen.kt`, `HistoryScreen.kt`, `DebugScreen.kt`, `PermissionsScreen.kt`):

1. `FooRoute(viewModel, navigation lambdas)` — `collectAsStateWithLifecycle`, optional `LaunchedEffect` refresh, then `FooScreen(...)`.
2. `FooScreen(uiState, onEvent: ...)` — stateless UI. Local `remember { mutableStateOf }` is allowed for draft text, password visibility, dialogs — not for `TaskStatus`.
3. Private helpers in the same file (bubbles, nav rail, confirm card). Do not extract a new module for a single repeated `Row`.

Chat is the dense case: `ChatRoute` → `ChatScreen` → item `when (ChatItem)` for `UserMessage` / `Thinking` / `ToolCard` / `ConfirmCard` / `AgentMessage`.

- Chat composer: `composerText` / schedule draft and attachment chips are hoisted in `MainActivity`. Capture, Photo Picker, and `TakePicture` run in `:app` (`ChatAttachmentSession`); `:feature:chat` only gets `ChatAttachmentUi` (id, kind, width, height) plus menu/preview callbacks. One **附件** menu: **截取屏幕 / 相册 / 拍照**. Chips: **屏幕|相册|拍照 · 宽×高**. × removes that item; send consumes the composer list. Max 4. Overlay still adds a screenshot only.

## Props Conventions

- Navigation is `() -> Unit` callbacks (`onOpenSettings`, `onBack`, `onOpenDebug`), injected from `MainActivity`.
- Domain events are method references (`onSend = viewModel::send`, `onConfirm = viewModel::confirm`).
- Pass `ChatUiState` / `SettingsFormState` / `MemoryUiState` as one data class, not dozens of scalars.
- Defaults on Route/Screen parameters (`allowCloud: Boolean = false`) exist so previews *could* be added; they are not a second source of truth for prefs. Live `allowCloud` comes from `PreferenceStore` in `MainActivity`.

## Styling Patterns

- Material3 `Text`, `IconButton`, `OutlinedTextField`, `Switch`, `AlertDialog`, `LazyColumn` — plus raw `Modifier.background` / `border` / `clip`.
- Colors: `DougieColors` object copied per feature. Stitch tokens: `primary #3D5198`, `primaryContainer #566AB2`, `surface #F8FAF9` (`feature/chat/.../DougieColors.kt`).
- Type: hardcoded `sp` / `FontWeight` / `FontFamily` in the screen file. No `Typography` theme object.
- Chat avatar drawables: `super_dougie.xml`, `dougie_logo.xml`, `dougie_logo_unavailable.xml`. Launcher in `app/src/main/res` stays `dougie_logo` with inset — not Super/Noob.
- Bottom nav labels in Chat: **对话 / 记忆 / 任务 / 设置** (product copy).

## Accessibility

Current bar is light, not WCAG-audited:

- Action icons use Chinese `contentDescription` (`发送`, `返回`, `权限中心`, `显示密钥` / `隐藏密钥`, `编辑`, `删除`).
- Decorative / branded images often use `contentDescription = null` (Chat avatar, some status icons).
- Confirm Card is visible buttons (confirm / reject), not a system permission dialog.
- Do not dump API keys into TalkBack: the key field is a password `TextField`; toggle visibility does not log the value.

Do not add a Compose semantics test suite unless the task asks for it — none exists.

## Common Mistakes

- Putting mapping logic in the composable (`if (status == FAILED)`) instead of `AgentTask.toChatUiState()` / `toHistoryItem()`. UI tests cannot see that; JVM tests can (`ChatUiStateTest`).
- Hardcoding tool label “电池” for every `ToolCard`. Chat maps known ids to Chinese (`battery` → 电池工具, `time` → 时间工具) and otherwise shows raw `toolName`.
- Using the sketch SVG as the default avatar regardless of `IntelligenceMark`.
- Forgetting IME/nav padding (`imePadding`, `navigationBarsPadding`, `statusBarsPadding`) on new full-screen columns — Chat and Settings already do this.
