# Phase 1a Cloud LLM Battery Tool

## Goal

在 Phase 0 骨架上接上 **OpenAI-compatible 非流式 Provider**、**Egress 默认拦截**、以及 **真实电量 Tool**，让用户完成一次真实对话并读到设备电量。

## Background

- 依赖：`08-17-phase-0-skeleton-chat` 已通过 Fake 3-loop 生死线。
- 产品：`PRD.md` §15 Phase 1a、§8.4–§8.5、§9.2 API Key、§6 电量 Tool、UF-01。
- 视觉：`design/权限与系统/Provider_设置_-_模型与密钥_中文版__*.html`；Chat 仍用 Stitch 蓝。

## Requirements

- **R1** `EgressPolicy.allowCloud` 默认 `false`。未授权时云端调用失败，Chat 显示自然语言（出境被拦截），不得静默改走 Fake。
- **R2** 用户可在 Provider 设置中：勾选允许云端（展示出境说明）、填写 base URL / API Key / model。Key 用 Android Keystore 封装存储（EncryptedSharedPreferences），禁止明文 SharedPreferences / Logcat。
- **R3** `OpenAICompatibleProvider`：非流式 `chat/completions`，支持 tool calling；经 `EgressGateway` 调用，Provider 不自开「绕过策略」的入口。
- **R4** 真实 `battery` Tool：读 `BatteryManager`（或粘性 `ACTION_BATTERY_CHANGED`），返回 `battery_percent` + `charging` JSON；幂等键仍为 `taskId+toolCallId`。`:feature:*` 不得直连电池 API。
- **R5** Loop 超时：LLM 与单次 Tool 有超时（对齐 PRD Tool 15s；LLM 可用 60s）。失败进入 `FAILED` 并给用户可读错误。
- **R6** Debug 构建可继续用 Fake 跑 JVM 测试；正式对话路径由设置决定。AppBar 能进入设置。
- **R7** 首次需要出境时必须先看到知情文案（设置页或首次弹窗），与 UF-01 一致。

## Acceptance Criteria

- [ ] `allowCloud=false` 时，配置了 Key 也不能发出 HTTP；Chat 出现出境拦截说明。
- [ ] `allowCloud=true` + 有效 Key 时，用户问电量：真实 Tool 结果进入上下文，Final Answer 反映设备电量（允许与 63 的 Fake 值不同）。
- [ ] MockWebServer（或等价）JVM 测试：Provider 解析一次 tool_call + 一次 final content。
- [ ] Gateway 单元测试：deny 时不调用 Provider。
- [ ] `:core:*` 仍无 Android plugin；电池实现只在 Android 模块。
- [ ] `./gradlew :core:runtime:test :core:llm:test :app:assembleDebug` 通过。

## Out of scope

- Streaming、多 Tool（日历等）、Room 任务恢复、Memory FTS、ASR/TTS、Play/Sideload flavor。
- 高风险 Confirm Card 真链路（电量为 L0）。
- 端侧 LLM。

## Constraints

- Release 不打印 Prompt / Key / 原始 LLM body。
- INTERNET 权限仅用于已授权的 Provider。
