# Kokoro TTS 规则 B

## Goal

在目标机上用 **单线程 Kokoro** 合成至少 5 条中文，把合成墙钟与音频时长写入 `filesDir/eval/tts/kokoro-rtf.jsonl`，再用现有 `KokoroEval.report` 得到规则 B 数字。过门前产品 TTS 仍是 VITS；设置页不出现 Kokoro。

## User value

第一次能在手机上核对「这台机能不能实时 Kokoro」，而不是把 sample JSONL 当成达标。过不了就继续用 VITS。

## Background

- `PRD.md` §6.9 规则 B：实机单线程 RTF ≤ 1.0 **且**自然度评审通过才启用；默认不内置；不达标维持 VITS。规则 A：Kokoro 不计入 sideload ≤400MB。
- JVM `KokoroEval` / `KokoroEvalTest` 已归档（`09-14-kokoro-rtf`）。`ruleBPassed` = nLabeled≥5 ∧ nScored≥5 ∧ p95Rtf≤1.0 ∧ `numThreads==1` ∧ 每条 scored `naturalnessOk==true`。缺仓库根 `eval/tts/kokoro-rtf.jsonl` 时 CI 仍过。sample jsonl 不是规则 B。
- 现网 catalog `tts` 只有 `vits-zh-hf-fanchen-C`（`TtsModelLayout`：`models/tts`）。`SherpaJni.ttsEngine` 只填 VITS，`numThreads` 2–4。绑定里已有空的 `OfflineTtsKokoroModelConfig`。
- 规则 E 真机采集已有：`:app` 写 `filesDir` jsonl，Debug「评测意图规则 E」，adb `run-as` 读 last。`:feature:debug` 不依赖 `:core:tool` / JNI。
- 公开 RPi RTF 2.7–6.6 不能当规则 B。`KokoroEvalReport.toString()` 只打 counts/rates。产品文案禁止「Kokoro 已达标」/「已达标」。

## Requirements

- R1 金标：`:core:tool` **main** 资源放入 ≥5 条无 PII 中文（`id`/`text`）。`KokoroEval.loadGold` / `runForward(synth)` / `writeJsonl` 与规则 E 同构。Fake synth 可测；`:core:tool` 不调 sherpa/ORT、不读 PCM。
- R2 评测包：独立 `ModelPack`，目录 **不是** `models/tts`（不得覆盖 VITS）。不加入 `OfficialModelCatalog.standard()`。开发者页点评测时缺包则 `ModelInstaller` 下载（按钮即确认）。针 https + sha256；优先官方 int8（若 sherpa 同 API）。进度走现有 `onProgress`。
- R3 `:app` 真机采集：缺包/引擎未就绪用中文 `UserFacingErrors`，不崩溃、不写半截 jsonl 当通过。合成 `OfflineTtsKokoroModelConfig` + **`numThreads=1`**，与产品 VITS 缓存分开。`synthMs` = `generate` 墙钟；`audioDurationMs` = PCM 时长。计时路径不播放。写出 `KokoroEval.MANIFEST_RELATIVE`。不改 `SherpaJni` VITS 接线与 `threadCount()`。
- R4 Debug：按钮「评测 Kokoro 规则 B」（busy、进度、不可重入）。完成后只展示 `report.toString()` + 相对路径 + `run-as` 提示，无 utterance / PCM /「已达标」。另有「本批自然度通过」：仅当已有 ≥5 条 scored 时可用；把 scored 行写成 `naturalnessOk=true` 再刷新报告。默认合成结果 **不** 自动 true。
- R5 设置模型列表、Chat `speech_output`、规则 C/D、版本号均不变。git 无 onnx、无 jsonl。不把 `ruleBPassed=true` 写成产品已切换 Kokoro。

## Out of scope

- Settings / `OfficialModelCatalog` 增加 Kokoro 行；把 Kokoro 设为当前 TTS。
- sideload APK 内置 Kokoro。
- 改 VITS `numThreads`、`speech_output`、规则 C / D、商店上架、androidTest 仪器框架。
- 自动把 `filesDir` jsonl 拷到仓库根 `eval/`。
- 逐条试听 UI（本刀整批勾选）。

## Technical notes

- 设备路径：`filesDir/eval/tts/kokoro-rtf.jsonl`。拉回后可放到 gitignored 仓库根再打分。
- 评测目录建议 `filesDir/eval/tts/kokoro/`。Kokoro 的 `espeak-ng-data` 若是目录树，允许评测专用解压；**不**改 catalog 用的 `ModelInstaller` 解包语义。
- 采集期间与产品 TTS 共用 JNI 锁可接受。评测引擎用完释放，避免双份 ~310MB 常驻。
- `toString()` 可含 `ruleBPassed=true`（与规则 E 相同）；UI 不得出现中文「已达标」。
- 2026-09-21 真机：单线程 int8 `p95Rtf=2.2515`，规则 B 未过门。评测入口保留；不因此拆代码，也不上架 Kokoro。

## Acceptance Criteria

- [x] AC1 Fake：≥5 条写出合法 jsonl；`numThreads=1`；无 `naturalnessOk` 时 `ruleBPassed=false`；勾选后随 P95/线程翻转。
- [x] AC2 缺评测包：Debug 显示中文未就绪或下载失败文案，不覆盖 VITS，不崩溃。
- [x] AC3 有包时：生成 ≥5 行；计时路径 `numThreads=1`；UI 无 utterance /「已达标」；adb 可 `run-as` 读 jsonl。真机 `p95Rtf=2.2515`。
- [x] AC4 「本批自然度通过」前 `naturalnessApplied=false`；之后 scored 全 true。未点则 last 保持未过自然度。
- [x] AC5 `OfficialModelCatalog.standard()` 仍无 Kokoro；设置页无 Kokoro 文案；`SherpaJni` VITS 仍 2–4 线程；git 无 onnx / `eval/tts/kokoro-rtf.jsonl`。
