# Phase 5 系统入口：通知 / Tile / 悬浮窗 / 定时

## Goal

在 Android 上提供离开 Chat 也能触达 Agent 的系统入口。本父任务只拥有地图与跨子项验收；实现落在 child。`:cli` 不在范围。

## User value

用户可从系统 UI 回到或观察 Agent，且不绕过出境策略与确认卡。禁止读取全部通知（`PRD.md` §3.2）。

## Confirmed facts

- `PRD.md` §15 Phase 5：Notification、Quick Settings、Floating Widget、Scheduled Agent。
- 证据：`08-25-qs-tile` 已归档（Tile 打开 Chat）。截屏前台通知仍在 `:tool:system`。无定时 / overlay。
- 下一 child：`08-25-notification-agent`（规划中）。

## Task map

| Child | 状态 | 独立验收 |
|---|---|---|
| `08-25-qs-tile` | archived | 快捷设置 Tile 打开 Chat |
| `08-25-notification-agent` | planning | 任务进度通知；无 NotificationListener |
| Floating Widget | 未建 | overlay 气泡 |
| Scheduled Agent | 未建 | 本地调度走既有 Loop |

依赖写在各 child 文档。通知不依赖 Tile。悬浮窗 / 定时仍未立项。

## Requirements

- R1 父任务不写产品代码。
- R2 入口复用 `TaskManager` / 出境 / 确认卡；不新开 Loop。
- R3 Play 无 NotificationListener、无 Accessibility 泄漏。
- R4 mosaic / `:cli` 不进 APK。

## Out of scope

- `:cli`、桌面端、向量记忆、端侧对话 LLM。
- 自动读取或摘要全部通知。
- 本父任务本回合实现范围以当前 child 为准（现为通知）。

## Acceptance Criteria（父级）

- [x] `08-25-qs-tile` 归档。
- [ ] 当前 child 按该 child AC 归档。
- [ ] Play `checkChannelLeak` 仍通过。
