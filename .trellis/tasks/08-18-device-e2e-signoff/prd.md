# 真机 §16.1 十四 Case 签字

## Goal

在至少一台真机上按根 `PRD.md` §16.1 执行 14 个 E2E Case，产出可复做的协议与带证据的签字记录。把「代码已覆盖」变成设备结论。本任务不是新功能切片。

## Background

父任务 `08-17-dougie-android-mvp` 子任务 24/24 已归档；交叉项只剩真机 14/14 签字。归档审查 `08-17-mvp-integration-review`（2026-08-18）仅做工程对照。

生产路径：`LoopEngine` 默认 LLM 超时 60s、Tool 超时 15s，无强制超时开关。进程死亡后 `DougieApplication` 调用 `recoverInterrupted` 再 `taskManager.seed`；Chat 对 `FAILED` 给自然语言错误与**重试**（新 `taskId`），不续传 LLM 流。设置里出境开关改完须**保存配置**。play 包 `com.dougie.app`，sideload 为 `.sideload` 后缀，可同机共存。

## Requirements

- **R1** 在本任务目录写 `protocol.md`：Case 01–14 各含前置（渠道、出境开关是否已保存、权限、是否要真实 API Key）、步骤、可观察通过标准。
- **R2** 执行人在 Android 10+ 真机（优先 13–16）按协议跑；记录机型与 API。对话类 Case 默认 play debug；Case 12 必须构建并安装 play 与 sideload。
- **R3** 写 `findings.md`：14 行均为通过 / 失败 / 受阻，禁止空白「未跑」。通过附日期、机型/API、渠道、现象；失败附复现；受阻写清条件（无日历、拒截屏、无法诱导超时等）及仓库里已有的单测证据。
- **R4** Case 06：用断网/无效上游或等待至约 60s 观察 `LLM_TIMEOUT` 与重试。Case 07：若真机无法让 Tool 超过 15s，标受阻并指向 `LoopEngineTest` 的 tool timeout，不为本 Case 加调试开关。
- **R5** Case 12：同机两包可同时打开；play 产物不得含 sideload-only Accessibility / `TapSwipeTool`。先用现有 `checkChannelLeak` 与 APK/源集核对，不够再补检查步骤，不先写新扫描器。
- **R6** 不为签字新写 Espresso/UIAutomator。Phase 5 全量音频、真机第三方 UI 模板、OpenCV、tokenizer 不作为通过条件。
- **R7（方案 A）** 本任务验收 = 协议 + 14 行记录。现场能小修的缺陷可顺手修；修不完记缺口并另开子任务。父任务最后一格仅当 14 条均为**通过**时才勾。

## Technical Notes

交付物只放本任务目录：`protocol.md`、`findings.md`。小修走常规模块与既有 spec，不把签字任务变成功能父任务。

## Acceptance Criteria

- [ ] `protocol.md` 覆盖 Case 01–14，另一人可按文档复做。
- [ ] `findings.md` 14 行均有结论与证据字段。
- [ ] Case 12 有双包构建/安装/共存与 play 不含侧载 Accessibility 的核对记录。
- [ ] 14/14 通过 → 更新父任务交叉项为已勾；否则交叉项保持打开并列出非通过 Case。

## Out of Scope

- 新功能（含强制超时调试开关、tokenizer、OpenCV AAR、Q4/Q8 再改）。
- 全量 ASR 评测、向量检索、桌面、归档整个 MVP 父任务。
- 在本任务内修到 14/14 全绿（失败另开子任务）。

## Key Decisions

- **方案 A**：完成标准是签字包，不是本任务内修绿全部 Case。
- 对话默认 play；双渠道仅 Case 12 强制双包。
- 无法诱导的超时允许「受阻 + 单测证据」，不升格为产品缺口 unless 正常路径也坏。

## Risks / Deferred

- 真机依赖真实 LLM Key、日历账号、MediaProjection 授权；缺条件用「受阻」而非假装通过。
- OEM 杀后台可能改变 Case 08/14 现象，须按实记录。
