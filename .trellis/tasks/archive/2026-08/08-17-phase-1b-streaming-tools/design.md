# Design — Phase 1b Streaming + 多 Tool

## Boundaries

```text
:core:model     LlmEvent, LlmRequest (optional), AgentTask.streamingText
:core:llm       OpenAICompatibleProvider.stream SSE; keep generate() as collect-to-LlmResponse for tests
:core:runtime   EgressGateway.stream; LoopEngine collects Flow; ToolCallSanitizer before execute
:core:tool      SystemTimeTool (Clock injectable) + existing FakeBatteryTool
:tool:system    DeviceBatteryTool unchanged
:feature:chat   map streamingText while busy; generic tool card labels
:app            register "time" + "battery"; OkHttp readTimeout for SSE idle
```

## Contracts

### LlmEvent
```kotlin
sealed class LlmEvent {
    data class TextDelta(val text: String) : LlmEvent()
    data class ToolCall(val id: String, val name: String, val argsJson: String) : LlmEvent()
}
```
A stream ends after one ToolCall **or** after text completes (no more events). Do not emit both in one HTTP response handling (OpenAI typically one or the other per assistant message).

### EgressGateway
```kotlin
fun stream(provider: LlmProvider, context: LoopContext): Flow<LlmEvent>
suspend fun complete(...): LlmResponse  // collect stream; keep 1a tests
```
Deny / missing key throw **before** `collect` starts (use `flow { check(); emitAll(provider.stream) }`).

### LoopEngine
On each `TextDelta`: `emit(task.copy(streamingText = (streamingText ?: "") + delta))`.
On `ToolCall`: run sanitizer → existing tool status cycle → clear streamingText.
On stream completion with only text: `COMPLETED` + `finalAnswer`.

### ToolCallSanitizer
Input: tool name, raw args JSON, `ToolDescriptor.parameters`.
Output: sanitized JSON or throw `AgentException`.
Unknown name: fail (do not invent).
Phase 1b descriptors: `battery` and `time` with empty object properties; extra keys stripped; empty/invalid args → `{}`.

### SystemTimeTool
```json
{"iso_local":"2026-08-17T09:30:00+08:00","zone":"Asia/Shanghai","epoch_ms":...}
```
`Clock` constructor param for tests.

## Data flow

```text
LoopEngine
  → Gateway.stream
  → SSE parse → LlmEvent
  → (text) StateFlow streamingText → Chat
  → (tool) Sanitizer → AgentTool.execute
  → next stream round with tool traces in messages
```

OpenAI request: `"stream": true`, same tools array including `time` + `battery`.

## UI

- `toChatUiState`: if `streamingText` not blank, append `AgentMessage(streamingText)` even when status is THINKING.
- Tool cards: map `battery` → 电池工具, `time` → 时间工具, else `toolName`.
- Empty-state: keep battery chip; optional second chip「现在几点了？」.

## Trade-offs

| Choice | Why |
|--------|-----|
| Keep `generate()` collecting stream | 1a MockWebServer tests can stay; add separate SSE tests |
| Time in `:core:tool` | No Android API; easy JVM tests |
| Minimal sanitizer (empty schemas) | Real coercion coverage via a test-only descriptor with typed fields |
| No Room | Parent map splits history later |

## Rollback

Set provider `stream: false` and collect as 1a if SSE parse is unstable; keep sanitizer + time tool.

## Compatibility

Fake `isLocal=true` can implement `stream` as `flow { emit(ToolCall or map FinalAnswer to TextDelta) }`.
