# Design: MiniRBT 意图包

## Boundaries

| Piece | Where |
|-------|--------|
| 语料 JSONL、train/export 脚本 | 仓库 `eval/intent/` 或 `core/tool/scripts/intent/`（文本进 git；权重 gitignore） |
| Bert tokenize + 双 algorithm 引擎 | `:core:tool` `OnnxIntentEngine` |
| int64 `nativeInfer` | `:tool:system` `intent_ort_jni.cpp` / `IntentOrtJni` |
| 布局 `vocab.txt` | `IntentModelLayout` + catalog 四文件 |
| 用户下载 | 现有 `ModelInstaller` / Settings 意图行 |

Chat 短路径不改标签映射。`:cli` 不接 MiniRBT。

## Data flow

```text
text → BertTokenizer (vocab.txt, max_len≤32)
    → input_ids, attention_mask
    → ORT session model.onnx
    → logits[11] → softmax → labels.txt / routeFor
```

`tokenizer.json`：`algorithm` 为 `bert_wordpiece` 走上述路径；`char_ngram_fnv1a32_hash_bag` 仅 testdata / JVM 夹具。

## Contracts

- 骨干：`hfl/minirbt-h288` + `BertTokenizer`（官方要求，勿用 RobertaTokenizer）。许可 Apache-2.0，导出 ONNX 可再分发。
- 训练：MLM checkpoint 无 pooler；`transformers` 5 不能用 `BertTokenizer(vocab_file=)`（词表丢光）。解冻末两层 + pooler + 头，多种子直到 held-out ≥90%。
- 输入名与导出图对齐（常见 `input_ids` / `attention_mask`）；输出 11 维 logits，顺序与 `labels.txt` 一致。
- 旧包：三文件且无 Bert vocab → `isPresent` false 或 `isEngineReady` false，Chat 走 Loop，不崩溃。
- 语料：每类建议训练 ≥30、held-out ≥8 条中文近义句；`unknown` 用闲聊/混合短句。划分固定 seed，评测不得用训练集。
- 发布：训练机导出 `model.onnx` + tokenizer 侧文件 → GitHub Release 资产 → 写入 `OfficialModelCatalog.DEFAULT_INTENT_*` SHA/URL。
- JNI 仍只 load `onnxruntime` + `dougie_intent`。不打特征/文本日志。

## Compatibility

- 已安装 testdata 意图的用户必须再下官方行。
- testdata 目录可保留微型 Gemm 供单测，**不再**作为 catalog URL。

## Trade-offs

- 双 algorithm：CI 无完整 MiniRBT 也能测 softmax/JNI 形状；生产只发 Bert 包。
- 冻结编码器只训头：数据少时更稳；若 held-out 达不到 90% 再允许轻量解冻顶层。

## Rollback

Catalog 指回 testdata；JNI 保留 float 路径。用户再下一份旧包即可（不推荐作产品默认）。
