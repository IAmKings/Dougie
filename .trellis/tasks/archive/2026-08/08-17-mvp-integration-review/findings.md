# Findings

**Pass 2 · 2026-08-18.** Verdict: all 14 §16.1 Cases have **code coverage**. MVP **engineering** baseline for Phases 0–4 and §3.1 UI/tools is met. **Not** claiming 14/14 device E2E sign-off.

See canvas `mvp-integration-review.canvas.tsx`.

## §16.1 Case map (pass 2)

| Case | Scene | Verdict | Evidence |
|------|--------|---------|----------|
| 01 | 普通聊天 → Final | 代码已覆盖 | `LoopEngine` + `OpenAICompatibleProvider` SSE；App 不静默 Fake |
| 02 | 电量 | 代码已覆盖 | `DeviceBatteryTool`；LoopEngine 单测 |
| 03 | 查询日历 | 代码已覆盖 | `CalendarQueryTool` + Policy |
| 04 | 创建日历 + 确认 | 代码已覆盖 | L2 + Chat `ConfirmCard` |
| 05 | 拒绝权限不执行 | 代码已覆盖 | `PolicyEngine`；权限中心 |
| 06 | LLM Timeout | 代码已覆盖 | `llmTimeoutMs` → `LLM_TIMEOUT`；Chat 重试 |
| 07 | Tool Timeout | 代码已覆盖 | `toolTimeoutMs` 单测 |
| 08 | Process death 恢复 | 代码已覆盖 | `recoverInterrupted` FAILED，不续传 LLM |
| 09 | 幂等不重复创建 | 代码已覆盖 | `taskId+toolCallId` 落盘 |
| 10 | 记忆 + 来源 | 代码已覆盖 | `retrievedMemories` → Final `来源：`（本轮已补） |
| 11 | 截图 + 匹配 | 代码已覆盖 | MediaProjection + NCC + `solid`/`logo` catalog（本轮已补 stub） |
| 12 | Play/Sideload | 代码已覆盖 | `checkChannelLeak` |
| 13 | 出境拦截 | 代码已覆盖 | `allowCloud` 默认 false |
| 14 | 杀进程提示 + 幂等 | 代码已覆盖 | seed 中断 + 新 taskId 重试 |

## P1 remaining

None.

## Closed this cycle (vs pass 1)

- Token budget / `max_tokens` on OpenAI-compatible JSON.
- Chat memory source citation (Case 10).
- ScreenMatch `TemplateLibrary` catalog `solid`+`logo` + downscale (Case 11).

## Explicitly non-blocking

- PRD §8.3 local 8K tokenizer / prefer-truncate Tool Result.
- Third-party real UI template packs / OpenCV AAR.
- Phase 5 rule D full audio set, VAD 95%, vector search, desktop, `:cli` mosaic.

## Parent cross-child

Blocking children archived. Last parent box stays open: no device-signed 14/14 E2E.
