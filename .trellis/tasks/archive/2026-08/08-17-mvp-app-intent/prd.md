# MVP App Intent Tool

## Goal

补齐 MVP Tool 清单中的 **App Intent**：在用户确认后，安全打开允许的 URI 或启动已安装应用。禁止短信/电话/任意隐式 Intent 轰炸。

## Background

- 依赖：已归档 Phase 4（确认卡、幂等 store、前台检测）。
- 产品：`PRD.md` §3.1 Tools #6；§3.2 不做短信/电话自动化。
- 打开外部应用属于副作用 → **L2 Confirm Card**。

## Requirements

- **R1** Tool 名 `app_intent`。参数：`uri`（必填）和可选 `package`。Descriptor 进 OpenAI tools 列表。
- **R2** 仅允许 scheme：`https`、`http`、`geo`、`package`（`package:com.example.app` 启动 MAIN/LAUNCHER）。拒绝 `file`、`javascript`、`content`、`tel`、`sms`、`mailto`、`intent` 及其他。
- **R3** 仅前台可执行（同剪贴板语义）。后台 → fatal 用户文案，不 launch。
- **R4** L2 确认；拒绝则不 launch。`IdempotencyStore`：同一 `taskId+toolCallId` 不二次 launch。
- **R5** Android 用 `Intent.ACTION_VIEW` / launcher intent；`FLAG_ACTIVITY_NEW_TASK`。解析失败 → 自然语言失败，不崩溃。
- **R6** `:feature:*` 不 `startActivity`。JVM Fake port 计数 launch 次数。

## Acceptance Criteria

- [ ] JVM：https URI confirm 后 launch 一次；reject 为 0。
- [ ] JVM：`tel:` / `file:` sanitize 或 execute 拒绝，不调用 port。
- [ ] JVM：同一 idempotency key 第二次不 launch。
- [ ] JVM：非前台 fatal，不 launch。
- [ ] OpenAI tools 含 `app_intent`。
- [ ] 现有 Fake 3-loop / 日历幂等测试仍绿。
- [ ] `./gradlew :core:runtime:test :core:tool:test :core:llm:test :app:assembleDebug` 通过。

## Out of scope

- Accessibility / TapSwipe
- Play/Sideload flavors
- 离线 ASR/TTS
- 任意 `Intent` extras / broadcast

## Constraints

- `:core:*` JVM-only。
- 不 log 完整 URI query（可 log scheme + host only if logging at all — prefer no URI logs）。
