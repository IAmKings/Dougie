# 任务进度通知（无 Listener）

## Goal

用户离开 Chat 时，仍能从系统通知栏看到**当前 Agent 任务**的状态机，点通知回到对话页。不读取、不摘要其他 App 的通知。

## User value

切走后仍知道 Loop 在跑、待确认或已结束；L2 确认只在 App 内完成。

## Background

- 父任务 `08-25-phase-5-system-surfaces`。`08-25-qs-tile` 已归档。用户采纳状态机正文（不含终答/输入）。
- `PRD.md` §15 Phase 5 Notification；§3.2 禁止自动读取全部通知。`checkChannelLeak` 禁止 Play `NotificationListenerService`。
- 证据：仅截屏 FGS 使用渠道「屏幕截取」。`TaskManager.task` 为唯一源。打开 Chat 复用 `chatLaunchIntent`。权限中心尚无通知项（`PermissionsViewModel.buildItems`）。
- `AndroidPermissions` 在 `core/model/.../AgentTask.kt`，尚无 `POST_NOTIFICATIONS`。
- 依赖：无。

## Requirements

- R1 `:app` 收集 `taskManager.task`，发/更新/取消独立渠道通知。不 `submit`，不读 `input` / `finalAnswer` / `lastError` / `streamingText` / `argsSummary` / `resultJson`。
- R2 点击通知与 Tile 相同：`chatLaunchIntent`（`OPEN_CHAT`）。
- R3 无 `NotificationListenerService`。Play 与 sideload 同一实现。截屏渠道不改。
- R4 标题 **Dougie**。正文仅：中文状态 + `loopCount` + 最近 `toolName`（若有）。`AWAITING_CONFIRMATION` 为「待确认」+ tool 名。`FAILED` 为「任务失败」+ 循环，不贴 `lastError`。
- R5 API 33+：`POST_NOTIFICATIONS`。未授权不崩溃、不发通知。权限中心增加「通知」行（仅 API 33+）。进程内首次出现忙任务且未授权时可请求一次；拒绝后本进程不再弹。
- R6 `task == null` 或 `IDLE` 时取消该通知。忙任务 `ongoing`；`COMPLETED`/`FAILED` 非 ongoing，保留直到下一次任务或用户划掉。

## Out of scope

- NotificationListener、通知栏确认 Tool、从通知发新任务。
- 悬浮窗、定时 Agent、`:cli`、改截屏 FGS 文案。
- 通知里展示终答、用户原话、错误长文。

## Acceptance Criteria

- [ ] AC1 一次 Loop 期间通知正文随 `status`/`loopCount`/最近 tool 名更新，不含 `input`/`finalAnswer`/`lastError` 原文。
- [ ] AC2 点通知打开 Chat，不因点击产生新 `taskId`。
- [ ] AC3 Play merged manifest 无 `NotificationListenerService`；`checkChannelLeak` 通过。
- [ ] AC4 JVM 单测覆盖正文格式（含 FAILED 不含错误原文、null 任务表示取消）。
- [ ] AC5 API 33+ 拒绝通知权限时 App 不崩、Chat 仍可用。
