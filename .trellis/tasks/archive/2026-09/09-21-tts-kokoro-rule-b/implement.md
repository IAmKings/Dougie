# Implement: Kokoro 规则 B 真机采集

## Order

1. `KokoroEvalLayout` + 评测 `ModelPack`（https/sha256 钉死；`relativeDir` ≠ `models/tts`）。`OfficialModelCatalogTest` 仍断言 standard 无 Kokoro。
2. 金标 main 资源 ≥5 条中文。`KokoroEval.loadGold` / `runForward` / `writeJsonl` / `markNaturalnessOk` + Fake 单测（AC1、AC4）。
3. `SherpaJni`：Kokoro `numThreads=1` 的 `generate`；**不**改 VITS `ttsEngine` / `threadCount()`。用完 release。
4. `:app`：缺包下载、合成写 `File(filesDir, KokoroEval.MANIFEST_RELATIVE)`、`last`、自然度批注、adb 提示。
5. Debug：注入回调；「评测 Kokoro 规则 B」+ 进度；「本批自然度通过」；文案无「已达标」/金标原文。
6. `./gradlew :core:tool:test :core:model:test :feature:debug:testDebugUnitTest :feature:settings:testDebugUnitTest :app:checkChannelLeak`

## Do not

- Settings / catalog 上架 Kokoro、改默认 TTS、VITS 线程改成 1。
- 合成计时路径播放 PCM；自动 `naturalnessOk=true`。
- 提交 onnx / `eval/tts/kokoro-rtf.jsonl`；升版本；androidTest。
- `task.py start` 前未获规划批准。
