# Chat 语音意图路由

## Goal

用户点发送后（打字或按住说话进草稿后再发），若本地意图包与引擎就绪，对正文做一次 ONNX 分类。仅高置信 `query_time` / `query_battery` 跳过云端 LLM，经 `PolicyEngine` 直接执行 `time` / `battery`，用固定中文模板写 `finalAnswer`。其余情况走现有 Loop。按住说话仍不自动提交。

## User value

「现在几点」「还有多少电」可离线、更快；未授权云端时也能答。复杂话术与其它意图仍走 Loop。

## Background

- UF-02：ASR → 分类 → Loop → TTS。ASR 草稿与宿主 TTS 已落地；`ChatViewModel.send` / `TaskManager.submit` 今日直接 `LoopEngine.run`，发送前不分类。
- `IntentClassifierTool`（`intent_classifier`，L0）成功 JSON：`ok` + `intent` + `slots` + `route` + `confidence`；`confidence < 0.5` → `INTENT_LOW_CONFIDENCE`；缺包/缺引擎中文失败。Tool **不得**静默改走云端。路由层缺包时 **不得调用** 该 Tool（否则会失败挡发送）。
- testdata `labels.txt`：`query_time`、`query_battery`、`query_calendar`、`create_calendar`、`query_location`、`clipboard_read`、`clipboard_write`、`open_app`、`screen_capture`、`speech_input`、`unknown`。`OnnxIntentEngine.routeFor`：`query_time`→`time`，`query_battery`→`battery`。
- `time` / `battery` 为 L0；分类不经 `EgressGateway`。Play 不内置意图 ONNX。
- 已拍板：打字发送也分类；高置信 L0 时间/电量跳过 LLM。

## Requirements

- R1 每次 `submit` 非空正文：意图包与引擎均就绪时，对 `AgentTask.input` 做一次 `IntentPort.classify`（打字与语音发送相同；`retry` 同样）。
- R2 包缺失、引擎未就绪、分类抛错、低置信、`unknown`、非本 MVP 标签：不挡发送，不把分类结果写入 Prompt / Logcat / `AuditLog`，走现有 Loop。
- R3 仅 `query_time` → 工具 `time`、`query_battery` → 工具 `battery`（`confidence >= 0.5`）走短路径：`PolicyEngine`（L0 为 Allow）→ `execute("{}")` → 中文模板 `finalAnswer` → `COMPLETED`。不调用 `LlmProvider` / `EgressGateway.stream`。L2 确认卡契约不变（本 MVP 短路径不含 L2）。
- R4 有附件（含截屏 pin）不走短路径，改走 Loop（分类可跳过）。
- R5 不改 `intent_classifier` 成功 JSON。分类文本与 `intent`/`slots`/`route` 不入日志；`AuditLog` 只记短路径上的 `time`/`battery` 名与 SUCCESS/FAILED。
- R6 Play 不内置意图权重。自动播报仍只看 `speakReply`（短路径 `COMPLETED` 后现有 TTS 规则照旧）。
- R7 气泡用户原文不被分类结果改写。

## Out of scope

- 流式 ASR、端侧对话 LLM、Kokoro。
- 日历 / 定位 / 剪贴板 / 开 App / 截屏 / `speech_input` 的无 LLM 短路径（槽位与 L1/L2 另议）。
- 自动执行 L3 tap/swipe。
- 把分类结果注入云端 Prompt。

## Technical notes

- 分类走 `IntentPort`，不要把 `intent_classifier` 登记进本次 toolTrace（缺包时该 Tool 会失败挡发送）。
- `time` 结果：`iso_local` / `zone`；`battery`：`battery_percent` / `charging`。模板解析失败 → `TOOL_FAILED`。
- `LoopEngine` 注入可选 `IntentPort`（默认 null：测试与 CLI 行为与今相同）。App 传入现有 `intentPort`。

## Acceptance Criteria

- AC1 包+引擎就绪、高置信 `query_time`：任务 `COMPLETED`，`finalAnswer` 含可读时间，测试用 Fake LLM **零次** `stream`。
- AC2 同上 `query_battery`：`finalAnswer` 含电量百分比与充电与否，Fake LLM 零次 `stream`。
- AC3 缺包或引擎未就绪：与今相同走 Loop；`classify` 不被调用。
- AC4 低置信 / `unknown` / `query_calendar` 等高置信非 MVP 标签：走 Loop；Prompt 不含 intent 字段。
- AC5 带 SCREEN 附件的「现在几点」走 Loop。
- AC6 短路径成功时 `AuditLog` 工具名为 `time` 或 `battery`，不含分类文本。
- AC7 `:app:checkChannelLeak` 仍禁止 Play APK 含 `models/intent` / `*.onnx`。
