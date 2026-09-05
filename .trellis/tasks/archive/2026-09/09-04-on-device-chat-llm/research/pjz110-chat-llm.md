# PJZ110 端侧对话 LLM：LiteRT-LM spike

- **Date**: 2026-09-04
- **Device target**: OnePlus 13 / PJZ110 (Snapdragon 8 Elite, Adreno 830)
- **Docs**: [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android)

## Maven pin

`com.google.ai.edge.litertlm:litertlm-android:**0.16.1**` (Google Maven; latest stable as of metadata `lastUpdated` 2026-08-29. `0.17.0-alpha1` exists and is not used.)

Sideload-only via `:tool:chatllm` (`implementation` of the AAR, not `api`). Play classpath must not resolve `litertlm` or `:tool:chatllm`.

The AAR’s Kotlin metadata is **2.3.0** and its `.class` files are **version 65 (Java 21)**. Dougie compiles with **Kotlin 2.0.21 / JVM 17**, so JDK 17 javac cannot read the AAR (`类文件具有错误的版本 65.0, 应为 61.0`). Probe UI and Engine calls live in **Java** (`:tool:chatllm`) compiled against **Java 17 stubs** (`src/stub/java`); the real AAR is **`runtimeOnly`**. The app depends with **`sideloadRuntimeOnly`** so sideload Kotlin compile does not see the AAR.

## Official API (this pin)

- `Engine.setNativeMinLogSeverity(LogSeverity.ERROR)`
- `Engine(EngineConfig(modelPath, backend, cacheDir))` then `engine.initialize()` **off Main**
- `engine.createConversation()` → `Conversation.sendMessageAsync(text): Flow<Message>`
- Default backend **`Backend.CPU()`**; GPU is **`Backend.GPU()`** (OpenCL, not Vulkan). Do not use `Backend.NPU` in this slice.
- Sideload manifest `uses-native-library` for `libOpenCL.so` and `libvndksupport.so` (`required=false`) for GPU.

Model format is `.litertlm` on a POSIX path (`filesDir`). Do **not** use `litert-community/Qwen2.5-0.5B-Instruct` — that card is MediaPipe `.tflite` / `.task`, not LiteRT-LM.

**Spike 首选（约 328 MB，INT4，ctx 4096）：**

https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm

卡片：https://huggingface.co/litert-community/Qwen3-0.6B

备选：

- INT8 586 MB：https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm
- mixed INT4 ~475 MB：https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm
- 关 think、闲聊更快（~347 MB）：https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm
- 官方表 Qwen2.5-1.5B q8 ~1.5 GB：https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm

```bash
curl -L -o Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm \
  "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
```

## Copy model + launch (sideload Debug)

Package: `com.dougie.app.sideload`. Activity: `com.dougie.tool.chatllm.ChatLlmSpikeActivity` (`exported=true`, no launcher filter — `adb shell am start` from uid 2000 cannot start `exported=false`).

Do **not** nest `sh -c 'mkdir -p …'` inside `adb shell`: the local shell strips quotes and Android `mkdir` then gets zero path args (`mkdir: Needs 1 argument`).

Push into the app **external** files dir (no `run-as`). Probe also looks in internal `filesDir/models/chat`.

```bash
# After installing sideload debug APK (open the app once so Android/data exists)
adb shell mkdir -p /sdcard/Android/data/com.dougie.app.sideload/files/models/chat
adb push Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm \
  /sdcard/Android/data/com.dougie.app.sideload/files/models/chat/
adb shell am start -n com.dougie.app.sideload/com.dougie.tool.chatllm.ChatLlmSpikeActivity
```

Screen shows filename only (not the full path), backend, load ms, first-token ms, elapsed, chunk/char counts, 字符/秒 (not tok/s; the Kotlin API does not expose token ids). Prompt is hardcoded `用一句话介绍你自己`. Product Chat `localLlmReady` stays false.

Do not Logcat the prompt, completion, or weight path.

## Device metrics

Recorded 2026-09-05 from the sideload probe (hardcoded prompt `用一句话介绍你自己`, max 64 tokens). File name was not reported; spike default was `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`.

| Field | Value |
|---|---|
| Device | PJZ110 (task target; user 真机) |
| LiteRT-LM | 0.16.1 |
| Model filename | not reported |
| CPU load | 1321 ms |
| CPU first token | 2453 ms |
| CPU generate | 3810 ms · 19 chunks · 36 chars · **9.4 字符/秒** |
| CPU status | 成功 |
| GPU (OpenCL) load | 4109 ms |
| GPU first token | 799 ms |
| GPU generate | 964 ms · 13 chunks · 25 chars · **25.9 字符/秒** |
| GPU status | 成功 |

Both backends finished a Chinese reply. Probe 字符/秒 is not tok/s.

CPU meets the spike budget (first token < 5s, full reply < 30s). GPU decode is ~2.8× 字符/秒 vs CPU; **load is slower** (4.1s vs 1.3s), typical of OpenCL compile/tuning on first engine create. Subsequent turns were not measured.

This **GPU OpenCL path completed**; it is not the llama.cpp Vulkan failure mode on the same Adreno class.

## NPU on this SoC (not measured)

PJZ110 = Snapdragon **8 Elite (SM8750)**. Silicon has Qualcomm **Hexagon HTP / AI Engine Direct** (NPU). Hardware support is yes.

That does **not** mean the current spike uses it. The probe only ran `Backend.CPU()` / `Backend.GPU()` (OpenCL). LiteRT-LM `Backend.NPU(nativeLibraryDir=…)` needs vendor HTP/QNN libs (Play **PODAI** to ship) and usually a **chipset-compiled** `.litertlm`. The official `Qwen3-0.6B.mediatek.mt6993.litertlm` is **MediaTek NPU**, not SM8750.

Published *other stacks* (not this APK): Qualcomm AI Hub Qwen3-0.6B **GENIEX_QAIRT** on Snapdragon 8 Elite ~**107 tok/s** decode / ~20 ms TTFT (w4a16). LiteRT community Qwen3-0.6B **MediaTek NPU** on vivo V2502A: decode **36 tok/s** vs GPU OpenCL **21 tok/s** on the same card. Google LiteRT README: Gemma3-1B on S25 Ultra **NPU** decode 84.8 tok/s vs S24 Ultra **GPU** 44.6 tok/s (different phones/models).

**Expectation:** a correctly compiled SM8750 NPU artifact *can* beat this GPU OpenCL run (~26 字符/秒, ~0.8s first token). It is not guaranteed for the INT4 file already on the phone, and it was **not** run on PJZ110. Do not treat NPU as a free `Backend.NPU()` switch.

## Conclusion

**Adopt LiteRT-LM** for a later product slice (settings download + `LlmProvider.isLocal` + `localLlmReady`). Do not restore llama.cpp / GGUF. Do not add ExecuTorch or MNN unless a later LiteRT product path fails.

Product notes (out of this spike): keep the Engine warm so GPU load is not paid every message; idle/first-open can stay CPU if 4s GPU init is too visible; NPU/PODAI still untested.

Intent MiniRBT ONNX is unchanged. `localLlmReady` stays false until a chat provider is wired.

## Next (not this slice)

1. Catalog + download a `.litertlm` chat pack; sideload-only Engine; wire `OpenAICompatibleProvider` alternative / `isLocal`.
2. Do **not** pull ExecuTorch XNNPACK or MNN unless LiteRT productization fails.

## Gradle

`:app:assembleSideloadDebug` and `:app:checkChannelLeak` passed on 2026-09-04 (JDK 17). Device metrics filled 2026-09-05.
