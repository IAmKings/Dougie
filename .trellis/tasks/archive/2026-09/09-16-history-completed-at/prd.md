# 任务卡显示完成时刻

## Goal

底栏「任务」每张已结束的卡，在现有耗时、Provider 之外再显示 **执行完成的本地时刻**。

## User value

耗时说明长短；完成时刻把这一轮放到今天或昨天的时间线上。

## Background

- 「展开工具步骤」已验收、尚未提交。本刀只加完成时刻。
- `endedAt` 已在 snapshot。旧卡缺字段则无耗时，本刀同样不编造完成时刻。
- 现网元信息：`耗时 · Provider`。不能用 `updated_at`。不 bump DB。Chat / Debug 不加。
- minSdk 26，`java.time` 可用。

## Requirements

- R1 有 `endedAt` 则显示完成时刻；缺则省略。进行中无 `endedAt` → 不显示。
- R2 文案用设备本地时区、精确到分：同一天「今天 14:32」；前一天「昨天 14:32」；同年更早「9月15日 14:32」；跨年「2025年9月15日 14:32」。不用秒、不用相对「3分钟前」。
- R3 元信息行：`listOfNotNull(duration, provider, completedAt).joinToString(" · ")`。
- R4 `formatCompletedAt(endedAt, nowMs, zone)` 纯函数，Compose 不解析 epoch。不 bump DB。不 log 输入。

## Acceptance Criteria

- [x] AC1 有 `endedAt` 的卡显示完成时刻；缺字段不显示。
- [x] AC2 固定时区单测覆盖今天 / 昨天 / 同年更早 / 跨年；分钟向下取整（14:32:59 → 14:32）。
- [x] AC3 元信息可同时有 `3秒 · 本地 LLM · 今天 14:32`；只有完成时刻时不带多余 ` · `。
- [x] AC4 `./gradlew :feature:history:testDebugUnitTest`（JDK 17）通过。

## Out of scope

展开交互、Chat/Debug 时刻、相对时间、开始时刻、秒、DB bump、`updated_at`。

## Key Decisions

- 用已有 `endedAt`，不是 `startedAt` 也不是表列 `updated_at`。
- 今天/昨天 + 钟点；更早写日期。
- 接在耗时、Provider 后面。
