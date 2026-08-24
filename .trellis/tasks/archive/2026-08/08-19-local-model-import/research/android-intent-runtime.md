# Research: Android on-device intent runtime (replace llama.cpp JNI)

- **Query**: Feasible Android on-device inference stack to replace llama.cpp JNI + Qwen3-0.6B GGUF for offline Chinese intent classification (`IntentClassifierTool`). Vulkan fails after first token on OnePlus PJZ110; CPU fallback emits 256 tokens and misses PRD P95 ≤ 500ms.
- **Scope**: mixed (repo internals + official docs / GitHub READMEs)
- **Date**: 2026-08-20

## Findings

### Current Dougie path (what exists)

| File Path | Description |
|---|---|
| `core/tool/src/main/kotlin/com/dougie/core/tool/IntentClassifierTool.kt` | Tool contract: `text` in; JSON `intent` / `slots` / `route` / `confidence` out; `confidence < 0.5` → `INTENT_LOW_CONFIDENCE`; never calls `EgressGateway` |
| `core/tool/src/main/kotlin/com/dougie/core/tool/IntentPort.kt` | `IntentPort` + `IntentEngine` + `IntentHit` + `IntentModelLayout` (`filesDir/models/intent/model.gguf`, Q4/Q8 `quant.id`) |
| `core/tool/src/main/kotlin/com/dougie/core/tool/LlamaIntentEngine.kt` | Ready = layout present + native; `complete(modelDir, IntentPrompt.render(text))` then `IntentJsonParser` |
| `core/tool/src/main/kotlin/com/dougie/core/tool/IntentJsonParser.kt` | `/no_think` Chinese JSON prompt; parse first JSON object; strip `<think>` |
| `tool/system/src/main/kotlin/com/dougie/tool/system/AndroidIntentPort.kt` | Wires `LlamaIntentEngine` + `LlamaJni` |
| `tool/system/src/main/kotlin/com/dougie/tool/system/LlamaJni.kt` | `System.loadLibrary("llama")`; POSIX absolute path to `model.gguf` |
| `tool/system/src/main/cpp/llama_jni.cpp` | Vulkan `n_gpu_layers=-1`, CPU fallback, greedy, **`kMaxPredict = 256`**, ctx 2048, ChatML |
| `tool/system/build.gradle.kts` | `fetchLlamaCpp` clones ggml-org/llama.cpp; CMake `GGML_VULKAN=ON` when `glslc` exists |
| `core/tool/src/main/kotlin/com/dougie/core/tool/OfficialModelCatalog.kt` | Q4 Unsloth `Qwen3-0.6B-Q4_K_M.gguf`; Q8 `Qwen/Qwen3-0.6B-GGUF` Q8_0; SHA-256 |
| `app/src/main/kotlin/com/dougie/app/AppOfflineModelProbe.kt` | Settings 测试: `classify("现在几点")` must not throw |
| `core/tool/src/test/resources/eval/intent-gold.json` | Closed catalog of 10 intents + `unknown` (all gold items have empty `slots`) |
| `tool/system` sherpa JNI | Already ships `libonnxruntime.so` (sherpa-onnx v1.13.4 static-link tarball) for ASR/TTS |

**PRD constraints (root `PRD.md`):**

- §6.10 / rule E: offline; structured JSON; Chinese; Qwen3-0.6B chosen vs SmolLM English-first; **accuracy ≥ 90%**; **P95 ≤ 500ms**; optional download; not in Play APK / not in speech budget (rule A).
- §17.1 Local LLM options listed as **llama.cpp / ONNX Runtime**.
- Product copy: this Tool is a **router**, not the main LLM. MVP does not include full on-device LLM productization.

**Latency arithmetic on the current JNI:** 256 greedy tokens on CPU cannot meet 500ms. Even a “successful” GPU path would need **short constrained JSON** (tens of tokens), not 256.

**Target phone:** OnePlus **PJZ110** = OnePlus 13, **Snapdragon 8 Elite (SM8750)**, Adreno 830. Qualcomm NPU (HTP / Hexagon) is present; llama.cpp Vulkan on Adreno is the failing path.

