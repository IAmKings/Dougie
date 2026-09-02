# Directory Structure

> How JVM/core code is organized in Dougie.

## Overview

Agent Runtime lives in Gradle `:core:*` modules. These modules use the **Kotlin JVM plugin only** — never `com.android.library`. Android SDK types (`android.*`) are forbidden so `:core:*` can be reused by `:cli` and a future desktop app (`PRD.md` §17.2).

`:cli` is a **JVM application** (`com.dougie.cli`, `application` + Kotlin JVM + Compose compiler for mosaic). No Android plugin. It is **not** in the APK. Direct Gradle dependency is `:core:runtime` only (runtime already pulls `:core:model` / `:core:llm` / `:core:tool`). Mosaic is **0.14.0**, not 0.18.0.

## Directory Layout

```
core/model/src/main/kotlin/com/dougie/core/model/
  LlmVendors.kt
core/llm/src/main/kotlin/com/dougie/core/llm/
core/tool/src/main/kotlin/com/dougie/core/tool/
core/runtime/src/main/kotlin/com/dougie/core/runtime/
  LoopEngine.kt
  IntentRouteAnswers.kt
  TaskManager.kt
  TaskStore.kt
  AuditLog.kt
  EgressGateway.kt
  ToolCallSanitizer.kt
  PolicyEngine.kt
core/memory/src/main/kotlin/com/dougie/core/memory/
  MemoryStore.kt
  MemoryGate.kt
  InMemoryMemoryStore.kt
core/tool/src/main/kotlin/com/dougie/core/tool/
  IdempotencyStore.kt
  CalendarCreateTool.kt
data/memory/src/main/kotlin/com/dougie/data/memory/
  RoomMemoryStore.kt
data/tasks/src/main/kotlin/com/dougie/data/tasks/
  DougieTaskStores.kt
data/preferences/src/main/kotlin/com/dougie/data/preferences/
core/runtime/src/test/kotlin/com/dougie/core/runtime/
core/tool/src/main/kotlin/com/dougie/core/tool/
  AgentTool.kt
  FakeBatteryTool.kt
  SystemTimeTool.kt
  CalendarPort.kt
  CalendarQueryTool.kt
  CalendarCreateTool.kt
  ClipboardPort.kt
  ClipboardReadTool.kt
  ClipboardWriteTool.kt
  AppIntentAllowlist.kt
  OpenAppEntries.kt
  AppIntentPort.kt
  AppIntentTool.kt
  SpeechPort.kt
  SpeechInputTool.kt
  SpeechSession.kt
  SpeechHold / HoldSpeechRecorder (composer hold-to-talk; Tool still uses 3s capture())
  SherpaSpeechEngine.kt
  TtsPort.kt (stop(); PreferOfflineTtsPort.speakFinal offline-only for Chat replies)
  TtsSpeakText.kt (ASCII digits → Chinese numerals before offline speakFinal; Chat still shows original finalAnswer)
  TtsVoices.kt (curated fanchen-C sid 0/14/100: 默认/音色一/音色二; clamp unknown)
  SpeechOutputTool.kt
  SherpaTtsEngine.kt
  IntentPort.kt
  IntentClassifierTool.kt
  IntentJsonParser.kt
  OnnxIntentEngine.kt
  ModelInstaller.kt
  ModelTreeNames.kt
  ModelImporter.kt
  OfficialModelCatalog.kt
  BundledModelSeed.kt
  CharacterErrorRate.kt
  IntentEval.kt
  FullEvalSet.kt
  ScreenFrame.kt
  ScreenCapturePort.kt
  ScreenCaptureTool.kt
  ScreenMatchTool.kt
  TemplateLibrary.kt
  GrayscaleNccMatcher.kt
  ScreenFrameDownscale.kt
core/tool/src/test/resources/eval/
  asr-gold.json
  intent-gold.json
tool/system/src/main/kotlin/com/dougie/tool/system/
  DeviceBatteryTool.kt
  AndroidCalendarPort.kt
  AndroidClipboardPort.kt
  AndroidAppIntentPort.kt
  AndroidScreenCapturePort.kt
  ScreenCaptureService.kt
  ScreenCaptureConsentStore.kt
  AndroidSpeechPort.kt
  AudioRecordSpeechRecorder.kt
  SherpaJni.kt
  AndroidSystemTtsEngine.kt
  AndroidIntentPort.kt
  IntentOrtJni.kt
  OkHttpModelGet.kt
  (trimmed) com/k2fsa/sherpa/onnx/Tts.kt
tool/system/src/main/cpp/
  intent_ort_jni.cpp
  include/onnxruntime_c_api.h
  CMakeLists.txt
tool/accessibility/src/main/kotlin/com/dougie/tool/accessibility/
  DougieAccessibilityService.kt
  GesturePort.kt
  AndroidGesturePort.kt
  HighRiskForeground.kt
  TapSwipeTool.kt
data/preferences/src/main/kotlin/com/dougie/data/preferences/
  PreferenceStore.kt
  ProviderSettings.kt
cli/src/main/kotlin/com/dougie/cli/
  Cli.kt
  FakeBatteryLoop.kt
cli/src/test/kotlin/com/dougie/cli/
  FakeBatteryLoopTest.kt
```

Package root is `com.dougie.*`. One conceptual type family per file (`AgentTask.kt`, `LoopEngine.kt`).

## Module Organization

