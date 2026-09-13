# 发布语义记忆句向量包

## Goal

用户在设置下载「语义记忆」后，同义问句能召回已存事实并显示 `来源：`。未下载或推理失败时召回仍是现网 FTS/`LIKE`。

## User value

上一刀混合召回已上线，但 catalog 无 URL，真机无法打开语义层；hash-bag 也过不了「美式 / 咖啡」。

## Background

- 已归档任务 `09-13-vector-semantic-memory`（`933fbd3`）：`HybridMemoryStore`、v2 `embedding BLOB`、设置行「语义记忆」、空 catalog。
- 设备现为 `HashBagEmbeddingPort`（`tokenizer.json`）。「我喜欢喝美式」对「我平时喝什么咖啡」余弦约 0.28，低于阈值 0.45。
- `IntentOrtJni` 在 `dougie_intent` 里只有**一个**全局 `OrtSession`。embed 必须用**第二套 session**，否则会卸掉意图模型。可复用已加载的 `libonnxruntime.so`。禁止 `models/intent/model.onnx`。
- `:core:tool` 已有 `BertWordPiece` + `vocab.txt`（意图 MiniRBT）。BGE 同为 WordPiece。
- `checkChannelLeak` 已禁 APK 内 `models/embed`。Play / 侧载同一 catalog 行。
- 用户决定：发可再分发的中文句向量 ONNX，不发 hash-bag 包。

## Requirements

- R1 设置「语义记忆」可下载、可测；`isConfigured()` 为真。
- R2 包就绪后，记住「我喜欢喝美式」，问「我平时喝什么咖啡」召回该事实且 Chat 有 `来源：`。未下载时问「美式」仍走 FTS。
- R3 权重 MIT 可再分发；默认 **BAAI/bge-small-zh-v1.5** 的 Xenova ONNX int8（约 24MB）+ `vocab.txt`。不上 APK。
- R4 问句加 BGE 官方前缀 `为这个句子生成表示以用于检索相关文章：`，写入事实不加前缀；向量 L2。
- R5 embed JNI 与 intent **分 session**；失败/`isReady()=false` → FTS，不空召回、不崩 Loop。
- R6 不 log 事实、问句、向量、本地绝对路径。

## Out of scope

- 改 MemoryGate / LoopEngine 预算、sqlite-vec、云端 embedding、把权重打进 APK。
- Hash-bag 作为可下载产物（JVM 测试夹具可留）。
- ASR Rule D、Kokoro、第三方连点。

## Key Decisions

- 模型：`bge-small-zh-v1.5`（MIT、中文、512 维）。int8 ONNX 来自 `Xenova/bge-small-zh-v1.5` `onnx/model_int8.onnx`（约 24MB，&lt; 50MB）。
- 分发：与 ASR 一样 catalog 钉 HuggingFace HTTPS + SHA-256（实现时下载后锁定）。`tokenizer` 规格写在代码里（`bert_wordpiece`，`max_len=64`），不另下一份 HF `tokenizer.json`。
- 布局：`models/embed/{model.onnx,vocab.txt}`。`EmbedModelLayout.isPresent` 两文件非空。
- 端口：`:tool:system` 实现 `EmbeddingPort`；tokenize 用现有 `BertWordPiece`。`:core:memory` 的 Fake / Hybrid 不变。

## Acceptance Criteria

- [x] AC1 设置行可下载；未配置/失败文案仍是现有中文常量。
- [x] AC2 真机：存「我喜欢喝美式」→ 问「我平时喝什么咖啡」终答有 `来源：`；不问句关键词也能中。
- [x] AC3 卸掉/未装 embed 包：问「美式」仍有来源；问同义句不因 embed 失败变空崩。
- [x] AC4 `./gradlew :core:memory:test :core:tool:test :app:checkChannelLeak` 通过；Play/sideload zip 无 `models/embed`。
