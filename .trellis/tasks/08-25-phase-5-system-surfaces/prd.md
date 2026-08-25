# Phase 5 系统入口：通知 / Tile / 悬浮窗 / 定时

## Goal

在 Android 上提供离开 Chat 也能触达 Agent 的系统入口。本父任务只拥有地图与跨子项验收；实现落在 child。`:cli` 不在范围。

## User value

用户可从系统 UI 回到或观察 Agent，且不绕过出境策略与确认卡。禁止读取全部通知（`PRD.md` §3.2）。

## Confirmed facts

- Tile 与任务进度通知已归档。悬浮窗已验收：Play 通知气泡 / sideload `SYSTEM_ALERT_WINDOW`。
- 下一项：Scheduled Agent（未建）。

## Task map

| Child | 状态 | 独立验收 |
|---|---|---|
| `08-25-qs-tile` | archived | 快捷设置 Tile 打开 Chat |
| `08-25-notification-agent` | archived | 任务进度通知；无 Listener |
| `08-25-floating-widget` | 验收通过 | Play 气泡 / sideload 真悬浮；点开 Chat |
| Scheduled Agent | 未建 | 本地调度走既有 Loop |

## Requirements

- R1 父任务不写产品代码。
- R2 入口复用 `TaskManager` / 出境 / 确认卡；不新开 Loop。
- R3 Play 无 NotificationListener、无 Accessibility 泄漏。
- R4 mosaic / `:cli` 不进 APK。

## Out of scope

- `:cli`、桌面端、向量记忆、端侧对话 LLM。
- 自动读取或摘要全部通知。

## Acceptance Criteria（父级）

- [x] `08-25-qs-tile` 归档。
- [x] `08-25-notification-agent` 归档。
- [x] `08-25-floating-widget` 按 child AC 验收。
- [x] Play `checkChannelLeak` 仍通过。
