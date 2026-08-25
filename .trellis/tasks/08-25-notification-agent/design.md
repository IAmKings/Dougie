# Design: 任务进度通知

## Boundaries

| Layer | Owns |
|-------|------|
| `:core:model` | Optional `AndroidPermissions.POST_NOTIFICATIONS` constant only |
| `:app` | Channel, `TaskProgressNotifier`, `formatTaskNotice(task): String?`, collect `task` in `DougieApplication`, PendingIntent → `chatLaunchIntent` |
| `:feature:permissions` | API 33+ 通知行 + runtime request (same pattern as calendar) |
| `:feature:chat` | Unchanged send path |
| `:tool:system` | Capture FGS channel unchanged (`屏幕截取`) |

## Copy contract (`formatTaskNotice`)

Return `null` → cancel notification.

| Status | Text shape (Chinese) |
|--------|----------------------|
| PREPARING / THINKING | `思考中 · 循环 {n}` |
| TOOL_PENDING / TOOL_EXECUTING / TOOL_RESULT | `工具 · 循环 {n}` + optional ` · {lastToolName}` |
| AWAITING_CONFIRMATION | `待确认` + optional ` · {lastToolName}` |
| COMPLETED | `已完成 · 循环 {n}` |
| FAILED | `任务失败 · 循环 {n}` |
| IDLE / null | `null` (cancel) |

`lastToolName` = last `toolTrace` entry’s `toolName` only (never `argsSummary`).

## Android

- Channel id `dougie_task_progress`, name **任务状态**, `IMPORTANCE_LOW` (not capture channel).
- Notification id dedicated, not capture FGS id.
- `setContentTitle("Dougie")`, `setContentText(line)`, `setContentIntent` immutable PendingIntent, `setOnlyAlertOnce(true)`.
- `POST_NOTIFICATIONS` in `app` manifest (`uses-permission` maxSdk not required; 13+ runtime).
- Collect on `Dispatchers.Main.immediate` or Main so `NotificationManager.notify` is straightforward; mapping is pure.

## Permission UX

- `PermissionsViewModel`: item id `notifications`, title **通知**, subtitle **任务进行时显示状态，不含对话原文**，`POST_NOTIFICATIONS`, L0/L1, only added when `SDK_INT >= 33`.
- `MainActivity` or notifier: if busy task and API 33+ and not granted, `requestPermissions` once per process (`AtomicBoolean`).

## Compatibility

- minSdk 26: no runtime POST_NOTIFICATIONS; posting allowed.
- `checkChannelLeak` already forbids Listener; keep that assert. Do not treat `POST_NOTIFICATIONS` as leak.

## Trade-offs

- Status-only vs answer in shade: user chose status-only (privacy / lockscreen).
- Application collector vs ChatViewModel: Application sees tasks even if Chat is not composed (History/Settings). Required.

## Rollback

Remove collector, channel, permission row, manifest permission. Users may have a leftover notification until dismissed.
