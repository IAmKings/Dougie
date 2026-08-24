# Hook Guidelines

> Compose `remember` / `LaunchedEffect` / activity-result launchers. There are no React `use*` hooks and no Retrofit/OkHttp in `:feature:*`.

## Overview

Stateful UI logic lives in `*ViewModel` + `StateFlow`. Composables only remember scroll/list/launcher state and refresh when a route is shown.

Reference files:
- `feature/memory/src/main/kotlin/com/dougie/feature/memory/MemoryScreen.kt` (`LaunchedEffect(Unit) { viewModel.refresh() }`)
- `feature/history/src/main/kotlin/com/dougie/feature/history/HistoryScreen.kt`
- `feature/debug/src/main/kotlin/com/dougie/feature/debug/DebugScreen.kt`
- `feature/chat/src/main/kotlin/com/dougie/feature/chat/ChatScreen.kt` (`rememberLazyListState`, `LaunchedEffect` scroll)
- `app/src/main/kotlin/com/dougie/app/MainActivity.kt` (`rememberLauncherForActivityResult` for `OpenDocumentTree`)
- `feature/permissions/src/main/kotlin/com/dougie/feature/permissions/PermissionsScreen.kt` (runtime permission + projection launchers)

## Custom Hook Patterns

Do not add a `useFoo` layer. If several screens need the same effect, put it in the ViewModel or a small named composable in that feature (`ChatRoute`), not a shared hooks package.

`ViewModelProvider.Factory` inner classes (e.g. `ChatViewModel.Factory`) are the DI seam. `MainActivity` constructs them with `DougieApplication` fields.

## Data Fetching

- Chat: collect `TaskManager.task` (already mapped in `ChatViewModel`). Never open SQLite in `:feature:chat`.
- Memory / History / Debug: `refresh()` on first composition of the route (`LaunchedEffect(Unit)`). Activity-scoped ViewModels otherwise keep an empty list after Chat writes a fact.
- Settings form is local until **保存配置**. `modelTreeUri` is written immediately via `PreferenceStore.setModelTreeUri`.
- Offline model HTTP is `ModelInstaller` in `:core:tool`, triggered from `OfflineModelDownloads` after confirm — not from a Compose effect.

## Naming Conventions

- Remembered UI: `rememberLazyListState`, `rememberScrollState`, `rememberLauncherForActivityResult`.
- One-shot refresh: `LaunchedEffect(Unit)` on the Route, not only `init {}` in the ViewModel.
- Collect: `collectAsStateWithLifecycle()`, not `collectAsState()`.

## Common Mistakes

- Forgetting `MemoryRoute` `refresh()` → empty memory list after ingest (already documented in state-management).
- Putting `OpenDocumentTree` inside `:feature:settings`. The picker stays in `MainActivity`.
- Starting a probe/`LoopEngine` from `LaunchedEffect` without a user click.
- A second `mutableStateOf` copy of `TaskStatus` (map from `AgentTask` only).