| Module | Owns | Must not own |
|--------|------|----------------|
| `:core:model` | Data classes, enums, `LlmResponse`, `LlmEvent`, `ToolContext`, `EgressPolicy`, `CloudLlmConfig`, `LlmVendors` | I/O, Android, HTTP |
| `:core:llm` | `LlmProvider.stream`, `FakeLlmProvider`, `OpenAICompatibleProvider` SSE (OkHttp) | Tool execution, UI, policy bypass |
| `:core:tool` | `AgentTool` + JVM tools + `IdempotencyStore` + local `TemplateLibrary` (`solid` fixture + bundled `logo`) + `ModelInstaller` / `ModelImporter` (not AgentTools) | `BatteryManager` / other Android APIs, OpenCV AAR, PNG assets, SAF / `ContentResolver` |
| `:core:runtime` | `LoopEngine`, `TaskManager`, `TaskStore`, `AuditLog`, `EgressGateway.stream`, `ToolCallSanitizer`, `PolicyEngine` | Compose, Android Context, HTTP |
| `:core:memory` | `MemoryStore`, `MemoryGate`, `InMemoryMemoryStore` | Room, Android Context |
| `:tool:system` (Android) | `DeviceBatteryTool`, calendar/clipboard/intent/speech/screen-capture ports, `ScreenCaptureService` (MediaProjection FGS), `SherpaJni` + trimmed `com.k2fsa.sherpa.onnx` JNI bindings, `AndroidSystemTtsEngine`, `AndroidIntentPort`, `IntentOrtJni`, `OkHttpModelGet` | Loop state machine, LLM HTTP, cloud STT/TTS, llama.cpp |
| `:tool:accessibility` (Android, **sideload flavor only**) | `DougieAccessibilityService`, `GesturePort` / `AndroidGesturePort`, `HighRiskForeground`, `TapSwipeTool` (L3 tap/swipe) | Play APK, `:core:tool` |
| `:data:preferences` (Android) | EncryptedSharedPreferences + `allowCloud` default false + `memoryEnabled` default true + `vendorId` / `maxTokens` | Loop / Chat UI |
| `:data:memory` (Android) | SQLite + FTS4 facts (`RoomMemoryStore`) | LoopEngine, Compose |
| `:data:tasks` (Android) | SQLite `agent_tasks` / `idempotency` / `audit_log` | LoopEngine, Compose |
| `:feature:history` (Android) | Task History list UI | LLM HTTP, SQLite helpers |
| `:app` | Wires OkHttp, Gateway, tools, PolicyEngine, PreferenceStore, RoomMemoryStore, DougieTaskStores, recoverInterrupted, `Dispatchers.Default`; sideload `ChannelHooks.seedBundledModels` | Business rules that belong in core |
| `:cli` (JVM application, not in APK) | `com.dougie.cli` Agent Console: kotlinx-cli `--log-only`, mosaic **0.14.0** TTY UI, `FakeLlmProvider` + `FakeBatteryTool` via `:core:runtime` | `com.android.*` plugin, Play/Sideload APK, `:tool:*` / `:data:*`, mosaic **0.18.0** |

New JVM tests for the loop and gateway go in `:core:runtime` `src/test`. Provider HTTP tests go in `:core:llm` `src/test`. CLI snapshot / flag tests go in `:cli` `src/test`.

## Naming Conventions

- Types: `AgentTask`, `TaskStatus`, `LoopEngine`, `PolicyEngine`, `FakeLlmProvider`, `FakeBatteryTool`, `SystemTimeTool`, `DeviceBatteryTool`, `EgressGateway`, `ToolCallSanitizer`
- Tool names in traces are lowercase ids (`battery`, `time`), not class names
- Idempotency key is always `taskId + toolCallId` (`ToolContext.idempotencyKey`)

## Design Decision: Fake script vs real provider

**Context**: Phase 0 needed a deterministic 3-loop proof without a network.

**Decision**: `FakeLlmProvider` (`isLocal = true`) always emits three `battery` ToolCalls then a FinalAnswer. App chat path uses `OpenAICompatibleProvider` only — never silent-fallback to Fake. Keep Fake for JVM tests and `:cli`.

## Design Decision: EgressGateway in `:core:runtime`

**Context**: Phase 1a needs default-deny cloud calls without a new empty `:core:policy` module.

**Decision**: `EgressGateway.stream` / `complete` live in `:core:runtime`. `stream` is `flow { ensureAllowed(); emitAll(provider.stream) }` so deny / missing key throw **before** collect (no HTTP). `complete` collects the same flow. Split `:core:policy` only when checker + decision types grow past ~2 files.

## Design Decision: SSE callbackFlow must not drop deltas

**Context**: OpenAI SSE arrives on OkHttp's thread via `callbackFlow`. Default rendezvous + `trySend` can drop `TextDelta` under backpressure.

**Decision**: Parse SSE off the OkHttp callback, `trySendBlocking` into the channel, then `.buffer(Channel.BUFFERED)`. Cancelled calls must `close()` without mapping to `LLM_FAILED`. `TaskManager.cancel()` cancels the loop job, which cancels the flow (`awaitClose { call.cancel() }`).

## Don't: Speak Anthropic Messages from the Android provider

**Problem**: Settings can point at many OpenAI-compatible hosts. A native Anthropic `/v1/messages` body would break DeepSeek / Groq / Together / 硅基流动.

