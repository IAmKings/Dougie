# Design: 发布语义记忆句向量包

## Boundary

- `:core:memory`：不改 `HybridMemoryStore` 合同（search 不 await backfill）。JVM Fake / `HashBagEmbeddingPort` 仅测试。
- `:core:tool`：`EmbedModelLayout` 改为 `model.onnx` + `vocab.txt`；`OfficialModelCatalog.embed(model, vocab)` 两文件；`BertWordPiece` 复用。
- `:tool:system`：`EmbedOrtJni`（或同 `.so` 内第二套 session + `nativeEmbedTokens`）+ `AndroidEmbeddingPort : EmbeddingPort`。
- `:app`：`HybridMemoryStore(..., AndroidEmbeddingPort)`；probe `embed("测")` 非 null；`BuildConfig` 增加 `EMBED_MODEL_*` / `EMBED_VOCAB_*`。
- 不改 `LoopEngine` / `MemoryGate`。

## Model

| 项 | 值 |
|----|-----|
| 基座 | BAAI/bge-small-zh-v1.5，MIT，可商用 |
| ONNX | Xenova `onnx/model_int8.onnx` ≈ 24MB，SHA 实现时锁定 |
| vocab | Xenova/BAAI `vocab.txt` |
| 输出 | 若 rank-3 `[1,seq,512]` 取 CLS（index 0）再 L2；若已是 `[1,512]` 则 L2 |
| 问句 | 前缀 `为这个句子生成表示以用于检索相关文章：`；事实原文不前缀 |

安装器把 URL 文件写成 layout 名 `model.onnx`（与 ASR 把 `model.int8.onnx` URL 写成 layout 名同一纪律）。

## JNI

`intent_ort_jni.cpp` 的 `g_session` 是单例。embed 必须 `g_embed_session` / `g_embed_path`，intent 路径不变。同链 `libonnxruntime.so` + `dougie_intent`。`CancellationException` 上抛；其它失败 → `embed()=null`。

## Catalog

`embed()` 默认填 `DEFAULT_EMBED_MODEL` / `DEFAULT_EMBED_VOCAB`（HF HTTPS + SHA）。`sizeLabel`「约 24MB」。`isInstalled` → `EmbedModelLayout.isPresent`。空 `BuildConfig` 走 DEFAULT（与 ASR 相同）。

## Logging

禁止 content / query / 前缀后文本 / float 向量 / 本地绝对路径。
