# Directory Structure

> How JVM/core code is organized in Dougie.

## Overview

Agent Runtime lives in Gradle `:core:*` modules. These modules use the **Kotlin JVM plugin only** — never `com.android.library`. Android SDK types (`android.*`) are forbidden so `:core:*` can be reused by `:cli` and a future desktop app (`PRD.md` §17.2).

## Directory Layout

```
core/model/src/main/kotlin/com/dougie/core/model/
core/llm/src/main/kotlin/com/dougie/core/llm/
core/tool/src/main/kotlin/com/dougie/core/tool/
core/runtime/src/main/kotlin/com/dougie/core/runtime/
  LoopEngine.kt
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
  AppIntentPort.kt
  AppIntentTool.kt
  SpeechPort.kt
  SpeechInputTool.kt
  SpeechSession.kt
  SherpaSpeechEngine.kt
  TtsPort.kt
  SpeechOutputTool.kt
  SherpaTtsEngine.kt
  IntentPort.kt
  IntentClassifierTool.kt
  IntentJsonParser.kt
  LlamaIntentEngine.kt
  ModelInstaller.kt
  OfficialModelCatalog.kt
  BundledModelSeed.kt
tool/system/src/main/kotlin/com/dougie/tool/system/
  DeviceBatteryTool.kt
  AndroidCalendarPort.kt
  AndroidClipboardPort.kt
  AndroidAppIntentPort.kt
  AndroidSpeechPort.kt
  AudioRecordSpeechRecorder.kt
  SherpaJni.kt
  AndroidSystemTtsEngine.kt
  AndroidIntentPort.kt
  LlamaJni.kt
  OkHttpModelGet.kt
  (trimmed) com/k2fsa/sherpa/onnx/Tts.kt
tool/system/src/main/cpp/
  llama_jni.cpp
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
```

Package root is `com.dougie.*`. One conceptual type family per file (`AgentTask.kt`, `LoopEngine.kt`).

## Module Organization

| Module | Owns | Must not own |
|--------|------|----------------|
| `:core:model` | Data classes, enums, `LlmResponse`, `LlmEvent`, `ToolContext`, `EgressPolicy`, `UserFacingErrors` | I/O, Android, HTTP |
| `:core:llm` | `LlmProvider.stream`, `FakeLlmProvider`, `OpenAICompatibleProvider` SSE (OkHttp) | Tool execution, UI, policy bypass |
| `:core:tool` | `AgentTool` + JVM tools + `IdempotencyStore` | `BatteryManager` / other Android APIs |
| `:core:runtime` | `LoopEngine`, `TaskManager`, `TaskStore`, `AuditLog`, `EgressGateway.stream`, `ToolCallSanitizer`, `PolicyEngine` | Compose, Android Context, HTTP |
| `:core:memory` | `MemoryStore`, `MemoryGate`, `InMemoryMemoryStore` | Room, Android Context |
| `:tool:system` (Android) | `DeviceBatteryTool`, calendar/clipboard/intent/speech ports, `SherpaJni` + trimmed `com.k2fsa.sherpa.onnx` JNI bindings, `AndroidSystemTtsEngine`, `AndroidIntentPort`, `OkHttpModelGet` | Loop state machine, LLM HTTP, cloud STT/TTS |
| `:tool:accessibility` (Android, **sideload flavor only**) | `DougieAccessibilityService`, `GesturePort` / `AndroidGesturePort`, `HighRiskForeground`, `TapSwipeTool` (L3 tap/swipe) | Play APK, `:core:tool` |
| `:data:preferences` (Android) | EncryptedSharedPreferences + `allowCloud` default false + `memoryEnabled` default true | Loop / Chat UI |
| `:data:memory` (Android) | SQLite + FTS4 facts (`RoomMemoryStore`) | LoopEngine, Compose |
| `:data:tasks` (Android) | SQLite `agent_tasks` / `idempotency` / `audit_log` | LoopEngine, Compose |
| `:feature:history` (Android) | Task History list UI | LLM HTTP, SQLite helpers |
| `:app` | Wires OkHttp, Gateway, tools, PolicyEngine, PreferenceStore, RoomMemoryStore, DougieTaskStores, recoverInterrupted, `Dispatchers.Default`; sideload `ChannelHooks.seedBundledModels` | Business rules that belong in core |

