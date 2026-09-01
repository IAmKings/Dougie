# 短路径扩到日历查询 / 剪贴板读取 / 定位

## Goal

高置信 `query_calendar`、`clipboard_read`、`query_location` 与时间/电量一样走本地短路径：Policy → `execute("{}")` → 中文模板 `finalAnswer`，跳过云端 LLM。开发者页仍显示 `本地意图`。

## Background

- 现 `IntentRouteAnswers.toolNameFor` 仅 `query_time`→`time`、`query_battery`→`battery`。`LoopEngineTest.unknownAndCalendarIntentsUseLlm` 把 `query_calendar` 钉死走 LLM。
- 工具已在 App 注册：`calendar_query` / `clipboard_read` / `location` 均为 **L1**。`PolicyEngine`：缺权限 `DeniedPermission`；L1 不弹确认卡。`execute("{}")` 合法（日历默认 limit 10）。
- JSON：`{"events":[{"id","title","startMs"}]}`；剪贴板 `ok`+`text`；定位 `ok`+`latitude`+`longitude`+`accuracy_m`。
- 分类原文 / intent 仍不进 AuditLog / Logcat。聊天气泡会显示模板（日程标题、剪贴板正文）——与 LLM 调同一 Tool 时一致。

## Requirements

- R1 映射：`query_calendar`→`calendar_query`，`clipboard_read`→`clipboard_read`，`query_location`→`location`。置信度门槛仍 `IntentModelLayout.MIN_CONFIDENCE`。
- R2 短路径成功：中文模板；空日程 / 空剪贴板有固定句。剪贴板正文截断到 200 字。
- R3 缺日历/定位权限、剪贴板非前台、工具失败：现有 `executeToolPass` Halt（`PERMISSION_DENIED` 等），**不**回落 LLM。`completionPath=LOCAL_INTENT`。
- R4 `create_calendar`、`clipboard_write`、`open_app`、`screen_capture`、`speech_input`、`unknown` 仍走 Loop。
- R5 附件仍跳过短路径。时间/电量行为不变。

## Acceptance Criteria

- [ ] AC1 高置信「今天有什么日程」不调 LLM，气泡为日程摘要或「最近没有日程。」
- [ ] AC2 高置信剪贴板读取 / 定位同上，开发者页 `本地意图`。
- [ ] AC3 `query_calendar` 的旧「走云端」单测改为短路径；`create_calendar` 仍走 LLM。
- [ ] AC4 无日历权限时失败文案为既有 `PERMISSION_DENIED`，`streamCount=0`。

## Out of scope

- 写日历、写剪贴板、开 App、截屏、听写短路径。
- 改分类模型 / 语料 / Release。
- 把日程标题或剪贴板打进 AuditLog。
- 槽位抽取（日期范围、指定日历）。

## Technical notes

- 改 `IntentRouteAnswers` + `LoopEngineTest`；模板解析失败 → `TOOL_FAILED`。
- `clipboard_read` JSON 含用户文本，只进 `finalAnswer`，不进日志。
