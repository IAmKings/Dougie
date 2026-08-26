# 定时触发既有 Agent Loop

## Goal

用户在本机预约到点提醒：通知点按打开对话，输入框预填该条草稿，**不**自动 `submit`。每条可勾选 **每日重复**（默认关）。

## User value

离开 Chat 也能在约定时刻被叫回来；一次性或每天同一时刻自选。Loop 仍由用户点发送。

## Background

- 父任务 `08-25-phase-5-system-surfaces`。Tile / 通知 / 悬浮窗已归档。
- `PRD.md` §10.3：调度不是常驻 Agent Runtime。
- 仓库无 WorkManager / AlarmManager。`chatLaunchIntent` 只开 Chat。Chat 草稿是 `ChatScreen` 本地 state。Tile 契约：Intent extra 不含 prompt。
- 日历「明天下午三点开会」仍走 Calendar Tool。
- **Q1** 到点 = 通知 + 开 Chat，不 `submit`。
- **Q2** 每条「每日重复」开关。
- **Q3** 点通知后用本地 id 预填输入框；通知栏与 extra 不含原文。

## Requirements

- R1 到点发独立通知渠道（勿占用任务进度 id 48）；点按 `chatLaunchIntent` + `scheduleId` extra，不 `submit`。
- R2 条目：时刻、可选草稿、每日重复（默认关）。一次性响完删除；每日按本地时区排次日同时刻。
- R3 最多 8 条。设置页增删改。无精确闹钟权限时仍可存，文案说明可能推迟。
- R4 杀进程 / 重启后从磁盘重注册闹钟（`BOOT_COMPLETED`）。
- R5 通知正文固定「定时提醒」类文案；extra / Logcat 不含草稿。
- R6 Play `checkChannelLeak` 既有针不变。不引入 NotificationListener。

## Out of scope

- 到点 `submit`、周中选日、多时区、自然语言进日历、WorkManager 周期 15 分钟粒度替代闹钟。
- `:cli`、桌面、保证 OEM 到秒、`USE_EXACT_ALARM`（非闹钟应用）。

## Acceptance Criteria

- [x] AC1 设置中可建条目；每日重复开关生效；可删；最多 8 条。
- [x] AC2 一次性到点后条目消失；每日到点后仍在并预约次日。
- [x] AC3 点通知进 Chat，输入框为该条草稿，不 `submit`、不新开 `taskId`。
- [x] AC4 通知栏与 Intent extra 无草稿原文。
- [x] AC5 重启后仍能响（已授权通知/闹钟的前提下）。
- [x] AC6 `./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak` 通过。
