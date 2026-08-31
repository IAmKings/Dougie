# Implement: MiniRBT 意图包

## Checklist

1. 写 11 类自建 JSONL（train/held-out），无 PII。
2. Python：加载 `hfl/minirbt-h288`，`BertForSequenceClassification`（11 label），训头，导出 ONNX（int8 优先），写出 `tokenizer.json` + `vocab.txt` + `labels.txt`。脚本可复现，权重不入库。
3. `IntentModelLayout.isPresent` 识别 Bert 四件套；哈希袋三件套仅测试夹具。
4. `OnnxIntentEngine` 按 `algorithm` 分支 tokenize；softmax/`routeFor`/`MIN_CONFIDENCE` 不变。
5. `IntentOrtJni` 增加 int64 输入（或与图一致的 tensor）；float 路径留给夹具。ORT 会话仍单例+锁。
6. Catalog 改为 Release HTTPS + 新 SHA；`sizeLabel` 按实装。`OfficialModelCatalogTest` 哈希夹具与用户包脱钩。
7. 评测：held-out 调 `classify`（Android 仪器或导出后 ORT Python 门禁 + JVM 解析契约）。至少文档化如何跑；能进 `:core:tool:test` 的用小夹具。
8. Settings 探测：`现在几点` 在新包应成功且高置信（若探测仍允许低置信，另加一条单测钉死金标句）。
9. `./gradlew :core:tool:test :app:checkChannelLeak`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :tool:system:test :app:checkChannelLeak
```

训练/导出在有 PyTorch 的机器上跑脚本，不进 CI。

## Risky files

- `intent_ort_jni.cpp`：输错 tensor 名会整段 INTENT_FAILED。
- `OfficialModelCatalog`：SHA 与 Release 资产必须一次提交对齐，否则 Settings 永远下不全。
- `IntentModelLayout.isPresent`：切错会把旧包当已安装或新包当未安装。

## Before start

规划已含 MiniRBT + 自建语料。实现批准前不改产品代码。上传 Release 需要仓库写权限（实现阶段执行）。
