# ASR 规则 D 评测定标

## Goal

做出可重复的规则 D **runner**：读 gitignored 评测清单、算 mean CER 与端到端成功率、对照 5% / 95% / ≥500 条。本刀**不收录音、不宣称达标**。用户以后把 wav + 参考文本放进 `eval/asr/` 再跑。

## User value

没有高质量 wav。先把口径和跳过规则钉死，补录音后不用再猜怎么算「达标」。

## Background

- PRD §6.8：≥500 条普通话、CER ≤ 5%、含 VAD 成功率 ≥ 95%。
- JVM 已有 `CharacterErrorRate` 与 5 条文本金标。`FullEvalSet` 只看是否存在任意 wav。Spec：**这条路径不得调 sherpa/ORT**；fixture `passed` ≠ 规则 D 完成。
- Chat 按住路径无 silero VAD。权重与 wav 不进 git。
- 父任务 `09-14-phase-5-calibration`。下一刀 `intent-rule-e` 等本任务归档。

## Decisions

- 只做 runner + 清单格式。录音靠用户后续提供。
- 不把 TTS 合成或公开语料冒充自建集。
- 本刀不切 SenseVoice、不改设置页「已达标」文案（本来也没写 CER）。
- 本刀不在 JVM 评测路径调 sherpa。转写（wav→hypothesis）等有文件后再接设备/外部工具。
- 无 VAD 字段时报告 `vadApplied=false`，**不能**判规则 D 通过（PRD 要求含 VAD）。

## Requirements

- R1 清单：每条 `id`、参考文本、可选 `wav` 相对路径、可选 `hypothesis`、可选 `vadOk`。目录 `eval/asr/` gitignore。
- R2 `AsrEvalReport`：n、meanCer、successRate、vadApplied、ruleDPassed（n≥500 ∧ CER≤0.05 ∧ 成功率≥0.95 ∧ vadApplied）。
- R3 n&lt;500 或缺目录：CI skip / 报告未达标，测试不红。
- R4 仓库内 example 清单只有少量条目；五百条 wav 永不入库。
- R5 不 log 参考/转写进 App Logcat。

## Out of scope

- 采集 UI、用户上传评测音频到服务器。
- SenseVoice catalog、Kokoro、规则 E。
- 本刀接入 silero 包或设备批量转写。

## Acceptance Criteria

- [x] AC1 缺 `eval/asr` 或不足 500 条：现有 `:core:tool:test` 仍过；`ruleDPassed=false`。
- [x] AC2 合成 ≥500 条带 hypothesis + vadOk 的清单：数字正确且 `ruleDPassed` 随阈值翻转。
- [x] AC3 有 hypothesis 无 VAD：不算通过。
- [x] AC4 git 无 wav；产品文案不出现「CER≤5% 已达标」。
