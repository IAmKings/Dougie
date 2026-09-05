# Implement: PJZ110 LiteRT-LM 选型

## Checklist

1. 对照 `08-19-local-model-import/research/android-intent-runtime.md` LiteRT-LM 节与官方 Android 文档（版本钉死进 research）。
2. Sideload 依赖 `litertlm-android`；Play 不引入或保证 leak 检查。
3. 探针读 `filesDir/models/chat/*.litertlm`；先 CPU 后可选 GPU。
4. 真机跑一句中文；数字写入 `research/pjz110-chat-llm.md`。
5. `./gradlew :app:checkChannelLeak`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:checkChannelLeak
```

权重不入库。Prompt 全文不进 Logcat。

## Risky

- 把 LiteRT-LM 打进 Play 或把 `.litertlm` 打进 APK。
- 再拉 llama.cpp / GGUF。
- GPU 用 Vulkan 后端。
- 第二份 ORT GenAI 与 sherpa 抢 `libonnxruntime.so`。