---

### 1. Google AI Edge / LiteRT / MediaPipe LLM Inference

**MediaPipe LLM Inference API** is **maintenance-only**. Google documents migration to **LiteRT-LM** Kotlin.

| Item | Official fact |
|---|---|
| Maven | `com.google.ai.edge.litertlm:litertlm-android` |
| Android API | `Engine(EngineConfig(modelPath, backend))` then `Conversation.sendMessage` / Flow |
| Model path | POSIX filesystem path (fits `filesDir` cache) |
| Backends | `Backend.CPU()`, `Backend.GPU()`, `Backend.NPU(nativeLibraryDir=...)` |
| GPU extra | Manifest `uses-native-library` for `libOpenCL.so` / `libvndksupport.so` (OpenCL, not Vulkan) |
| NPU | LiteRT unified NPU: Qualcomm AI Engine Direct, MediaTek, Google Tensor, Exynos, Intel. Play **PODAI** for shipping NPU runtimes |
| Model family | Gemma 3/4, FunctionGemma, Phi-4-mini, **Qwen2.5-0.5B / Qwen2.5-1.5B / Qwen3-0.6B**, others via Hugging Face LiteRT Community (`.litertlm`) |
| Qwen3-0.6B size | **586 MB** (official table) |
| Qwen3-0.6B published speed | Vivo X300 Pro: CPU prefill 165 tok/s, decode **9 tok/s**; GPU prefill 580 tok/s, decode **21 tok/s** |

**Fit for P95 ≤ 500ms (generative JSON):** at 21 GPU tok/s, ~40 JSON tokens ≈ **1.9s decode** plus prefill. That **misses 500ms** unless NPU decode is substantially faster than the published GPU numbers, or generation is capped to a handful of tokens (incompatible with free-form JSON of current prompt).

**FunctionGemma** (289 MB, S25 Ultra CPU decode 154 tok/s) is closer to budget for short tool JSON, but it is a Gemma/function-calling family, not a Chinese-first encoder. PRD already rejected English-first SmolLM for this Tool.

**LiteRT (non-LM) `CompiledModel`:** appropriate for **encoder / embedding `.tflite`**, including GPU (`Accelerator.GPU`). Separate from LiteRT-LM chat orchestration.

