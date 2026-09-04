# 意图包 int8

## Goal

把现网 MiniRBT 意图 ONNX（catalog「约 47MB」fp32）量化到 PRD **10–20MB**，设置下载更轻；短路径标签与 JNI 输入不变。held-out 仍 ≥90%，否则不发。

## Background

- 队列：钉附件已归档；本切片；其后向量记忆、端侧对话 LLM。
- 现网：`intent-minirbt-v1` / `model.onnx` + tokenizer/vocab/labels；`OfficialModelCatalog` `sizeLabel`「约 47MB」。`train_export.py` 只 `torch.onnx.export` fp32，无量化步。
- 布局文件名仍是 `model.onnx`（`IntentModelLayout`）。JNI 喂 int64 `input_ids`/`attention_mask`；量化只动权重。
- 语料已在 `core/tool/scripts/intent/`。CI 不训、不提交正式 ONNX。Play APK 不得带意图权重。
- Q1 已拍板：新 Release **`intent-minirbt-v2`**，不覆盖 v1（回滚可改回 catalog）。

## Requirements

- R1 导出后 `model.onnx` ≤ 20MB；`sizeLabel` 按实装改。
- R2 四件套文件名不变；`algorithm=bert_wordpiece`；不新增 ORT。
- R3 held-out 真推理 ≥90%；「现在几点」仍高置信 `query_time`。低于 90% 不更新 catalog。
- R4 Catalog 指向 `intent-minirbt-v2` 的 HTTPS+SHA；`:core:tool:test` 小夹具；`checkChannelLeak`。
- R5 不改 11 类 labels、短路径映射、成功 JSON。已装 v1 须用户再下载。

## Acceptance Criteria

- [x] AC1 发布包体积 ≤20MB，设置文案与 `sizeLabel` 一致。~12MB。
- [x] AC2 held-out ≥90%；探针句 `现在几点` 仍过。81/88。
- [x] AC3 Catalog URL 含 `intent-minirbt-v2`；SHA 与资产一致。
- [x] AC4 小夹具单测 + `checkChannelLeak` 过。
- [x] AC5 真机下载 v2 后短路径仍可用。意图分类测试通过。

## Out of scope

- 改语料/标签、向量记忆、端侧 LLM、听写短路径。
- 覆盖或删除 v1 Release。
- 已装 v1 自动替换（SHA 对不上即显示未安装）。