New JVM tests for the loop and gateway go in `:core:runtime` `src/test`. Provider HTTP tests go in `:core:llm` `src/test`.

## Naming Conventions

- Types: `AgentTask`, `TaskStatus`, `LoopEngine`, `PolicyEngine`, `FakeLlmProvider`, `FakeBatteryTool`, `SystemTimeTool`, `DeviceBatteryTool`, `EgressGateway`, `ToolCallSanitizer`
- Tool names in traces are lowercase ids (`battery`, `time`), not class names
- Idempotency key is always `taskId + toolCallId` (`ToolContext.idempotencyKey`)

## Design Decision: Fake script vs real provider

**Context**: Phase 0 needed a deterministic 3-loop proof without a network.

**Decision**: `FakeLlmProvider` (`isLocal = true`) always emits three `battery` ToolCalls then a FinalAnswer. App chat path uses `OpenAICompatibleProvider` only — never silent-fallback to Fake. Keep Fake for JVM tests.

## Design Decision: EgressGateway in `:core:runtime`

**Context**: Phase 1a needs default-deny cloud calls without a new empty `:core:policy` module.

**Decision**: `EgressGateway.stream` / `complete` live in `:core:runtime`. `stream` is `flow { ensureAllowed(); emitAll(provider.stream) }` so deny / missing key throw **before** collect (no HTTP). `complete` collects the same flow. Split `:core:policy` only when checker + decision types grow past ~2 files.

## Design Decision: SSE callbackFlow must not drop deltas

**Context**: OpenAI SSE arrives on OkHttp's thread via `callbackFlow`. Default rendezvous + `trySend` can drop `TextDelta` under backpressure.

**Decision**: Parse SSE off the OkHttp callback, `trySendBlocking` into the channel, then `.buffer(Channel.BUFFERED)`. Cancelled calls must `close()` without mapping to `LLM_FAILED`. `TaskManager.cancel()` cancels the loop job, which cancels the flow (`awaitClose { call.cancel() }`).

## Don't: Android plugin on `:core:*`

**Problem**: Adding `com.android.library` to runtime/llm/tool/model breaks the JVM-pure red line and `:cli` reuse.

**Instead**: Put `BatteryManager` in `:tool:system` and inject `AgentTool` into `LoopEngine`. Store API keys in `:data:preferences` via EncryptedSharedPreferences (MasterKey), not plaintext XML prefs.

## Don't: Put `TapSwipeTool` in `:core:tool`

**Problem**: `:core:tool` is on the Play classpath. A TapSwipe class there would ship in the Play APK even if unregistered.

**Instead**: Keep `TapSwipeTool` and `AccessibilityService` in `:tool:accessibility`, wired only with `sideloadImplementation`. Play `ChannelTools` must not import those types. `PolicyEngine` treats `RiskLevel.L3` as always `NeedsConfirmation`. Sideload `TapSwipeTool` dispatches via `GesturePort` (`dispatchGesture`); refuse bank/payment/password-manager foreground packages in `HighRiskForeground` before any gesture.

## Don't: Cloud STT or commit 230MB ASR models

**Problem**: System `SpeechRecognizer` / online engines can egress audio. Checking in Paraformer int8 (~230MB) blows git and Play APK size.

**Instead**: `SpeechInputTool` in `:core:tool` talks to `SpeechPort`. `SpeechSession` records only after gates pass, then `SherpaSpeechEngine.transcribe`. `AndroidSpeechPort` uses `filesDir/models/asr/{model.int8.onnx,tokens.txt}` and `SherpaJni.isAvailable()` (`System.loadLibrary("sherpa-onnx-jni")`). Do not class-load `OfflineRecognizer` until the library loads. Models and `jniLibs` stay out of git. Sideload may ship ASR/TTS under gitignored `app/src/sideload/assets/models/{asr,tts}/`; `BundledModelSeed` copies to `filesDir` when layout is missing. Play sourceSets must not include those assets. `checkChannelLeak` fails if the play Debug APK zip contains `models/asr`, `models/tts`, or `*.onnx`. Intent GGUF is never bundled. Trimmed JNI bindings are Apache-2.0 from sherpa-onnx v1.13.4.

## Don't: Online TTS or commit VITS ONNX

**Problem**: System voices with `isNetworkConnectionRequired` egress text. Checking in VITS (~116MB) belongs in a later slice, not this contract.

