# Phase 5 定标

## Goal

按 PRD §16.2 依次定标：规则 D（ASR）→ 规则 E（意图）→ 规则 B（Kokoro）。不定标前不宣称离线语音/意图「达标」，Kokoro 不达标则维持 VITS。

## Sequence

1. `09-14-asr-rule-d` — **已归档**（runner；缺录音不算达标）。
2. `09-14-intent-rule-e` — **当前**。自建集 ≥10 类准确率 ≥ 90%，P95 ≤ 500ms。
3. `09-14-kokoro-rtf` — **等 E 归档后开工**。PJZ110 单线程 RTF ≤ 1.0 且音色过审才启用；否则维持 VITS。

依赖写在子任务 `prd.md`，不靠目录顺序。

## Out of scope

桌面端、sqlite-vec、真流式 ASR 第二包、松手自动 submit。权重不进 git。
