# Phase 0 skeleton Chat Fake Loop

## Goal

在空仓库中建立可编译的 Android Gradle 骨架，跑通 Fake LLM + Fake Tool + Loop 状态机，并在 Chat 主界面按 `design/语音与对话` 展示完整执行链。

用户价值：开发者能在真机/模拟器上发出一条消息，看见 Agent 三次 Tool 循环后给出最终答案，而不是只有「正在思考」。

## Background

- 产品主干：`PRD.md` §5.3 状态机、§5.4 Loop、§11.1/§11.5 Chat 组件、§15 Phase 0、§17.2 模块红线。
- 视觉参考：`design/语音与对话/Waku_对话_-_AI_交互_中文版__040b9b0466794abd8dc1d514b1733729.html`；空态参考同目录「空态引导_-_首次启动对话」。
- 仓库无 `.kt` / Gradle 工程。品牌图标：根目录 `Dougie-logo.svg`。

## Confirmed facts

- Phase 0 完成标准：Fake Provider 完整执行 **3 次 Tool Loop**（`PRD.md` §15）。
- 5 天内跑不通则禁止进入 Phase 1。
- `:core:*` 必须 JVM 纯净；Chat 主题用 Compose 自绘，不得引入 mosaic。
- Stitch Chat 主色为 `#3d5198` / `#566ab2`；`PRD.md` §11.4 写的 teal `#006A6A` 与设计稿冲突。本任务 **以 Stitch Chat 稿为准**。

## Requirements

- **R1** 多模块 Gradle（Version Catalog）：至少 `:app`、`:core:model`、`:core:llm`、`:core:tool`、`:core:runtime`、`:feature:chat`。
- **R2** `AgentTask` + Loop Engine 实现 `PRD.md` §5.3 状态：至少 `IDLE → PREPARING → THINKING → TOOL_PENDING → TOOL_EXECUTING → TOOL_RESULT → THINKING`（×3）→ `COMPLETED`。
- **R3** Fake LLM 按 loop 次数返回 ToolCall，第 3 次 Tool 结果后返回 FinalAnswer；Fake Tool 返回稳定 JSON，幂等键形状为 `taskId + toolCallId`。
- **R4** JVM 单元测试：同一输入连续跑 3 次任务，每次恰好 3 次 Tool 循环后 COMPLETED。
- **R5** Chat UI：AppBar（Dougie 标题 + 本地出境提示）、用户气泡、思考状态（含 loop 序号）、Tool 卡片、Final Answer、底部输入框；空态有示例指令。
- **R6** UI 只收集 `StateFlow`，Loop 不在主线程跑。
- **R7** 发送示例「我现在手机还有多少电？」可走完演示链路（Fake 电量结果即可）。

## Acceptance Criteria

- [ ] `./gradlew :core:runtime:test`（或等价 JVM test）通过：Fake 任务 3 次 Tool Loop 后 COMPLETED。
- [ ] `./gradlew :app:assembleDebug` 成功。
- [ ] Chat 一次发送后可见：User → Thinking（含 loop）→ Tool 卡片 → 再次 Thinking → … → Final，禁止只显示「正在思考」。
- [ ] `:core:runtime` / `:core:model` / `:core:llm` / `:core:tool` 的 Gradle 依赖不含 Android SDK。
- [ ] 应用图标或 AppBar 使用 `Dougie-logo.svg`（或由其导出的矢量），文案用 Dougie 而非 Waku。

## Out of scope

- 真实 Cloud Provider、Room 持久化、进程死亡恢复、Permission Center / Memory / Settings 完整页。
- ASR/TTS、屏幕感知、Sideload Accessibility、`:cli` mosaic。
- 高风险 Confirm Card 真确认流（可静态占位，不接 Policy）。
- 统一 PRD teal 令牌（留待后续设计对齐任务）。

## Technical notes

- minSdk 26+ 以满足 Android 10；compile/target 以 AGP 默认当前稳定为准，写入 `libs.versions.toml`。
- Fake 实现放在 `:core:llm` / `:core:tool` 的 `fake` 源或同模块测试可见实现，由 `:app` 组装注入。
