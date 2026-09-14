# Implement: Kokoro 规则 B runner

## Order

1. `KokoroEvalItem` / `KokoroEvalReport` / `loadJsonl` + `report` + `rtf`。
2. JVM：不足 5 条 → `ruleBPassed=false`；5 条假数据过/不过 P95 RTF；`numThreads!=1` 或自然度缺/false 不通过。
3. testdata `core/tool/src/test/resources/eval/kokoro-rtf-sample.jsonl`（几行）。仓库根 `eval/` 仍 gitignore。
4. `./gradlew :core:tool:test`

## Do not

- 提交 Kokoro/VITS onnx。
- JVM 评测调 sherpa。
- 改 catalog、`SherpaJni` 线程、设置文案、规则 D/E。
- `task.py start` 前未获规划批准。
