# Design: 意图包 int8

## Boundaries

| 件 | 位置 |
|---|---|
| 训头 + fp32 ONNX + 动态量化 | `core/tool/scripts/intent/train_export.py` |
| 评测 held-out | 同脚本（ORT Python 或现有 accuracy 打印） |
| Catalog | `OfficialModelCatalog` URL `.../intent-minirbt-v2/` + SHA + `sizeLabel` |
| 设置文案 | 跟 `sizeLabel`；`OfficialModelCatalogTest` / 若有 47MB 硬编码一并改 |
| JNI | 尽量不改；int64 输入、logits 输出 |

权重不入库。Play 不打正式 ONNX。

## Quantization

1. 保持现有训练与 `torch.onnx.export`（opset 14，`dynamo=False`）。
2. `onnxruntime.quantization.quantize_dynamic`：权重量化为 QInt8（MatMul/Gemm），写出仍名 `model.onnx`。
3. 量化后用同一 held-out 跑 classify；准确率 < 90% **停止**，catalog 仍指 v1。
4. 打印字节数；&gt; 20MB 同样不发。

不改 `model.onnx` 文件名，避免动 `IntentModelLayout`。

## Release

```text
gh release create intent-minirbt-v2 <four files> --repo IAmKings/Dougie
```

tokenizer/labels/vocab 若与 v1 字节相同，SHA 可不变，URL 仍要换成 v2 路径。`model.onnx` SHA 必变。

## Compatibility

- 已装 v1：本地哈希 ≠ catalog → 未安装，用户再下 v2。
- testdata hashbag 夹具不变。
- 回滚：catalog 改回 `intent-minirbt-v1` SHA。

## Risks

- 动态量化后 ORT mobile JNI 打不开 QDQ 图 → 需试 sideload 设置「测试」；失败则记录、不发。
- Python 需 `onnxruntime`（量化 API），与手机 `libonnxruntime.so` 不是同一构建，以真机探针为准。
