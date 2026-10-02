# 树状态提取器与短信编辑验证

## Goal

给动作验证层补上 State Extractor 段：无障碍树快照端口（内容级状态提取，纯本机），并以 sms_compose 为第一个内容验证对象——短信编辑器打开后确认「正文确实出现在当前窗口」，验证失败走既有 FAIL 分支（LLM 回灌重规划 / 非致命观察），不再「composer 打开即成功」。收件人正确性由 `ACTION_SENDTO` URI 构造保证，不作为观察目标（联系人匹配会把号码显示成姓名，纳入针会制造假阴性）。

## User value

- 用户让 Dougie 填短信草稿后，若编辑器里实际没有正文（系统吞了 `sms_body`、应用兼容性问题），Dougie 如实说「未能确认是否成功」并重试，而不是答「已打开短信」。
- 树快照纯本机消费：不出进程、不进 JSON/日志/LLM 上下文，与设置页「数据不出境」承诺对齐。

## Confirmed facts

- `accessibility_service_config.xml` 已有 `canRetrieveWindowContent="true"` 与 `flagRetrieveInteractiveWindows`，树读取无需改配置（`rootInActiveWindow` 已被 `AndroidGesturePort.foregroundPackage()` 使用）。
- `AndroidTelecomPort.composeSms` 用 `ACTION_SENDTO smsto:<canonical>` + `sms_body` extra 打开默认短信应用编辑器；地址即 URI 内容，正文由 extra 预填。
- `SmsComposeTool` 现状：`composeSms` 返回 ok 即成功（`{"ok":true}`），幂等键 `taskId + toolCallId`，`isFatal` 前置检查 Dougie 前台；验证层语义（verified 三态、失败非致命、失败不进幂等缓存）已在 10-02-verification-layer 立约，`AppIntentTool` 是接入范本。
- LoopEngine 的 verify 分支是工具无关的（trace 计数预算 + resultJson 回灌），本任务无需改 runtime。
- `sms_compose` 无本地捷径路径（协议明令不教该工具），只经 LLM 路径执行。
- 隐私红线（logging-guidelines）：短信内容禁止出现在日志/审计/trace JSON；验证失败结果 JSON 只含错误文案与 verified 布尔。
- 上一任务教训：探针必须是「每次调用时取用、可为 null」的 provider 形态（`() -> TreeSnapshotPort?`），否则无观察渠道会假报验证失败。

## Requirements

- R1 `core/tool` 新增 `TreeSnapshot`（`texts: List<String>`，可见文本 + contentDescription，带节点数上限）与 `TreeSnapshotPort`（`suspend fun snapshot(): TreeSnapshot?`，null=无观察者/无活动窗口）。快照仅在工具进程内消费，禁止序列化进 ToolResult JSON、日志、审计。
- R2 `core/tool` 新增 `TreeVerifier`：`awaitContains(port, needle, timeoutMs, pollIntervalMs)`——立即查一次，未命中轮询至超时；匹配为**空白不敏感包含**（双方过滤所有空白字符后做子串匹配，兼容号码分组显示与多行正文）。默认 5s/250ms，与 ForegroundVerifier 同款虚拟时间可测设计。
- R3 `SmsComposeTool` 接入：构造器加 `treeProbe: () -> TreeSnapshotPort? = { null }` 与超时参数；`composeSms` ok 后验证 needle = 正文；`verified=true` → JSON 注入 `"verified":true` 并写幂等缓存；`verified=false` → 非致命 `{"ok":false,"error":VERIFY_FAILED,"verified":false}` 且**不写缓存**；无探针 → `verified=null` 行为与今日字节一致。幂等重放保留 verified 标志。
- R4 `tool/accessibility` 新增 `AccessibilityTreeSnapshotPort`：从 `rootInActiveWindow` BFS 收集 text/contentDescription，节点上限 300，空活动窗口返回 null。
- R5 接线：app main 加 `TreeProbeBridge`（与 `ForegroundProbeBridge` 同款持针模式）；`DougieApplication` 把 provider 传给 SmsComposeTool；sideload `ChannelTools` 填充 bridge；Play 零改动（恒 verified=null）。
- R6 不改 LoopEngine/PolicyEngine/14 条冻结契约；不新增 AgentTool；不碰 Compose UI；验证失败复用既有 `UserFacingErrors.VERIFY_FAILED`。
- R7 编辑器可能把号码渲染成分组格式（空白差异）——空白不敏感匹配必须覆盖；联系人匹配显示姓名的场景不视为验证失败（收件人不设针）。

## Acceptance Criteria

- [x] AC1 JVM 测试：快照含正文 → `verified==true` 且结果 JSON 含 `"verified":true`；正文带空白/换行变体（如显示为分组数字或多行文本）时同样命中。
- [x] AC2 JVM 测试：快照持续不含正文 → `verified==false` 非致命、不写 IdempotencyStore、结果 JSON 不含正文内容（只含错误文案与 verified 布尔）。
- [x] AC3 JVM 测试：无探针 provider（Play 场景）→ `verified==null`，成功 JSON 与改动前字节一致（`{"ok":true}`）。
- [x] AC4 JVM 测试：幂等重放保留 verified 标志；重试用新 toolCallId 时真实重新执行。
- [x] AC5 JVM 测试：TreeVerifier 轮询语义（立即命中 / N 次后命中 / 超时未命中 / 快照 null 视为未命中继续轮询）。
- [x] AC6 全量 `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak` 通过；`LocalToolContractEvalTest` 不改动且全绿。

## Out of scope

- tap_swipe 截屏差分验证（授权耦合，另立任务）。
- app_intent / dial 的树级内容验证（前景包名验证已覆盖）。
- 树快照的 bounds/结构化视图 id 提取（验证只消费文本）。
- 把树内容喂给 LLM 做语义判断（禁止；验证只做确定性包含检查）。