**Instead**: `SpeechOutputTool` talks to `PreferOfflineTtsPort`. If offline `TtsEngine.isReady()`, speak offline only. Else system TTS via `AndroidSystemTtsEngine`, max 80 chars, reject network voices. App default offline is `SherpaTtsEngine` on `filesDir/models/tts/{model.onnx,tokens.txt,lexicon.txt}` plus `SherpaJni.isAvailable()`. Do not class-load `OfflineTts` until the library loads (no companion `loadLibrary`). Trimmed `Tts.kt` is Apache-2.0 from sherpa-onnx v1.13.4. VITS ONNX stays out of git. Success JSON is `ok` + `backend` only.

## Don't: Commit GGUF or silent-cloud intent

**Problem**: Qwen3-0.6B GGUF is 420–639MB. Falling back to the cloud LLM hides that the local classifier is missing.

**Instead**: `IntentClassifierTool` talks to `IntentPort`. `filesDir/models/intent/model.gguf` missing or engine not ready → fail with Chinese errors. `LlamaIntentEngine.isReady` needs GGUF + `LlamaJni.isAvailable()` (`System.loadLibrary("llama")`). Parse the first JSON object from complete text. `confidence < 0.5` → `INTENT_LOW_CONFIDENCE`. Do not call `EgressGateway` from this tool. `*.gguf`, `third_party/llama.cpp/`, and `jniLibs` stay out of git. `:tool:system` CMake runs only if `third_party/llama.cpp/CMakeLists.txt` exists; JNI is `nativeComplete` (CPU, temp 0.7 / top-p 0.8 / presence 1.5). Native code must not log the prompt.

## Don't: AgentTool with attacker-controlled download URL

**Problem**: Letting the cloud LLM pick a URL would fetch arbitrary payloads into `filesDir`.

**Instead**: `ModelInstaller` is app-owned. Require `userConfirmed`, `https://` only, SHA-256 match, write `.part` then rename. Rethrow `CancellationException` (do not map to `MODEL_DOWNLOAD_FAILED`); delete `.part` on cancel. `OkHttpModelGet` cancels the Call and `ensureActive()` while copying; rejects non-https redirects. Not registered on `LoopEngine`. HTTPS/SHA-256 come from gitignored `local.properties` keys `dougie.model.*` → `BuildConfig` → `AppOfflineModels` / `OfficialModelCatalog`. Blank URL or SHA → offer not configured; UI must not fetch.

## Scenario: speech_output TTS contract

### 1. Scope / Trigger
Cross-layer: `:core:tool` `SpeechOutputTool` + `PreferOfflineTtsPort` + `SherpaTtsEngine`; `:tool:system` `AndroidSystemTtsEngine` + `SherpaJni.speak` + `AudioTrack`.

### 2. Signatures
- `TtsEngine.isReady(): Boolean` / `suspend fun speak(text: String): TtsOutcome`
- `SherpaTtsEngine(modelDir, nativeAvailable, speakNative)`
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

## Scenario: LoopEngine status contract

### 1. Scope / Trigger
Cross-layer: JVM loop emits `AgentTask` snapshots; Chat maps them to bubbles.

### 2. Signatures
- `LoopEngine.run(initial: AgentTask, emit: suspend (AgentTask) -> Unit): AgentTask`
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
- `TextDelta` snapshots set `AgentTask.streamingText` while `THINKING`; `COMPLETED.finalAnswer` is the joined text and `streamingText` is cleared.
- `TaskManager.cancel()` → `FAILED` + `UserFacingErrors.CANCELLED`; in-flight HTTP/SSE is cancelled.

### 4. Validation & Error Matrix
- Unknown tool name → `FAILED`, `lastError` set, no further LLM calls
- `result.isFatal` → `FAILED`
- `loopCount >= maxLoops` without FinalAnswer → `FAILED` / `MaxLoopExceeded`
- Empty trimmed input → `TaskManager` no-op
- New submit while status is not COMPLETED/FAILED → ignored (no overlapping loops), including `AWAITING_CONFIRMATION`
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
- `LoopEngineTest.unknownToolFailsWithoutExecuting`
- `LoopEngineTest.canCallTimeThenBatteryInOneTask`
- `LoopEngineTest.cancelStopsInFlightStreamAndFailsTask`

### 7. Wrong vs Correct
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
)
```
