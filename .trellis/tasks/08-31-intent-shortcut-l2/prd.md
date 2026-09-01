# L2 写操作短路径

## Goal

高置信 `create_calendar` / `clipboard_write`，且规则能抽出合法工具参数时：现有 L2 确认卡 → 执行 → 中文模板结束，不调云端。抽不出参数则与低置信相同，走现有 Loop。

## Background

- 查询短路径已覆盖 time / battery / calendar_query / clipboard_read / location。`completeFromIntentIfMatched` 目前一律 `argsJson = "{}"`。
- `calendar_create` 必填 `title`+`startIso`（L2，`WRITE_CALENDAR`）。`clipboard_write` 必填 `text`（L2）。缺字段经 Sanitizer → `INVALID_TOOL_ARGS`。
- L2 走 `NeedsConfirmation`；拒绝 → `CONFIRM_REJECTED`。LLM 路径确认后还会再 `stream` 一轮写终答；短路径确认成功后应直接模板 `COMPLETED`。
- Chat `ConfirmCard` 已展示 `argsJson`。AuditLog 仍只有工具名与成败。

## Requirements

- R1 映射：`create_calendar`→`calendar_create`，`clipboard_write`→`clipboard_write`。`MIN_CONFIDENCE` 不变。
- R2 仅当从 `input`（及 normalize 后的 classify 文本）抽出可过 Sanitizer 的 JSON 才进短路径；否则 `completeFromIntentIfMatched` 返回 null，走 LLM。不猜时间、不把「这句话」当剪贴板正文。
- R3 确认并执行成功：中文模板、`completionPath=LOCAL_INTENT`、不再 LLM。
- R4 拒绝确认、缺写日历权限、工具失败：现有 Halt，不回落 LLM。
- R5 开 App / 截屏 / 听写 / 查询短路径不变。附件仍跳过短路径。

## Acceptance Criteria

- [x] AC1 「明天下午三点开会」高置信：确认卡含 title 与 startIso；确认后不调 LLM，气泡已创建日程类模板；开发者页本地意图。
- [x] AC2 「把『你好』写到剪贴板」高置信：确认后写入并模板成功；拒绝则 `CONFIRM_REJECTED` 且 `streamCount=0`。
- [x] AC3 「帮我定个日程」（无时刻）即使分类为 create_calendar 仍走 LLM。
- [x] AC4 `unknownAndCreateCalendarIntentsUseLlm` 改为：无槽位的 create 仍 LLM；有槽位的走短路径单测。

## Out of scope

- `app_intent`、截屏、听写短路径。
- 「下下周三」等复杂日期、多事件、改模型权重。
- 槽位进 AuditLog / Logcat。

## Technical notes

- 槽位解析与 `formatFinalAnswer` 放 `IntentRouteAnswers`（或同模块纯函数），JVM 单测钉死样例。
- `startIso` 用设备默认时区的 offset datetime，满足 `CalendarCreateTool`。
- 剪贴板：引号内正文；无引号且无明确拷贝片段 → 不抽。