**Instead**: `OpenAICompatibleProvider` always POSTs `{baseUrl}/chat/completions` with `model`, `stream`, `max_tokens`, `messages`, and `tools`. Vendor presets live in `LlmVendors` (`:core:model`): optional OpenCode Go (`https://opencode.ai/zen/go/v1`, model `deepseek-v4-flash`); official DeepSeek default model is `deepseek-v4-flash`; new-install default remains OpenAI. Body `model` is the configured model id trimmed (`deepseek-v4-flash`, never an `opencode-go/` prefix). DeepSeek V4 / `deepseek-reasoner` keep default thinking (do not send `thinking:disabled`). Their request `max_tokens` is `effectiveMaxTokens` (at least `V4_THINKING_MAX_TOKENS` = 8192) so reasoning does not starve `content`; OpenAI default remains 2048. Empty SSE `tool_calls:[]` must not skip `content`. Go bases (`/zen/go`) send `x-opencode-session: taskId`. Custom vendor keeps the user's URL.

## Don't: Android plugin on `:core:*`

**Problem**: Adding `com.android.library` to runtime/llm/tool/model breaks the JVM-pure red line and `:cli` reuse.

**Instead**: Put `BatteryManager` in `:tool:system` and inject `AgentTool` into `LoopEngine`. Store API keys in `:data:preferences` via EncryptedSharedPreferences (MasterKey), not plaintext XML prefs.

## Don't: Android plugin or APK membership for `:cli`

**Problem**: `com.android.application` / `com.android.library` on `:cli` would pull Android SDK into the console and could ship mosaic/JLine in the phone APK (`PRD.md` §17.3).

**Instead**: `:cli` uses Kotlin JVM + `application` (`mainClass` `com.dougie.cli.CliKt`). `include(":cli")` in `settings.gradle.kts` only. `:app` must not `implementation(project(":cli"))`. Direct module dependency is `project(":core:runtime")` only.

## Design Decision: Mosaic 0.14.0, not 0.18.0

**Context**: JakeWharton mosaic 0.18.0 is compiled with Kotlin 2.2 metadata. This repo is Kotlin **2.0.21**. 0.18 also added `NonInteractivePolicy`; 0.14 has no such API.

**Decision**: Pin `libs.versions.toml` `mosaic = "0.14.0"` (newest mosaic built for Kotlin 2.0.x). Parse `--log-only` with kotlinx-cli. When mosaic TTY/raw-mode setup throws, print the same `formatSnapshot` lines as `--log-only`. Do not bump mosaic to 0.18 until the repo Kotlin version can read 2.2 metadata.

## Don't: Persist retrieval questions as facts

**Problem**: `MemoryGate` markers include `我住`. User input `我住在哪里` is a question, not a durable fact, but `contains("我住")` still stored it.

**Instead**: `looksLikeDurableFact` returns false when the text looks like a question (`？`/`?`, or `哪里`/`哪儿`/`什么`/`吗`/`呢`). Keep storing `我叫小明，我住上海`.

## Don't: stopForeground MediaProjection from a worker thread

**Problem**: After `screen_capture` returned `capture_id`/`width`/`height`, ColorOS (PJZ110 / API 36) killed the process. `ScreenCaptureService` ran `stopForeground`/`stopSelf` and `MediaProjection.stop()` on a background thread.

