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
  EgressGateway.kt
  ToolCallSanitizer.kt
core/runtime/src/test/kotlin/com/dougie/core/runtime/
core/tool/src/main/kotlin/com/dougie/core/tool/
  AgentTool.kt
  FakeBatteryTool.kt
  SystemTimeTool.kt
tool/system/src/main/kotlin/com/dougie/tool/system/
  DeviceBatteryTool.kt
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
| `:core:tool` | `AgentTool` + JVM tools (`FakeBatteryTool`, `SystemTimeTool`) | `BatteryManager` / other Android APIs |
| `:core:runtime` | `LoopEngine`, `TaskManager`, `EgressGateway.stream`, `ToolCallSanitizer` | Compose, Android Context, HTTP |
| `:tool:system` (Android) | `DeviceBatteryTool` | Loop state machine, LLM HTTP |
| `:data:preferences` (Android) | EncryptedSharedPreferences + `allowCloud` default false | Loop / Chat UI |
| `:app` | Wires OkHttp (`readTimeout` 60s, `callTimeout` 0), Gateway, `battery`+`time`, PreferenceStore, `Dispatchers.Default` | Business rules that belong in core |

New JVM tests for the loop and gateway go in `:core:runtime` `src/test`. Provider HTTP tests go in `:core:llm` `src/test`.

## Naming Conventions

- Types: `AgentTask`, `TaskStatus`, `LoopEngine`, `FakeLlmProvider`, `FakeBatteryTool`, `SystemTimeTool`, `DeviceBatteryTool`, `EgressGateway`, `ToolCallSanitizer`
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
- New submit while status is not COMPLETED/FAILED → ignored (no overlapping loops)
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
