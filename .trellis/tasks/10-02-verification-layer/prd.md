# 动作验证层（Verification Layer）

## Goal

Dougie 的工具动作不再「dispatch 成功即成功」：执行后用确定性的本机观察确认效果真的发生，验证失败作为观察反馈给重规划（retry/replan），而不是静默成功或直接判死。对应架构链路：

Action Planner → Policy Engine → Tool Executor → Android Action → **Observer → State Extractor → Verification** → PASS(下一步) / FAIL(retry/replan)

本期以「打开应用/链接」（app_intent）为第一个验证对象，把契约、观察原语、验证器、循环层 FAIL 分支一次立起来。

## User value

- 用户说「打开微信」，若应用实际没到前台，Dougie 明确说「未能确认是否成功」并重试/重规划，而不是答「已打开」。
- LLM 看到的工具结果是诚实信号（含 verified 字段），从源头掐断「工具自报成功 → 模型幻觉已完成」的链路。

## Confirmed facts

- `LoopEngine.executeToolPass` 现状：工具没抛异常且 `isFatal==false` 即记 SUCCESS；异常/超时/`isFatal=true` 一律 `ToolPass.Halt` → 任务 FAILED。无验证、无重试。
- `TapSwipeTool` 成功 JSON 是入参回显（`{"ok":true,"action":"tap",...}`）；`AppIntentTool` 以 `port.launchView` 返回 ok 为成功；`AndroidAppIntentPort.launchView` 不检查启动结果。
- `ChatPromptAssembler` 会把带 `resultJson` 的 trace 注入 LLM 上下文（本地与云端皆是），observation 反馈通道现成。
- `GesturePort.foregroundPackage()`（tool/accessibility，sideload-only）是全库唯一「查任意前台包名」原语；`DougieAccessibilityService` 只记包名不读树。
- core 层各 port 的 `isAppForeground()` 语义是「Dougie 自己是否前台」，不能用于验证目标应用。
- `AppIntentAllowlist.validate` 对 `package:x` URI 归一化为 `package:$pkg`，期望包名可从显式 `package` 参数或 canonical URI 提取；http/geo 无包名。
- `IdempotencyStore` 键为 `taskId + toolCallId`；重试必须换新 toolCallId 才不会命中旧缓存。
- spec 硬约束：`:core:*` 禁止 `android.*`；TapSwipeTool/无障碍类型只走 sideload；Play `ChannelTools` 不得导入 sideload 类型（`checkChannelLeak` 把关）；新用户文案必须加到 `UserFacingErrors` 并有测试断言；工具 JSON 保持小契约。
- 14 条冻结工具契约（`core/llm` 的 `LocalToolContractEval`，`EXPECTED_N=14`）不许改动。

## Requirements

- R1 契约：`ToolResult` 与 `ToolTraceEntry` 增加 `verified: Boolean?`（true=效果已确认 / false=已检查且未生效 / null=未验证）；工具成功 JSON 仅在非 null 时注入 `"verified"` 键，无探针时字节与现状一致。
- R2 观察原语：`core/tool` 新增 `ForegroundProbePort`（`currentForegroundPackage(): String?`）与 `ForegroundVerifier`（立即查一次，未命中轮询至超时，默认 5s/250ms，纯 JVM 可测）。
- R3 app_intent 验证：launchView ok 后验证期望包名到前台；通过或 unknown 才写 IdempotencyStore；`verified=false` 返回非致命结果 `{"ok":false,"error":VERIFY_FAILED,"verified":false}`。
- R4 循环 FAIL 分支（LLM 路径）：`verified=false` 作为观察回灌模型（现有 trace→prompt 通道），模型可重试/换路/如实作答；同任务 trace 中 `verified==false` 计数达 2 次后任务 FAILED 且 `lastError=VERIFY_FAILED`，不再进下一轮 THINKING。
- R5 捷径 FAIL 分支（无 LLM 路径）：意图捷径「打开X」拿到 `verified=false` 后用新 toolCallId 确定性重试一次（不重复确认门），仍失败则 FAILED(VERIFY_FAILED)，全程零模型调用。
- R6 接线：app main 经 `ForegroundProbeBridge` 注入探针；sideload `ChannelTools` 用既有 `AndroidGesturePort` 填充 bridge；Play 零改动（探针恒 null → 全部 verified=null）。
- R7 错误文案：新增 `UserFacingErrors.VERIFY_FAILED`，测试断言常量本身。
- R8 不新增 AgentTool、不改 PolicyEngine、不动 14 条冻结契约、不碰 Compose UI、不改 maxLoops/confirm 语义。

## Acceptance Criteria

- [x] AC1 JVM 测试：launch 后探针在超时内报目标包名 → `result.verified==true` 且成功 JSON 含 `"verified":true`。
- [x] AC2 JVM 测试：探针持续不报 → `verified=false` 非致命返回且不写 IdempotencyStore；同任务第二次 verify 失败后任务 FAILED、`lastError==UserFacingErrors.VERIFY_FAILED`。
- [x] AC3 JVM 测试：无探针（Play 场景）或无期望包名（http/geo URI）→ `verified==null`，成功 JSON 与改动前字节一致，`LocalToolContractEvalTest` 不改动且全绿。
- [x] AC4 JVM 测试：捷径「打开X」首次验证失败 → 恰好重试一次（新 toolCallId，`FakeAppIntentPort.launchCount==2`），仍失败 → FAILED(VERIFY_FAILED)，`FakeLlmProvider` 零调用。
- [x] AC5 JVM 测试：LLM 路径首次 verified=false 时模型收到含 `"verified":false` 的 resultJson 并进入下一轮（第二轮给终答即完成）。
- [x] AC6 重试使用的新 toolCallId 不命中幂等缓存（第二次真实执行 `launchCount` 递增）。
- [x] AC7 全量 `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak` 通过。

## Out of scope

- tap_swipe 的点击后验证（需截屏差分或无障碍树，涉及授权耦合，另立任务）。
- 无障碍树状态提取器（AccessibilityTreePort，隐私对齐「数据不出境」承诺，另立任务）。
- sms_compose 等其余工具的验证契约。
- 循环层通用 replan 策略升级（本期只做 verify 失败的最小重试/回灌）。
