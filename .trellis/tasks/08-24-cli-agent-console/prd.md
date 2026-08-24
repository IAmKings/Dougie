# JVM :cli mosaic Agent Console

## Goal

开发者在本机终端运行 `:cli`，用 Fake Loop 看到 `PREPARING → THINKING → TOOL_* → COMPLETED`，验证 `:core:*` 可脱离 Android 跑 Agent Runtime（`PRD.md` 决策 #14、§17.3）。不进 APK。

## Confirmed facts

- `LoopEngine` / `TaskManager` 已是 JVM；`FakeLlmProvider` 与 `FakeBatteryTool` 在 `:core:*` **main**（非仅 test）。
- `LoopEngineTest.fakeTaskCompletesAfterExactlyThreeToolLoops` 已证明三次 battery 循环。
- 仓库无 `:cli`。Kotlin JVM 插件已在根 `build.gradle.kts` `apply false`。
- 用户选定本切片做 CLI，不做 MiniRBT / 评测。

## Requirements

- **R1** Gradle `:cli`，`application` 可执行；`include(":cli")`。依赖 `:core:runtime`（及其 api 传递的 model/llm/tool/memory）。禁止 `android.*`、禁止依赖 `:app` / `:feature:*` / `:tool:system`。
- **R2** 默认模式：`FakeLlmProvider` + `FakeBatteryTool`；`Dispatchers.Default`；`InMemoryMemoryStore` 可选。提交固定或 stdin 一句后跑完 Phase 0 脚本（三次电量 Tool 再终答）。
- **R3** 有 TTY 时用 mosaic 展示当前 `TaskStatus`、`loopCount`、`toolTrace` 摘要、`finalAnswer` / `lastError`；无 TTY 或 mosaic 失败时 stdout 打印同样字段（§17.3 降级）。
- **R4** 入口用 kotlinx-cli（或等价参数：`--log-only`）。不把 API 密钥写入仓库；本切片不接真实 Cloud Provider。
- **R5** 不改 App 主题；mosaic 不得进入 Android sourceSet。

## Out of Scope

- 真实 LLM、日历 L2 确认、Room、SAF、意图/ASR/TTS JNI、桌面 GUI、向量记忆。

## Acceptance Criteria

- [ ] `./gradlew :cli:run`（或 README 写明的命令）能启动并完成一次 Fake 电量 Loop，`status=COMPLETED`，三次 `battery` SUCCESS。
- [ ] `--log-only`（或无 TTY）仍能看到状态序列，不以崩溃代替降级。
- [ ] play/sideload APK 的依赖图不含 `:cli`；`:core:*` 仍无 `android.*`。

## Key decisions

- **D1** 本切片只 Fake Loop（Phase 0 加分项落地），日常 Cloud 调试留后续。
- **D2** 工具集只注册 `battery`（与 FakeLlm 脚本一致）；可顺带展示 `SystemTimeTool` 但不强迫 Fake 去调。
