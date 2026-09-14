# Kokoro RTF 门槛评估

## Goal

给规则 B 一个可重复、CI 不误报的 JVM 评测契约：目标机 **单线程** Kokoro RTF ≤ 1.0 且每条测量有自然度评审。缺测量时跳过、不算达标。不达标则维持 VITS；本刀不上架 Kokoro、不替换主路径、不内置。

## User value

不定标前不把 ~310MB Kokoro 放进设置下载，也不宣称「自然度优先 TTS 已启用」。

## Depends on

`09-14-intent-rule-e` 已归档。不改 ASR / 意图评测。父任务：`09-14-phase-5-calibration`（D、E 已归档，本刀为 Phase 5 定标最后一刀）。

## Background

- PRD §6.9 规则 B：仅当目标机型实机单线程 RTF ≤ 1.0 **且**音色自然度评审通过才启用；默认不内置；不达标维持 VITS。规则 A：Kokoro 不计入 sideload ≤400MB。规则 C 不改。
- 现网 TTS：catalog 仅 `tts` = `vits-zh-hf-fanchen-C`（`TtsModelLayout`：`model.onnx` + `tokens.txt` + `lexicon.txt`）。`SherpaJni.ttsEngine` 只填 `OfflineTtsVitsModelConfig`，`numThreads = availableProcessors.coerceIn(2, 4)`（**不是**单线程）。
- sherpa 绑定里有空的 `OfflineTtsKokoroModelConfig`，产品路径未接线。无 Kokoro catalog 行，设备默认无包。仓库无 androidTest。`:core:*` 禁止调 sherpa。`/eval/` 已 gitignore。
- 公开参考 RTF 2.7–6.6（RPi4）对移动端不友好；本 runner 不把桌面/树莓派数字当成规则 B。

## Requirements

- R1 `:core:tool` 增加与 `AsrEval` / `IntentEval.ruleEReport` 同构的 JSONL 报告。一行：`id`、`text`，可选 `synthMs`、`audioDurationMs`、`numThreads`、`naturalnessOk`。缺 `synthMs` 或 `audioDurationMs`（≤0）→ unscored。`rtf = synthMs / audioDurationMs`。`toString()` 只打 counts/rates，不打 utterance / PCM。
- R2 `ruleBPassed` = nLabeled≥5 ∧ nScored≥5 ∧ p95Rtf≤1.0 ∧ threadsApplied ∧ naturalnessApplied。scored = 两条时长都有。`threadsApplied` = 每条 scored 的 `numThreads==1`。`naturalnessApplied` = 每条 scored 都有非空 `naturalnessOk` **且均为 true**。P95：nearest-rank `sorted[ceil(0.95 * n) - 1]`（与规则 E 相同）。
- R3 缺 `eval/tts/kokoro-rtf.jsonl` 时 `:core:tool:test` 仍过。合成 ≥5 条可翻转 RTF / 线程 / 自然度。testdata 仅几行 sample。
- R4 不改 `OfficialModelCatalog`、不改 `SherpaJni` VITS 接线与线程数、不改 `speech_output` / 规则 C、不把 onnx 进 git、设置页不出现「Kokoro 已达标」。

## Out of scope

- PJZ110 实跑 Kokoro、下载 310MB 包、androidTest、设置页采集 UI。
- 上架 Kokoro catalog 行、替换 VITS 主路径、sideload 内置 Kokoro。
- 把 Python/桌面/多线程 RTF 当作规则 B。
- 改规则 D / E、SenseVoice、松手自动 submit。

## Technical notes

- 布局：gitignore `eval/tts/kokoro-rtf.jsonl`。不把路径并进 `FullEvalSet`（仍 ASR）。
- 真机采集（本刀之后）：必须 `OfflineTtsModelConfig.numThreads=1`，RTF 用合成墙钟 / 生成音频时长，自然度人工听写布尔。现网 VITS 的 2–4 线程不能复用为规则 B。

## Acceptance Criteria

- [x] AC1 缺 jsonl 或不足 5 条 scored：现有 `:core:tool:test` 仍过；`ruleBPassed=false`。
- [x] AC2 合成 ≥5 条带时长 + numThreads=1 + naturalnessOk：数字正确且 `ruleBPassed` 随 P95 RTF / 线程 / 自然度翻转。
- [x] AC3 有时长但 `numThreads!=1` 或缺线程：不算通过。
- [x] AC4 有时长但缺 `naturalnessOk` 或任一条为 false：不算通过。
- [x] AC5 git 无 Kokoro onnx；catalog 仍只有 VITS；产品文案不出现「Kokoro 已达标」。
