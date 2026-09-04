# Implement: 意图包 int8

## Checklist

1. `train_export.py`：fp32 导出后动态量化；打印体积与 held-out 准确率；失败则 exit ≠ 0。README 改为 v2 + `onnxruntime` 依赖。
2. 本机导出到 `/tmp/dougie-intent-pack`（`HF_HOME` 可写）。确认 `model.onnx` ≤20MB 且 held-out ≥90%。
3. `gh release create intent-minirbt-v2` 上传四件套。
4. 更新 `OfficialModelCatalog` URL/SHA/`sizeLabel`；单测 v1→v2、去掉「约 47MB」。spec 里 ~47MB fp32 改为 int8 实装体积。
5. `./gradlew :core:tool:test :app:checkChannelLeak`
6. 真机：设置下载意图行「测试」+ 一条短路径。

## Validation

```bash
HF_HOME=/tmp/huggingface-hub python3 core/tool/scripts/intent/train_export.py --out /tmp/dougie-intent-pack
shasum -a 256 /tmp/dougie-intent-pack/*
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :app:checkChannelLeak
```

## Risky

- 量化后 JNI `CreateSession` 失败。
- 误把正式 ONNX 提交进 git。
- catalog SHA 与 Release 文件不一致导致永远下不完。