Citations: [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview), [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android), [MediaPipe LLM Android (maintenance)](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android), [LiteRT NPU](https://ai.google.dev/edge/litert/next/npu), [LiteRT GitHub](https://github.com/google-ai-edge/litert).

---

### 2. ExecuTorch (Qwen export, XNNPACK, Vulkan, QNN)

Official Android AARs: **XNNPACK (CPU)**, **Vulkan (GPU)**, **Qualcomm AI Engine (QNN)**. Docs: start on XNNPACK, then add vendor backends.

Qwen3 export (repo `examples/models/qwen3/README.md`): `python -m examples.models.llama.export_llama --model qwen3-0_6b ... -X --xnnpack-extended-ops -qmode 8da4w` → `.pte`. QNN LLM export is a **separate** Qualcomm script (`examples/qualcomm/oss_scripts/llama/`); wiki warns **not** to use `export_llama.py --qnn`.

Qwen/Gemma tokenizers need **regex lookahead**; Android CMake `-DSUPPORT_REGEX_LOOKAHEAD=ON`.

**Fit:** first-class Qwen3-0.6B CPU path. Vulkan backend is the **same GPU API class** that already fails on PJZ110 with llama.cpp. QNN can use Snapdragon 8 Elite HTP, but needs QNN SDK, chipset-specific lowering, and a second native stack besides sherpa.

Citations: [Using ExecuTorch on Android](https://docs.pytorch.org/executorch/main/using-executorch-android.html), [Qwen3 example README](https://github.com/pytorch/executorch/blob/main/examples/models/qwen3/README.md), [export model-specific wiki](https://github.com/pytorch/executorch/blob/main/.wiki/export/model-specific.md).

---

### 3. MLC-LLM Android

Official Android SDK compiles TVM model libraries into `libtvm4j_runtime_packed.so` + `tvm4j_core.jar`; weights via `mlc_llm package` / `mlc-package-config.json`. Android compile output is `.tar` objects. GPU: **Vulkan preferred** for Android; some devices OpenCL-only.

**Fit:** mature chat-app packaging, not a small classifier runtime. Vulkan-centric GPU repeats the PJZ110 failure mode. Heavy compile/packaging toolchain vs “router Tool”.

Citations: [MLC Android SDK](https://llm.mlc.ai/docs/deploy/android.html), [TVM GPU (Vulkan/OpenCL)](https://llm.mlc.ai/docs/install/tvm.html), [compile models](https://llm.mlc.ai/docs/compilation/compile_models.html).

---

### 4. ONNX Runtime GenAI / ONNX Runtime Mobile

**ORT GenAI** (`microsoft/onnxruntime-genai`): generative loop (tokenize, KV cache, sampling, optional structured/tool decoding). Support matrix: **Qwen** architectures; **Java**; **Android**; **CPU** and **QNN** listed as hardware acceleration (CUDA/DirectML/WebGPU are desktop). API documented as **preview**. Maven install page currently emphasizes Python/NuGet; Android samples live under onnxruntime-inference-examples (Phi-3 Android historically used GenAI Java `Model` / `Generator` / `Tokenizer`).

**ORT Mobile / `onnxruntime-android`:** non-generative graphs (BERT, embeddings, classifiers). Android EPs: **CPU, XNNPACK, NNAPI**. Model must often be ORT format for the mobile-reduced opset.

**Repo overlap:** `:tool:system` already loads **`libonnxruntime.so`** for sherpa. A second ORT/GenAI `.so` risks ABI/version clash unless the same ORT build is reused.

**Fit for classifier:** ORT Mobile / existing `libonnxruntime.so` is a natural **encoder** host. **Fit for 0.6B generate:** GenAI + QNN could theoretically hit latency on 8 Elite; still a full LLM productization path, and GenAI is preview.

Citations: [ORT GenAI GitHub](https://github.com/microsoft/onnxruntime-genai), [ORT generate() docs](https://onnxruntime.ai/docs/genai), [Deploy on mobile](https://onnxruntime.ai/docs/tutorials/mobile/), [Get started ORT Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html).

---

### 5. Alibaba MNN

Official: lightweight engine used in Alibaba apps; **MNN-LLM** for on-device LLM (Qwen / Llama / etc.). Export: `llmexport.py --export mnn --hqq` → directory `llm.mnn`, `llm.mnn.weight`, tokenizer, `config.json`. Android GPU: **`MNN_OPENCL=ON`**. `backend_type`: `cpu` | `opencl`.

**Android Hexagon (HTP/cDSP):** documented LLM path; **4-bit symmetric** weights only (`--quant_bit 4 --sym`); Transformer C4 export; `backend_type: hexagon`; extra `libMNN_htpops.so` / skel. Example export path in docs is **`Qwen3-0.6B`**. Android Chat app README: Qwen3 support.

**Fit:** Chinese-vendor stack with OpenCL (not Vulkan) GPU and a **documented Hexagon path for Qwen3-0.6B** on Qualcomm phones. Still a generative LLM (multi-file model dir, JNI). Better GPU API match than llama Vulkan on Adreno; Hexagon is the latency-relevant backend on PJZ110. Binary size ~core hundreds of KB to low MB, models still hundreds of MB.

Citations: [alibaba/MNN](https://github.com/alibaba/MNN), [MNN-LLM user guide](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html) (Hexagon section + OpenCL Android flags), [MNN Chat Android](https://github.com/alibaba/MNN/blob/master/apps/Android/MnnLlmChat/README.md).

---

### 6. Encoder classifier vs generative 0.6B

**Task shape in-repo:** closed intent list (~10 labels in `intent-gold.json`); Tool JSON includes `slots` but gold slots are empty; product is a **router** (PRD §6.10). Current engine is generative because Qwen3 was chosen as a small **Instruct LLM**, not because classification requires autoregression.

| Approach | Typical size | Latency class vs 500ms | Chinese | Slots | Offline download |
|---|---|---|---|---|---|
| MiniRBT-H256 / H288 / RBT4-H312 (HFL) | **10–12 MB** weights (fp32); int8 smaller | One encoder pass; mobile BERT-tiny class is **tens of ms**, well under P95 | Distilled Chinese RoBERTa-wwm | Need separate slot rules or a second small head | Optional tiny pack |
| GTE-small-zh (Alibaba DAMO) | **~0.10 GB** fp; ONNX exists in ecosystem | One pass; larger than MiniRBT | Chinese embedding | Cosine-to-prototypes or linear head; slots still extra | Optional |
| Qwen3-Embedding-0.6B LiteRT | Same **0.6B** class; HF card ~**390 ms** GPU embed on Pixel 8a | **Near/over P95 for embed alone**, then still a head | Multilingual | Retrieval, not labels | Large optional download |
| Generative Qwen3-0.6B (any runtime) | **~420–640 MB** | Decode-bound; published GPU **~21 tok/s** ⇒ JSON of tens of tokens **>500ms**; 256 tok **>>500ms** | Yes | Free-form JSON | Optional, Play-budget OK |

**Encoder is a better fit for this Tool** given: closed catalog, rule E latency, “not a full LLM”, existing ORT in the APK, and PJZ110 Vulkan failure. Slot filling for MVP can be **regex / allowlists** (`open_app` names, calendar phrases) plus `confidence` from softmax; complex slots remain Cloud LLM per PRD (low confidence → clarify / cloud).

Accuracy ≥ 90% is **not guaranteed** by MiniRBT out of the box: needs a **fine-tuned linear head** on a Chinese intent set covering the gold labels. That is a training artifact (ONNX/TFLite + tokenizer + SHA-256), not a runtime research gap.

Citations: [MiniRBT README](https://github.com/iflytek/MiniRBT/blob/main/README_EN.md), [hfl/minirbt-h288](https://huggingface.co/hfl/minirbt-h288), [thenlper/gte-small-zh](https://huggingface.co/thenlper/gte-small-zh), [Qwen3-Embedding-0.6B-LiteRT](https://huggingface.co/litert-community/Qwen3-Embedding-0.6B-LiteRT).

---

### 7. Qualcomm AI Hub / QNN (OnePlus Snapdragon)

PJZ110 = **Snapdragon 8 Elite**. AI Hub publishes **Qwen3-0.6B** for **Snapdragon 8 Elite Mobile** (and 8 Elite Gen 5).

Published (Hugging Face `qualcomm/Qwen3-0.6B`):

| Runtime | Precision | Chipset | Response rate | Short-prompt TTFT |
|---|---|---|---|---|
| **GENIEX_QAIRT** | w4a16 | Snapdragon 8 Elite Mobile | **~107 tok/s** | **~20 ms** (up to ~0.63 s at 4096 ctx) |
| GENIEX_LLAMACPP | q4_0 GGUF ~429 MB | same | ~57–101 tok/s | tens–hundreds of ms |

**~40 JSON tokens at 107 tok/s + 20 ms TTFT ≈ 390 ms** — the **only published generative numbers in this research that land inside 500ms** on this SoC class.

Caveats from the same sources: **QAIRT ≥ 2.45**, **GenieX** runtime (Genie being deprecated), **chipset-specific** context binaries, Workbench/API token to export, Qualcomm Generative AI terms, Android 15+ tutorials. Play-wide shipping of QNN/HTP libs is the hard part (LiteRT NPU + Play PODAI is Google’s packaging story; AI Hub apps are samples). OnePlus Hexagon driver / permission differences vs Samsung QRD are **not** covered by Hub QRD numbers.

Citations: [AI Hub Qwen3-0.6B](https://aihub.qualcomm.com/models/qwen3_0_6b), [qualcomm/Qwen3-0.6B HF](https://huggingface.co/qualcomm/Qwen3-0.6B), [ai-hub-models qwen3_0_6b](https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/qwen3_0_6b/manifest.yaml), [LLM on Genie tutorial](https://github.com/qualcomm/ai-hub-apps/blob/main/tutorials/llm_on_genie/README.md), [LiteRT Qualcomm NPU](https://ai.google.dev/edge/litert/next/npu).

---

## Comparison table

| Stack | Model format | Android API | GPU/NPU | Qwen3-0.6B | Hits P95 ≤500ms as JSON LLM? | Hits P95 as encoder? | Play optional download | JNI POSIX `filesDir` | Extra APK native | Vulkan risk on PJZ110 | MVP router fit |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Current llama.cpp JNI** | GGUF | custom JNI | Vulkan then CPU | Yes (catalog) | No (256 tok CPU; Vulkan broken) | n/a | Yes | Yes | `libllama.so` + ggml | **Observed fail** | No |
| **LiteRT-LM** | `.litertlm` | Kotlin `Engine` | CPU / **OpenCL GPU** / NPU | Official 586 MB card | Unlikely on GPU decode 21 tok/s; NPU unstated for this card | n/a | Yes | Yes | LiteRT-LM AAR | Low (OpenCL) | Weak for latency; strong Google API |
| **LiteRT CompiledModel** | `.tflite` | Kotlin | CPU/GPU/NPU | Embed 0.6B too slow | n/a | **Yes** for MiniRBT-class | Yes | Yes | LiteRT (or TFLite) | Low | **Strong** |
| **ExecuTorch** | `.pte` | Java/Kotlin AAR | XNNPACK / Vulkan / QNN | Official export | CPU likely miss; QNN possible; Vulkan risky | Encoder possible | Yes | Yes | executorch AAR | High if Vulkan | Medium (Qwen export exists) |
| **MLC-LLM** | TVM lib + weights | mlc4j | Vulkan / OpenCL | Possible compile | Unlikely + Vulkan | n/a | Awkward (compiled libs) | Yes | TVM runtime | High | Poor |
| **ORT GenAI** | ONNX LLM | Java GenAI (preview) | CPU / QNN | Architecture supported | QNN maybe; CPU no | n/a | Yes | Yes | Second ORT risk vs sherpa | Low | Medium |
| **ORT Mobile / existing sherpa ORT** | ONNX classifier | Java `OrtSession` | CPU / XNNPACK / NNAPI | No (use MiniRBT ONNX) | n/a | **Yes** | Yes | Yes | **Reuse `libonnxruntime.so`** | None | **Strongest MVP** |
| **MNN-LLM** | `llm.mnn` dir | C++/JNI | CPU / **OpenCL** / **Hexagon** | Official Qwen3-0.6B Hexagon export | Hexagon maybe; OpenCL unknown | Encoder also supported by MNN core | Yes (multi-file) | Yes | `libMNN.so` + HTP ops | Low | Medium (still LLM) |
| **Qualcomm GenieX + QNN** | QAIRT context | GenieX / sample ChatApp | HTP | Hub Qwen3-0.6B | **Published ~390ms for ~40 tok on 8 Elite** | n/a | Yes | Yes | QAIRT/GenieX | n/a | OnePlus-capable; not Play-generic |

---

## Recommended MVP path

**Primary (feasible for rule E + “router not LLM”):** replace generative GGUF with a **fine-tuned Chinese encoder + linear head** over the closed intent schema, run on **existing ONNX Runtime** (`OrtSession` JNI or a thin Kotlin wrapper around the already-loaded `libonnxruntime.so`), files under `filesDir/models/intent/` (e.g. `model.onnx` + `tokenizer` / labels). Map softmax → `IntentHit` (intent, empty-or-rule slots, route table, confidence). Keep optional HTTPS + SHA-256 catalog (one small pack, drop Q4/Q8 GGUF mutual overwrite).

**If product still requires generative JSON without training a head:** treat **Qualcomm GenieX QAIRT Qwen3-0.6B** as a **PJZ110 / Snapdragon 8 Elite experiment** only (published tok/s can meet 500ms for **short** JSON). Do not use it as the Play-generic engine.

**Do not choose as MVP replacement:** llama.cpp Vulkan, MLC Vulkan, ExecuTorch Vulkan, LiteRT-LM Qwen3-0.6B as the latency solution, Qwen3-Embedding-0.6B as the classifier.

**Secondary generative (Google-maintained, if encoder slots later fail):** LiteRT-LM with **hard max tokens + stop at first `}`**, GPU/NPU — still expect **device measurement**; published 21 tok/s GPU does not support 500ms.

---

## What to keep

- `IntentClassifierTool` + `IntentPort` / `IntentEngine` / `IntentHit` contract (`intent`, `slots`, `route`, `confidence`, `MIN_CONFIDENCE = 0.5`).
- Fail-closed Chinese errors; **no** silent cloud from this Tool.
- `OfficialModelCatalog` **SHA-256** + HTTPS optional download; Play vs sideload flavors; SAF tree → **`filesDir` POSIX cache** for native.
- Settings **测试** smoke: `classify("现在几点")` must not throw (`AppOfflineModelProbe` / `OfflineModelProbe`); low confidence still OK for probe per spec.
- `IntentEval` threshold 0.90 and gold labels (extend gold with real model outputs later).
- sherpa `libonnxruntime.so` path (reuse rather than a second ORT).

## What to delete (llama JNI)

- `LlamaJni.kt`, `llama_jni.cpp`, CMake `libllama.so`, `fetchLlamaCpp`, `third_party/llama.cpp` clone, `GGML_VULKAN`.
- `LlamaIntentEngine` name/implementation (replace with encoder or new runtime engine behind the same `IntentEngine`).
- Catalog GGUF URLs (`DEFAULT_INTENT_Q4` / `Q8`) and `IntentModelLayout.MODEL_FILE = "model.gguf"` / Q4–Q8 **exclusive overwrite** once a single classifier pack exists.
- Logging of `model.gguf.stat` llama counters.

---

## Risks

- **Encoder accuracy:** 90% needs a trained head; MiniRBT weights alone are not an intent model.
- **Slots:** generative JSON hid slot extraction; encoder MVP may return empty slots except rule-based ones.
- **ORT dual use:** sherpa vs classifier must share one `libonnxruntime.so` version.
- **NPU/GenieX:** Hub numbers are QRD, not OnePlus; HTP skel libs and Android 16 page size (repo already 16 KB for llama) still apply.
- **LiteRT-LM NPU:** Play PODAI / vendor `.so` packaging; `engine.initialize()` can take seconds (load, not per-query).
- **MNN Hexagon:** 4-bit symmetric only; extra HTP `.so`; multi-file layout vs current single `model.gguf`.
- **FunctionGemma / Gemma:** English-centric vs PRD Chinese requirement.

---

### Related Specs

- `.trellis/spec/backend/directory-structure.md` — Intent GGUF never bundled; `IntentPort`; llama JNI Vulkan/CPU; catalog SHA-256; filesDir JNI cache
- `.trellis/spec/backend/error-handling.md` — `model.gguf` missing; probe `现在几点`; intent probe timeout 180s
- `.trellis/spec/backend/logging-guidelines.md` — no prompt/completion logs from llama JNI
- `.trellis/spec/frontend/directory-structure.md` — Q4/Q8 settings rows; smoke probe injection
- Root `PRD.md` §6.10, §17.1, rule E / decision #21

## Caveats / Not Found

- No PJZ110-specific LiteRT-LM or MNN Hexagon published tok/s for Qwen3-0.6B (Hub numbers are Snapdragon 8 Elite QRD).
- ORT GenAI Android Maven coordinates were not on the main install page (Python/NuGet); Android is in the GitHub support matrix + historical Phi-3 sample.
- ExecuTorch Qwen3 README fetch via raw GitHub returned empty in one attempt; facts taken from GitHub blob search snippets + Android docs.
- Device-info sites used only to identify PJZ110 as OnePlus 13 / SM8750; not used for latency claims.
