# Implement: 发布语义记忆句向量包

## Order

1. `EmbedModelLayout`：`isPresent` = `model.onnx` + `vocab.txt` 非空。catalog `embed(model, vocab)` 两文件；`standard()` + `AppOfflineModels` / BuildConfig 字段。测试 size=7、embed `isConfigured`、installed 两文件。
2. JNI：`nativeEmbedTokens(path, inputIds, attentionMask) -> float[]`，**独立 session**。CMake 仍链现有 `dougie_intent`。
3. `AndroidEmbeddingPort`：`BertWordPiece` `max_len=64`；问句加 BGE 前缀；CLS/向量 + L2；未就绪 false。App 替换 `HashBagEmbeddingPort`。probe 用该端口。
4. 下载 Xenova int8 + vocab，算 SHA，写入 `DEFAULT_EMBED_*`（与 catalog 同一提交）。
5. JVM：Fake 同义句仍在 `HybridMemoryStoreTest`；`OfficialModelCatalogTest` 钉 URL/SHA；可选小 ONNX 夹具不入库大权重。
6. `./gradlew :core:memory:test :core:tool:test :app:checkChannelLeak`。真机下包后跑 AC2/AC3。

## Validation

```bash
./gradlew :core:memory:test :core:tool:test :app:checkChannelLeak
```

JDK 17。真机 PJZ110：记忆开，存「我喜欢喝美式」，下载语义记忆，问「我平时喝什么咖啡」应有来源。

## Risk / rollback

- 与 intent 共用一个 ORT session → 意图推理被换图。必须双 session。
- HF 文件变更 → hash mismatch，用户下不全；SHA 与 URL 一次提交。
- 卸包 = FTS。不 drop DB。

## Do not

- 用 `models/intent/model.onnx`。
- 把 ONNX 打进 APK / git（除既有 tiny testdata 规则）。
- `task.py start` 前未获规划批准。
- search 里 await backfill。
