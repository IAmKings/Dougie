# Implement: ASR 规则 D runner

## Order

1. `AsrEvalItem` / `AsrEvalReport` / `AsrEval.loadJsonl` + `report`。
2. JVM：不足 500 → `ruleDPassed=false`；500 条假数据过/不过阈值；无 vadOk 不通过。
3. `FullEvalSet` 注释 + 可选 jsonl 路径 `eval/asr/manifest.jsonl`。缺目录仍 skip。
4. 提交 `core/tool/src/test/resources/eval/asr-manifest-sample.jsonl`（几行）。仓库根 `eval/` 仍 gitignore。
5. `./gradlew :core:tool:test`

## Do not

- 提交 wav / onnx。
- JVM 评测调 sherpa。
- 改设置文案、SenseVoice、规则 E。
- `task.py start` 前未获规划批准。
