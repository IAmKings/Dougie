# Phase 5 系统入口：通知 / Tile / 悬浮窗 / 定时

## Goal

在 Android 上提供离开 Chat 也能触达 Agent 的系统入口。本父任务只拥有地图与跨子项验收；实现落在 child。`:cli` 不在范围。

## User value

用户可从系统 UI 回到或观察 Agent，且不绕过出境策略与确认卡。禁止读取全部通知（`PRD.md` §3.2）。

## Confirmed facts

- `PRD.md` §15 Phase 5：Notification、Quick Settings、Floating Widget、Scheduled Agent。
- 无 `TileService` / overlay / 定时 Agent；截屏前台通知仅在 `:tool:system`。
- 第一 child（用户接受建议并要求推进）：`08-25-qs-tile` — Tile 只打开对话，不 submit。

## Task map

| Child | 状态 | 独立验收 |
|---|---|---|
| `08-25-qs-tile` | planning | 快捷设置 Tile 打开 Chat；`checkChannelLeak` 绿 |
| Notification Agent | 未建 | 任务进度通知；无 NotificationListener |
| Floating Widget | 未建 | overlay 气泡；独立权限说明 |
| Scheduled Agent | 未建 | 本地调度走既有 Loop |

依赖写在各 child 文档。通知 / 悬浮窗 / 定时 **不** 依赖 Tile 完成，但本回合不实现它们。

## Requirements

- R1 父任务不写产品代码。
- R2 入口复用 `TaskManager` / 出境 / 确认卡；不新开 Loop。
- R3 Play 无 NotificationListener、无 Accessibility 泄漏。
- R4 mosaic / `:cli` 不进 APK。

## Out of scope

- `:cli`、桌面端、向量记忆、端侧对话 LLM。
- 自动读取或摘要全部通知。
- 本回合实现通知 / 悬浮窗 / 定时。

## Acceptance Criteria（父级，本回合）

- [ ] `08-25-qs-tile` 按该 child AC 归档。
- [ ] Play `checkChannelLeak` 仍通过。
