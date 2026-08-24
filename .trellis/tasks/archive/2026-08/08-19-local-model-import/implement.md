# Implement — 外部模型目录 + 意图 ONNX 编码器（PRD V2.1.11）

SAF 目录切片若已在工作区，本清单以 **意图主路径替换** 为优先。

1. Layout：`IntentModelLayout` 改为 `model.onnx` + `tokenizer.json` + `labels.txt`。去掉 `model.gguf` / `quant.id` / Q4/Q8 id。`isPresent` = 三文件均非空。
2. Catalog：单一 offer `id=intent`，`standard()` 三行（asr/tts/intent）。删除 `intentQ4`/`intentQ8` 与 GGUF URL。无 HTTPS 时仍可按哈希从外部树识别。
3. `:core:tool`：`OnnxIntentEngine` 实现 `IntentEngine`。JVM 侧做特征（字符 n-gram 哈希，与 `tokenizer.json` 一致）+ softmax + 标签映射；`run(modelDir, features)` 可注入。禁止 `android.*`。槽位 MVP 空 map 或极简规则；`route` 来自标签表第三列或固定 map。
4. `:tool:system`：薄 JNI `IntentOrtJni`，`System.loadLibrary("onnxruntime")`（与 sherpa 同 .so）后 `OrtSession` 跑 `model.onnx`。禁止第二份 ORT、禁止 llama.cpp。删除 `LlamaJni` / `llama_jni.cpp` / `fetchLlamaCpp` / CMake llama。
5. 生成并提交小型 ONNX 包（testdata + 可选 sideload seed）：含 `现在几点` → `query_time`。权重可用线性层；金标 utterance 覆盖 schema 中除评测用 mismatch 外的标签。不提交 GGUF。
6. 接线：`AndroidIntentPort`、`AppOfflineModelProbe`（probe id 改为 `intent`）、设置一行「意图理解」、下载互斥逻辑删除。`gguf` 历史文件不得 `isInstalled`。
7. 测试：`LlamaIntentEngineTest` → `OnnxIntentEngineTest`；catalog/settings 单测改单 offer；`IntentEval` 仍解析 Tool JSON。
8. Spec：directory-structure / error-handling / logging / frontend settings 从 GGUF/llama 改为 ONNX 三文件 + 共用 ORT。
9. 验证：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`  
   `./gradlew :core:tool:test :feature:settings:test :app:compilePlayDebugKotlin`（必要时 `-x extractDebugAnnotations`）。

## 风险

- 与 sherpa 抢两份 `libonnxruntime.so`：只 load 现有 jniLibs。
- 勿把 DocumentFile 放进 `:core:tool`。
- 勿把用户文本写入 Logcat。
