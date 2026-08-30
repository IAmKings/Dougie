# Design: Chat 意图短路径

## Boundaries

| Piece | Module |
|-------|--------|
| Classify-then-maybe-skip-LLM | `:core:runtime` `LoopEngine` |
| `IntentPort` / ONNX / `IntentClassifierTool` | unchanged `:core:tool` + `:tool:system` |
| Wire port | `:app` `DougieApplication` |
| CLI / JVM tests without port | `intentPort = null` → 今日 Loop |

`:feature:chat` 不改发送 API。`TaskManager.submit` 仍只 `loopEngine.run`。

## Data flow

```text
submit(trimmed, speakReply, attachments)
  → LoopEngine.run
      PREPARING → (memory retrieve 照旧)
      若 intentPort 非空且 isModelPresent 且 isEngineReady
         且 attachments 与 attachedCaptureId 皆空
         → classify(input)  [catch → 当未命中]
         → 仅 intent ∈ {query_time, query_battery} 且 confidence >= MIN_CONFIDENCE
            → 与现有 TOOL_* 同一套：sanitize "{}"、validate、PolicyEngine、execute、AuditLog
            → 模板 finalAnswer → COMPLETED → ingestMemory
            → return（不进 while / collectLlmTurn）
      否则 while LLM loop（与今相同；不把 IntentHit 写入 LoopContext）
```

## Contracts

- 短路径工具名：`query_time` → `time`，`query_battery` → `battery`。参数固定 `{}`。
- `finalAnswer`（中文，确定性）：
  - time：从 `iso_local` + `zone` 拼「现在是 …，时区 …。」解析失败则 `TOOL_FAILED`。
  - battery：`battery_percent` + `charging` →「当前电量 N%。」+「正在充电。」/「未在充电。」
- 不把 `intent_classifier` 写入 `toolTrace`。短路径 trace 只有 `time` 或 `battery`。
- `IntentClassifierTool` 成功 JSON 形状禁止改。
- 分类异常 / 低置信 / 其它标签：零日志中的 utterance/intent，直接 LLM。

## Compatibility

- 无迁移。`AgentTask` 不加字段。
- Play：意图包仍按需下载；leak 测试不变。
- 自动播报：短路径也会 `COMPLETED`+`finalAnswer`；`speakReply` 仍由 Chat 层决定。

## Trade-offs

- **Port 注入 Loop 而非 ChatViewModel**：缺包不挡发送；测试可 Fake；CLI 可不接。
- **MVP 只两条 L0**：日历等缺槽位，短路径会瞎执行；注入 Prompt 会让意图出设备。
- **有附件不短路径**：截屏+短句需要视觉 Loop。

## Rollback

`intentPort` 保持 null 或不注入 App：行为回退为仅云端 Loop。
