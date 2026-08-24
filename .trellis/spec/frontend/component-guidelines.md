# Component Guidelines

> Jetpack Compose screens in `:feature:*`. This is not a React component library.

## Overview

Each feature exposes a `*Route` composable that collects `StateFlow` and a stateless `*Screen` that takes data + lambdas. `MainActivity` owns navigation (`remember { mutableStateOf(AppRoute) }`) and ViewModel factories. There is no `:core:ui` module yet; `DougieColors` is copied per feature.

Reference files:
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatScreen.kt` (`ChatRoute` / `ChatScreen`)
- `feature/settings/src/main/kotlin/com/dougie/feature/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/dougie/app/MainActivity.kt`
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/DougieColors.kt`

## Component Structure

- `FooRoute(viewModel, onBack, …)`: `collectAsStateWithLifecycle()`, forward events as `viewModel::method`.
- `FooScreen(uiState, onSend, …)`: no `ViewModel`, no `TaskManager`, no OkHttp.
- Private `@Composable` helpers in the same file (`ThinkingChip`, model rows). Do not extract a design-system module for one-off chrome.
- Product copy is **Dougie**, never Waku. User-facing strings are Chinese.

## Props Conventions

Callbacks are named `on*` (`onSend`, `onConfirm`, `onPickModelTree`). Screens receive already-mapped UI models (`ChatUiState`, `OfflineModelsUi`), not `AgentTask` or `DocumentFile`.

Do not pass `android.content.Context` into `:feature:settings` download logic. SAF and `ContentResolver` stay in `:app` (`ExternalModelTreeImpl`).

## Styling Patterns

Material 3 primitives (`TextField`, `OutlinedTextField`, `Button`, `Switch`, `AlertDialog`) plus `DougieColors` tokens (Stitch: primary `#3D5198`, surface `#F8FAF9`). Feature screens use `statusBarsPadding` / `navigationBarsPadding` / `imePadding`. Do not introduce Compose Material theming XML or Tailwind.

Color objects may be duplicated once per `:feature:*`. Extract `:core:ui` only when more than colors is shared.

## Accessibility

No TalkBack suite in CI. Practical rules in code: icon buttons need a `contentDescription` when they have no text; confirm dialogs use Chinese title/body; download confirm must state size (`约 230MB` / `约 116MB` / `约 10–20MB`) before HTTPS. Do not add Espresso a11y checks as a gate.

## Common Mistakes

- Running `LoopEngine` or model probe on Main. Probes use `Dispatchers.Default` (`AppOfflineModelProbe`).
- Showing **Noob-Dougie** as the launcher or default chat avatar (see directory-structure).
- Auto-scanning the SAF tree when Settings opens; only **刷新** or picking a folder scans.
- Using system TTS as intent/ASR/TTS **测试** success.