**Instead**: `startForeground` with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` on the service thread before `getMediaProjection`. Release `VirtualDisplay` / `ImageReader` / `projection.stop()` on the capture `HandlerThread`, then `quitSafely` on that same thread. Post `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()` to the main looper only after that teardown is posted. Cap capture width at 720. Tool JSON still has no pixels.

Consent is one-shot: after `projection.stop()`, `ScreenCaptureConsentStore.clear()`; the next `screen_capture` must re-prompt MediaProjection. Background calls fail with `应用不在前台，无法截取屏幕。` before starting the service. PJZ110 / API 36 verified 2026-08-18: capture 720×1584 + match, deny path, background gate, no process kill.

## Design Decision: Local screen-match catalog is JVM pixels, not OpenCV

**Context**: PRD §6.7 requires a local template library for `screen_match`. Phase 3b deferred OpenCV AAR and a productized template catalog, not “no catalog at all”.

**Decision**: `TemplateLibrary` in `:core:tool` is the catalog: `solid` (8×8 all-white NCC fixture) plus a generated bundled `logo` (24×24 high-contrast D, non-uniform grayscale). `GrayscaleNccMatcher` stays pure JVM. `ScreenMatchTool` downscales captures wider than ~320px before NCC (and the template by the same scale), then maps `x`/`y` back to original capture pixels. Gray bytes stay in process-local `ScreenFrameStore`; tool JSON is `{template_id, found, x, y, confidence}` and is UNTRUSTED_DATA. Do not add OpenCV, `android.*` in `:core:*`, PNG assets `:core:tool` cannot load, or log gray bytes.

## Don't: Put screenshot pixels in the LLM Prompt when Chat attaches a frame

**Problem**: Multi-modal Chat attach could dump grayscale/`data:image` into `task.input` or OpenAI `messages`.

**Instead**: `:app` `ChatAttachmentSession` holds up to 4 mixed items. Screenshots go to `ScreenFrameStore` (gray only); gallery/camera JPEG stays in the session. `AgentTask.attachments` is id/kind/WxH only (codec omits pixels). `OpenAICompatibleProvider` always puts attachment metadata in the system note. Vision `image_url` JPEG parts are added only for GALLERY/CAMERA when `allowCloud()` is true; SCREEN never leaves the device. While pinned (`pinId` of the last SCREEN), `ScreenCaptureTool` returns that metadata and does not recapture; `put` of a different id is ignored. `TaskManager` `clearPin()` + `onTaskFinished` (session `releaseAfterTask`) in `finally`. Chat chips are `屏幕|相册|拍照 · 宽×高`; in-app fullscreen preview is local only. Send consumes the composer list without uploading screenshots. Chat-button capture still requires foreground. Sideload overlay tap may call `pinCurrentScreen(requireForeground = false)` then open Chat with `EXTRA_APPLY_PINNED_SCREEN` (boolean only); a 5th overlay attach is `最多附上 4 张`. LLM `screen_capture` keeps the foreground gate.

## Don't: Put `TapSwipeTool` in `:core:tool`

**Problem**: `:core:tool` is on the Play classpath. A TapSwipe class there would ship in the Play APK even if unregistered.

**Instead**: Keep `TapSwipeTool` and `AccessibilityService` in `:tool:accessibility`, wired only with `sideloadImplementation`. Play `ChannelTools` must not import those types. `PolicyEngine` treats `RiskLevel.L3` as always `NeedsConfirmation`. Sideload `TapSwipeTool` dispatches via `GesturePort` (`dispatchGesture`); refuse bank/payment/password-manager foreground packages in `HighRiskForeground` before any gesture. Overlay is the same split: `DougieOverlayService` / `SYSTEM_ALERT_WINDOW` live only under `app/src/sideload/` (not `:feature:settings`, not play). Play uses `NotificationCompat.BubbleMetadata` on the existing task notice when `!BuildConfig.IS_SIDELOAD` and API ≥ 29.

## Don't: Cloud STT or commit 230MB ASR models

**Problem**: System `SpeechRecognizer` / online engines can egress audio. Checking in Paraformer int8 (~230MB) blows git and Play APK size.

**Instead**: `SpeechInputTool` in `:core:tool` talks to `SpeechPort`. `SpeechSession` records only after gates pass, then `SherpaSpeechEngine.transcribe`. `AndroidSpeechPort` uses `filesDir/models/asr/{model.int8.onnx,tokens.txt}` and `SherpaJni.isAvailable()` (`System.loadLibrary("onnxruntime")` if present, then `sherpa-onnx-jni`). Do not class-load `OfflineRecognizer` until the library loads. Models stay out of git. `:tool:system` `fetchSherpaJni` downloads the v1.13.4 **shared** Android tarball (`sherpa-onnx-v1.13.4-android.tar.bz2`, not the static-link archive) into `build/sherpa-jni/jniLibs/arm64-v8a/` (not committed) so `libsherpa-onnx-jni.so` and `libonnxruntime.so` are the same ORT build. Sideload may ship ASR/TTS under gitignored `app/src/sideload/assets/models/{asr,tts}/`; `BundledModelSeed` copies to `filesDir` when layout is missing. Play sourceSets must not include those assets. `checkChannelLeak` fails if the play Debug APK zip contains `models/asr`, `models/tts`, or `*.onnx`. Intent ONNX is never bundled in the Play APK. Trimmed JNI bindings are Apache-2.0 from sherpa-onnx v1.13.4. `SherpaJni` keeps the recognizer/TTS session and tries `nnapi` then `xnnpack` then `cpu` (2–4 threads). Do not force `provider="cpu"` / `numThreads=1`.

## Don't: Online TTS or commit VITS ONNX

**Problem**: System voices with `isNetworkConnectionRequired` egress text. Checking in VITS (~116MB) belongs in a later slice, not this contract.

**Instead**: `SpeechOutputTool` talks to `PreferOfflineTtsPort`. If offline `TtsEngine.isReady()`, speak offline only. Else system TTS via `AndroidSystemTtsEngine`, max 80 chars, reject network voices. App default offline is `SherpaTtsEngine` on `filesDir/models/tts/{model.onnx,tokens.txt,lexicon.txt}` plus `SherpaJni.isAvailable()`. Do not class-load `OfflineTts` until the library loads (no companion `loadLibrary`). Trimmed `Tts.kt` is Apache-2.0 from sherpa-onnx v1.13.4. VITS ONNX stays out of git. Success JSON is `ok` + `backend` only.

## Don't: Commit GGUF or silent-cloud intent

**Problem**: Qwen3-0.6B GGUF is 420–639MB and misses P95. Falling back to the cloud LLM hides that the local classifier is missing.

**Instead**: `IntentClassifierTool` talks to `IntentPort`. Official layout is `filesDir/models/intent/{model.onnx,tokenizer.json,labels.txt,vocab.txt}` (`IntentModelLayout.isPresent`). Hashbag testdata is the old three files (`isHashbagFixturePresent`). Missing or engine not ready → `INTENT_MODEL_MISSING` / `INTENT_ENGINE_NOT_READY`. `OnnxIntentEngine` switches on `tokenizer.json` `algorithm`: `bert_wordpiece` tokenizes on JVM and JNI int64 `input_ids`/`attention_mask` (optional `token_type_ids`); `char_ngram_fnv1a32_hash_bag` keeps float features. Softmax + argmax vs `labels.txt`. Blank/failed infer is `INTENT_FAILED`. Low confidence still returns a hit (tool maps to `INTENT_LOW_CONFIDENCE`). Do not call `EgressGateway` from this tool. `*.gguf` and `third_party/llama.cpp/` stay out of git. `:tool:system` reuses that one `libonnxruntime.so` (`dougie_intent` `DT_NEEDED`, `OrtGetApiBase`; do not `dlopen` across Android linker namespaces). The static-link sherpa tarball hides ORT and must not be used. No second ORT AAR, no llama.cpp. Tiny ONNX testdata may live under `core/tool/src/test/resources/intent-pack/`. Catalog MiniRBT weights are GitHub Release `intent-minirbt-v1`, not testdata. Intent ONNX is never in the Play APK. Never log classifier text or features.


## Don't: Commit full ASR eval dumps

**Problem**: Rule D wants ≥500 wav clips and CER ≤ 5%. Checking in audio, ONNX, or GGUF blows git and CI.

**Instead**: JVM `CharacterErrorRate` + `IntentEval` run on tiny text gold under `core/tool/src/test/resources/eval/`. Repo-root `eval/` (e.g. `eval/asr/*.wav`) is gitignored; `FullEvalSet.isPresent()` skips when missing. Do not call sherpa or ORT from this path. Fixture `passed` is not a claim that the 500-clip set is done.

## Don't: AgentTool with attacker-controlled download URL

**Problem**: Letting the cloud LLM pick a URL would fetch arbitrary payloads into `filesDir`.

**Instead**: `ModelInstaller` is app-owned HTTPS download into a cache dir. `ModelImporter` copies hashed sources into `filesDir` (JNI cache). Neither talks to SAF / `DocumentFile`. Both require SHA-256 to match `OfficialModelCatalog` specs (`SHA256.matches` + file hash), safe names / `relativeDir` / canonical, write `.part` then rename, delete `.part` on failure. Importer matches sources to specs by lowercase content hash (one source may fill multiple specs that share a hash; extra unmatched hashes or missing specs fail). `:app` `ExternalModelTreeImpl` streams tree files to temps for scan, and streams cache layout files onto a **reused** `{tree}/models/{asr,tts,intent}` after a confirmed download (`ModelTreeNames` treats SAF uniquified `models (1)` / `models(2)` as the same folder; never `createDirectory` when a match exists; `listFiles` not `findFile`). No tree / lost persistable permission → UI must not fetch. Not registered on `LoopEngine`. Intent pack is `model.onnx` + `tokenizer.json` + `labels.txt` (historical `model.gguf` must not mark installed). Rethrow `CancellationException` (do not map to `MODEL_DOWNLOAD_FAILED`); `OkHttpModelGet` cancels the Call and `ensureActive()` while copying; rejects non-https redirects. HTTPS/SHA-256 defaults live in `OfficialModelCatalog` (HuggingFace Paraformer / vits-fanchen-C / GitHub raw `IAmKings/Dougie` `master` testdata `core/tool/src/test/resources/intent-pack/` for intent `model.onnx` + `tokenizer.json` + `labels.txt`). A selected tree with matching hashes may still sync without HTTP. gitignored `local.properties` keys `dougie.model.*` → `BuildConfig` override those defaults when non-blank (`dougie.model.intent.url` / `tokenizer.url` / `labels.url` plus matching sha256 keys). Invalid override URL/SHA → offer not configured; UI must not fetch.

## Scenario: `:cli` fake battery console

### 1. Scope / Trigger
New Gradle command / process entry: `com.dougie.cli.CliKt` + kotlinx-cli `--log-only`. Cross-layer: CLI hosts `TaskManager` / `LoopEngine` from `:core:runtime` with Fake LLM + Fake battery only.

### 2. Signatures
- `fun main(args: Array<String>)` — `application.mainClass` `com.dougie.cli.CliKt`
- `parseLogOnly(args): Boolean` — `ArgParser("dougie-cli")`, boolean option `fullName = "log-only"`, default `false` (flag needs no value)
- `fakeBatteryManager(dispatcher, scope, stepDelayMs): TaskManager` — `FakeLlmProvider()`, `tools = mapOf("battery" to FakeBatteryTool())`
- `formatSnapshot(task: AgentTask?): String`

### 3. Contracts
- Prompt is fixed `FAKE_BATTERY_PROMPT` (`我现在手机还有多少电？`). No cloud provider, no API key, no Android ports.
- `--log-only`: print each `formatSnapshot` line to stdout; no mosaic TTY.
- Default (no flag): `runMosaic { MosaicConsole }`. Mosaic 0.14 has no `NonInteractivePolicy`; on TTY/raw-mode failure, print the collected snapshot lines instead.
- Snapshot line: `taskId=… status=… loop=… tools=<last 3 traces as name:status;…> end=<finalAnswer or lastError>`. Idle: `status=IDLE`.
- Process exit `1` + snapshot on stderr if terminal status is not `COMPLETED`.
- Stdout must not contain `resultJson`, `battery_percent`, the prompt, or tool `argsSummary`.

### 4. Validation & Error Matrix
- `--log-only` present → log mode (`true`); omitted → mosaic-first
- Unknown kotlinx-cli flags → parser error (process does not start the loop)
- Terminal `FAILED` → stderr snapshot, exit 1
- Mosaic setup exception (not `CancellationException`) → fallback to printed snapshots

### 5. Good/Base/Bad Cases
- Good: `--log-only` completes with `status=COMPLETED`, `loop=3`, three `battery:SUCCESS`
- Base: empty args tries mosaic, then the same snapshot contract if TTY fails
- Bad: mosaic 0.18.0 on Kotlin 2.0.21; `:cli` in `:app`; logging `resultJson` / percent / prompt

### 6. Tests Required
- `FakeBatteryLoopTest.fakeLoopCompletesThreeBatteryTools` — COMPLETED, 3 SUCCESS battery traces, `loop=3`; snapshot has `status=COMPLETED` / `battery:SUCCESS` / `loop=3` and not `resultJson` / `battery_percent` / prompt / args
- `FakeBatteryLoopTest.logOnlyFlagNeedsNoValue` — `--log-only` true, empty args false
- `./gradlew :cli:test` and `./gradlew :cli:run --args='--log-only'` (JDK 17)

### 7. Wrong vs Correct
#### Wrong
```kotlin
implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
plugins { id("com.android.library") }
```
#### Correct
```kotlin
// libs.versions.toml: mosaic = "0.14.0"  // 0.18.0 needs Kotlin 2.2 metadata
plugins { alias(libs.plugins.kotlin.jvm); application }
dependencies { implementation(project(":core:runtime")) }
```

## Scenario: speech_output TTS contract

### 1. Scope / Trigger
Cross-layer: `:core:tool` `SpeechOutputTool` + `PreferOfflineTtsPort` + `SherpaTtsEngine`; `:tool:system` `AndroidSystemTtsEngine` + `SherpaJni.speak` + `AudioTrack`.

### 2. Signatures
- `TtsEngine.isReady(): Boolean` / `suspend fun speak(text: String): TtsOutcome`
- `SherpaTtsEngine(modelDir, nativeAvailable, speakNative)`
- `SherpaJni.generatePcm(modelDir, text)` — PCM only, no `AudioTrack`; `speak` still plays
- `TtsModelLayout.isPresent`: `model.onnx` + `tokens.txt` + `lexicon.txt` non-empty
- `PreferOfflineTtsPort.speak(text): TtsSpeakResult` (`ok`, `backend` `offline`|`system`, optional `error`)
- `SpeechOutputTool` name `speech_output`, `RiskLevel.L0`, required `text`

### 3. Contracts
- Offline ready → never call system TTS.
- Offline unready → system TTS only if `text.length <= 80`.
- `Voice.isNetworkConnectionRequired` → fail, no speak. Check **after** `setLanguage` (language switch can pick a network voice).
- Target 30+: `:tool:system` merged manifest must `<queries>` `android.intent.action.TTS_SERVICE` so engines/voices are visible.
- Success JSON `{"ok":true,"backend":"offline"|"system"}` — no PCM/bytes.
- Do not commit VITS ONNX. Layout is `filesDir/models/tts/`. Optional `dict/` passed as `dictDir`.

### 4. Validation & Error Matrix
- Empty/blank `text` → `INVALID_TOOL_ARGS`
- Fallback length > 80 → `TTS_TOO_LONG`
- Network voice → `TTS_NETWORK`
- Engine fail → `TTS_FAILED`

### 5. Good / Base / Bad
- Good: offline ready, short text, `backend=offline`
- Base: offline unready, short text, `backend=system`
- Bad: 81-char fallback; network voice; empty text

### 6. Tests Required
- `SpeechOutputToolTest`: prefers offline; fallback; long fallback; network; empty text; VITS layout + native gate
- `./gradlew :core:tool:test :app:checkChannelLeak`

### 7. Wrong vs Correct
- Wrong: `TextToSpeech` with a network voice, checking the voice only before `setLanguage`, or logging utterance text
- Correct: reject `isNetworkConnectionRequired` on the voice actually used; AuditLog stores tool name/outcome only

## Scenario: Chat final-answer host TTS

### 1. Scope / Trigger
Cross-layer: `AgentTask.speakReply` on `:core:model` / `TaskManager.submit` / `TaskSnapshotCodec`; host `speakFinal` in `:app` + `:tool:system`; Chat gets `speakingReply` + `onStopReply` + bubble `onSpeakReply`.

### 2. Signatures
- `AgentTask.speakReply: Boolean = false`
- `TaskManager.submit(..., speakReply: Boolean = false)`
- `PreferOfflineTtsPort.speakFinal(text): TtsSpeakResult` — offline only; engine hears `TtsSpeakText.forOffline(text)`
- `TtsSpeakText.forOffline(text): String`
- `TtsEngine.stop()` / `SherpaJni.stopSpeak()`

### 3. Contracts
- `speakReply` true only after a successful hold-to-talk append in this compose session; retry copies the flag.
- After `COMPLETED`, host speaks `finalAnswer` once per `taskId`. Typed send does not autoplay.
- Last completed Agent bubble: **播报** only if `ttsReady`; replays via `speakFinal`. While `speakingReply`, that control is **停止播报**. FAILED last bubble keeps **重试**, no 播报.
- Official replies never use system TTS. Unready/fail → `TTS_REPLY_UNAVAILABLE`; task stays `COMPLETED`.
- VITS lexicon skips ASCII `0-9`; `speakFinal` expands digits (year digit-by-digit, 1–2 digit 十/二十, `HH:MM` → 点). Bubble text stays original. Do not log the expanded utterance.
- `speech_output` success JSON unchanged (`ok` + `backend`).
- Codec: missing `speakReply` → false.

### 4. Validation & Error Matrix
- Offline unready / speak fail (not user stop) → attachment line `语音回复暂不可用`
- User stop / `onStop` / new send → silent stop, no unavailable banner
- Empty `finalAnswer` or `speakReply=false` → no playback

### 5. Good / Base / Bad
- Good: voice append then send → autoplay offline
- Base: typed send → silence
- Bad: `speakFinal` falling through to system TTS; treating stop as `TTS_FAILED`; sending ASCII digits straight to VITS
- Good replay: last COMPLETED bubble **播报** → `speakFinal` again

### 6. Tests Required
- `ReplyPlaybackTest` / `TaskStoreTest` speakReply round-trip; `SpeechOutputToolTest` still prefers offline for the Tool; `TtsSpeakTextTest` date/time digits
- `ChatUiStateTest` bubble 播报 vs 重试; voice overlay copy unchanged; composer stop is callback-driven
- `./gradlew :core:runtime:test :core:tool:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak`

### 7. Wrong vs Correct
- Wrong: put `speakingReply` on `ChatUiState`/`TaskStatus`; log `finalAnswer`; `PreferOfflineTtsPort.speak` for host replies; mutate bubble text to Chinese numerals
- Correct: Activity flag + `speakFinal` + `TtsSpeakText`; Stop does not fail the task

## Scenario: LoopEngine status contract

### 1. Scope / Trigger
Cross-layer: JVM loop emits `AgentTask` snapshots; Chat maps them to bubbles.

### 2. Signatures
- `LoopEngine.run(initial: AgentTask, emit: suspend (AgentTask) -> Unit): AgentTask`
- `LoopEngine(..., intentPort: IntentPort? = null)` — App wires `AndroidIntentPort`; CLI/tests omit
- `TaskManager.submit(input: String)` / `TaskManager.cancel()` / `TaskManager.task: StateFlow<AgentTask?>`
- `EgressGateway.stream(provider, context): Flow<LlmEvent>` / `complete(...): LlmResponse`
- `ToolCallSanitizer.sanitize(name, rawArgsJson): String` before `AgentTool.execute`

### 3. Contracts
- `emit` is called on the injected dispatcher, never Main.
- Status sequence for a 3-tool Fake run: `PREPARING` then (`THINKING` → `TOOL_PENDING` → `TOOL_EXECUTING` → `TOOL_RESULT`) ×3 then `THINKING` → `COMPLETED`.
- `loopCount` increments after each successful tool result (Fake complete ⇒ `loopCount == 3`).
- `ToolTraceEntry.toolCallId` is unique per task; `idempotencyKey == taskId + toolCallId`.
- LLM timeout default 60s, tool timeout default 15s → `FAILED` + `UserFacingErrors.LLM_TIMEOUT` / `TOOL_TIMEOUT`.
- Cloud provider + `allowCloud=false` → `FAILED` + `UserFacingErrors.EGRESS_BLOCKED`; provider `stream`/`generate` is not invoked.
- `TextDelta` snapshots set `AgentTask.streamingText` while `THINKING`; `COMPLETED.finalAnswer` is the joined trimmed text and `streamingText` is cleared.
- Blank / whitespace-only final (no tool_call) → `FAILED` / `UserFacingErrors.LLM_EMPTY_REPLY`, not `COMPLETED` with empty `finalAnswer`.
- `TaskManager.cancel()` → `FAILED` + `UserFacingErrors.CANCELLED`; in-flight HTTP/SSE is cancelled.
- Optional `intentPort`: pack+engine ready, no attachments / `attachedCaptureId`, `confidence >= 0.5`, intent `query_time` / `query_battery` / `query_calendar` / `clipboard_read` / `query_location` / `screen_capture` → Policy + matching tool `{}` → Chinese template `finalAnswer` → `COMPLETED`; **no** `LlmProvider.stream`. High-confidence `screen_capture` is L1 (`screen_capture` `{}`); success template is `已截取屏幕。` (needs `capture_id`/width/height; does not pin Chat composer attachments). Not-foreground / missing MediaProjection Halt does not fall through. High-confidence `create_calendar` / `clipboard_write` take the same path only when `IntentRouteAnswers.parseShortcutArgs` can build sanitizer-valid JSON (clock + title; quoted clipboard text); otherwise fall through to the LLM loop like low confidence. High-confidence `open_app` needs a user-list alias exact match after stripping 打开/帮我打开/请打开 → `app_intent` `package:`; L2 ConfirmCard; missing alias → LLM. `package:` and non-empty `package` args must be on that list for shortcut **and** LLM (empty list denies all package launches). L2 still uses ConfirmCard; reject / missing `WRITE_CALENDAR` Halt does not fall through. L1 permission deny or clipboard-not-foreground Halt does not fall through to LLM. Classify tries `input` then a punctuation/particle-stripped form (Chat example `现在几点了？` → `现在几点`). Trace is only that tool (not `intent_classifier`). Other labels (`speech_input` / `unknown`), low confidence, classify throw, missing pack/engine → existing LLM loop; `IntentHit` never goes on `LoopContext` / Prompt / Audit. Settings probe success allows low confidence and is not a shortcut guarantee.

### 4. Validation & Error Matrix
- Unknown tool name → `FAILED`, `lastError` set, no further LLM calls
- `result.isFatal` → `FAILED`
- `loopCount >= maxLoops` without FinalAnswer → `FAILED` / `MaxLoopExceeded`
- Blank final text with no tool_call → `FAILED` / `LLM_EMPTY_REPLY`
- Empty trimmed input → `TaskManager` no-op
- New submit while status is not COMPLETED/FAILED → ignored (no overlapping loops), including `AWAITING_CONFIRMATION`
- Missing intent pack or engine not ready → do not call `classify`; LLM loop unchanged
- SCREEN / gallery attachments or `attachedCaptureId` → no shortcut (may skip `classify`)
- Missing Android permission → `FAILED` / `PERMISSION_DENIED`, no `AgentTool.execute`
- L2 tool → `AWAITING_CONFIRMATION` then `TaskManager.confirm()` executes once; `reject()` or 60s timeout → `FAILED` / `CONFIRM_REJECTED`, no execute
- `allowCloud=false` and `isLocal=false` → `EgressBlockedException` (even if API key is set)
- `allowCloud=true` and blank API key → `MissingApiKeyException` (no HTTP)
- Unknown / unregistered tool → sanitizer throws `UNKNOWN_TOOL` (no execute)
- Extra keys on empty schemas (`time`, `battery`) are stripped to `{}`

### 5. Good/Base/Bad Cases
- Good: same input three times, each run 3 SUCCESS battery traces + FinalAnswer containing `63`
- Base: `stepDelayMs = 0` in tests
- Bad: Chat calling `BatteryManager` directly; app chat injecting Fake when cloud is blocked

### 6. Tests Required
- `LoopEngineTest.fakeTaskCompletesAfterExactlyThreeToolLoops`
- `LoopEngineTest.statusSequenceIsPreparingThinkingToolCycleTimesThreeThenCompleted` (distinct consecutive statuses)
- `LoopEngineTest.cloudProviderBlockedWhenAllowCloudFalse`
- `LoopEngineTest.llmTimeoutFailsTaskWithReadableError`
- `LoopEngineTest.toolTimeoutFailsTaskWithReadableError`
- `EgressGatewayTest.denyDoesNotInvokeCloudProvider`
- `EgressGatewayTest.denyNeverSendsHttpEvenWhenApiKeyIsConfigured`
- `EgressGatewayTest.streamDenyNeverSendsHttpEvenWhenApiKeyIsConfigured`
- `OpenAICompatibleProviderTest.parsesToolCallThenFinalContentAcrossTwoGenerateCalls`
- `OpenAICompatibleProviderTest.streamsTextDeltasIntoFinalAnswer`
- `OpenAICompatibleProviderTest.assemblesStreamedToolCallArguments`
- `ToolCallSanitizerTest.coercesNumericStringForTypedField`
- `LoopEngineTest.emptyFinalAfterTimeToolFailsInsteadOfSilentComplete`
- `LoopEngineTest.unknownToolFailsWithoutExecuting`
- `LoopEngineTest.canCallTimeThenBatteryInOneTask`
- `LoopEngineTest.cancelStopsInFlightStreamAndFailsTask`
- `LoopEngineTest.highConfidenceQueryTimeSkipsLlm` (LLM stream count 0; `finalAnswer` starts with `现在是`)
- `LoopEngineTest.timeExampleWithLeAndQuestionMarkStillSkipsLlm`
- `LoopEngineTest.highConfidenceQueryBatterySkipsLlm` (template + Audit `battery`/`SUCCESS` only)
- `LoopEngineTest.missingIntentPackDoesNotClassifyAndUsesLlm`
- `LoopEngineTest.screenAttachmentSkipsShortcut`
- `LoopEngineTest.attachedCaptureIdSkipsShortcut`
- `LoopEngineTest.unknownAndSlotlessCreateCalendarUseLlm`
- `LoopEngineTest.highConfidenceScreenCaptureSkipsLlm`
- `LoopEngineTest.screenCaptureNotForegroundDoesNotCallLlm`
- `LoopEngineTest.screenCaptureWithoutConsentDoesNotCallLlm`
- `LoopEngineTest.highConfidenceCreateCalendarWithSlotsSkipsLlm`
- `LoopEngineTest.createCalendarRejectDoesNotCallLlm`
- `LoopEngineTest.createCalendarDeniedDoesNotCallLlm`
- `LoopEngineTest.highConfidenceClipboardWriteSkipsLlm`
- `LoopEngineTest.clipboardWriteWithoutQuoteUsesLlm`
- `LoopEngineTest.highConfidenceOpenAppWithAliasSkipsLlm`
- `LoopEngineTest.openAppWithoutAliasUsesLlm`
- `LoopEngineTest.openAppWithExtraWordsUsesLlm`
- `LoopEngineTest.llmPackageNotOnListFailsBeforeLaunch`

### 7. Wrong vs Correct
#### Wrong
```kotlin
// L2 write shortcut with empty JSON (Sanitizer fails required title/startIso/text)
argsJson = "{}"
```
#### Correct
```kotlin
val argsJson = IntentRouteAnswers.classifyTexts(start.input)
    .firstNotNullOfOrNull { IntentRouteAnswers.parseShortcutArgs(toolName, it) }
    ?: return null // missing slots → existing LLM loop
```

#### Wrong
```kotlin
// feature/chat calling Android battery APIs
val pct = context.getSystemService(BatteryManager::class.java).getIntProperty(...)
```
#### Correct
```kotlin
val engine = LoopEngine(
    llm = provider,
                tools = mapOf(
                    "battery" to DeviceBatteryTool(appContext),
                    "time" to SystemTimeTool(),
                ),
    dispatcher = Dispatchers.Default,
    gateway = EgressGateway(policy = { EgressPolicy(allowCloud = prefs.allowCloud) }, apiKey = { prefs.apiKey }),
    intentPort = intentPort,
)
```
