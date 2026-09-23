# 封卷 V2.1

## Goal

让以后排期的人一眼看出 V2.1.11 执行基线已随 v0.1.5 关闭，下一章在父任务 `09-23-v3-daily-assistant`，而不是继续把 Phase 5 未做项当成当前迭代。

## User value

排期不再把已经上线的语音、意图、端侧对话、向量记忆重排一遍，也不会把未闭合的测量门写成已完成。

## Requirements

- R1 改根 `PRD.md` 文头状态：V2.1.11 是已关闭的执行基线，关闭版本为 v0.1.5（2026-09-21 GitHub Release）。不提升功能版本号。
- R2 在演进记录后加一小节「V3」：只写下一章目录是 `.trellis/tasks/09-23-v3-daily-assistant/prd.md`，以及本阶段做常驻规则、端侧工具契约、晨间简报。不把 V3 需求正文复制进根 PRD。
- R3 §15 / §16.2 保留现有「已交付 / 未做」句子。追加一句：规则 D 500 条、Kokoro、自动读通知、完整多模态、商店上架留在卷外，不是 V3 迭代。
- R4 不勾选 §16.3 里仍然空着的 DoD。不改 `source/` 历史 PRD。不改 README 的能力说明，版本号仍是 0.1.5。

## Acceptance Criteria

- [x] AC1 根 `PRD.md` 文头状态含「已关闭」和 v0.1.5，且不再把该文件称为仍在执行的基线。
- [x] AC2 文内有指向 `09-23-v3-daily-assistant/prd.md` 的路径，并列出上述三件当前事项。
- [x] AC3 规则 D、Kokoro 未过门、读通知、端侧视觉、商店上架仍是未完成，没有被改成已交付。
- [ ] AC4 `git diff` 只有 `PRD.md`（以及本任务自己的 Trellis 文件，若同批提交）。常驻规则与封卷在同一工作区，提交时再按文件分开。

## Out of scope

- Kotlin、Gradle、README 能力表、`source/`。
