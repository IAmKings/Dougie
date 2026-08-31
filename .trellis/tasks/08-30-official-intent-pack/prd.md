# 正式意图分类包

## Goal

把用户下载的意图包换成 **MiniRBT-h288 量级编码器 + 11 类分类头** 的 ONNX；运行时用 Bert 分词与 int64 输入。仓库自建中文近义句训头、导出并更新 catalog。近义「问时间 / 问电量」能高置信走已有 Chat 短路径；held-out 文本准确率 ≥ 90%。

## User value

离线短指令能覆盖说法变体，不再依赖与「现在几点」字面重合。

## Background

- 已拍板：MiniRBT 量级编码器；标注语料仓库自建（仅文本，无真实聊天）。
- Chat 短路径已归档：高置信 `query_time` / `query_battery` 跳过 LLM。
- 今日包：n-gram 哈希袋 + testdata Gemm（只拟合「现在几点」）。`IntentOrtJni` 吃 `float[1, dim]`。
- MiniRBT-h288（`hfl/minirbt-h288`，约 12.3M 参数，Apache-2.0）用 **BertTokenizer / BertModel**，不是 Roberta。fp32 ~50MB 量级，**int8 后目标落入 PRD 10–20MB**。
- 11 类 `labels.txt` 不变。Play 不内置 ONNX。不改 `intent_classifier` 成功 JSON；不把 utterance/intent 写入日志。
- `IntentEval` 今日只比解析后的 intent 字段；本任务要对 held-out **真 classify**。

## Requirements

- R1 骨干 `hfl/minirbt-h288`，冻结或轻量微调编码器，在自建 11 类文本上训分类头，导出 ONNX（优先 int8，体积超 20MB 须在 design 记录原因）。
- R2 布局：`models/intent/` 至少 `model.onnx` + `tokenizer.json` + `vocab.txt` + `labels.txt`。`tokenizer.json` 标明 `algorithm=bert_wordpiece`（名称以实现为准）。旧哈希袋包视为未就绪，须重新下载，不得用错图硬推理。
- R3 JNI / `OnnxIntentEngine`：Bert 路径喂 `input_ids` + `attention_mask`；testdata 哈希袋夹具可保留供 JVM 单测（`algorithm` 分支）。禁止第二份 ORT、禁止 llama.cpp。
- R4 语料进 git（JSONL/TSV 文本）：每类训练与 held-out 划分；实现期写近义句，不含用户聊天、密码、PII。
- R5 Catalog HTTPS + SHA 指向新包（GitHub Release 或同等，不把正式权重放 `src/test/resources` 冒充夹具）。`sizeLabel` 按实装体积改。
- R6 held-out 上 `classify` 准确率 ≥ 90%（规则 E）。CI 不训练、不提交完整 MiniRBT 权重（gitignore 除 allowlist 小夹具外继续拒 `*.onnx`）。
- R7 设置探测仍可 `classify("现在几点")` 不抛；该句在新包上应为高置信 `query_time`。

## Out of scope

- 端侧对话 LLM、Kokoro、向量记忆、其它意图短路径。
- CI 真机 P95 ≤ 500ms（本机抽测写入任务笔记即可）。
- 用真实用户对话训练。

## Technical notes

- 旧设备已装 testdata 包：升级 App 后 `isPresent` 因缺 `vocab.txt` 或 algorithm 不匹配而走 Loop，直到用户重新下载意图行。
- 权重托管：与 ASR 一样 HTTPS；本产品自研头优先 GitHub Release（`IAmKings/Dougie`），避免把 10MB+ 放 testdata raw。

## Acceptance Criteria

- AC1 自建语料在仓库；held-out 每类至少数条近义句；评测脚本/测试对真推理准确率 ≥ 90%。
- AC2 新包 `现在几点` / `现在几点了？` / 至少一条电量近义句 → 对应标签且 confidence ≥ 0.5（JVM 或文档化的导出后评测）。
- AC3 Catalog SHA 与发布文件一致；旧哈希袋三文件不再被标为已安装。
- AC4 `:core:tool:test` 仍用小型夹具通过；`:app:checkChannelLeak` 禁止 Play APK 含正式意图 ONNX。
- AC5 `intent_classifier` 成功 JSON 字段不变。
