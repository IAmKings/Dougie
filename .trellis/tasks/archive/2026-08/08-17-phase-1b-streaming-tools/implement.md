# Implement — Phase 1b Streaming + 多 Tool

## Checklist

1. Add `LlmEvent` + `AgentTask.streamingText` in `:core:model`.
2. `LlmProvider.stream(context): Flow<LlmEvent>`; default `generate` collects to `LlmResponse`. Fake emits existing 3-loop as ToolCall events then TextDelta/Final.
3. OpenAI SSE parser + `"stream": true`; keep non-stream parse for fallback if needed. Fix baseUrl joining so MockWebServer paths stay `/v1/chat/completions`.
4. `EgressGateway.stream`; LoopEngine collect + timeouts; cancel propagates.
5. `ToolCallSanitizer` + `ToolDescriptor`; LoopEngine calls it before `execute`.
6. `SystemTimeTool`; app `tools` map `time` + `battery`.
7. Chat mapping for streamingText + generic tool labels + optional time example chip.
8. Tests listed in PRD. OkHttp: `readTimeout` 60s is OK; `callTimeout` may need 0 for long streams.

## Validation

```bash
./gradlew :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:assembleDebug
```

## Review gates

- Gateway deny still zero HTTP on stream path
- No SSE/prompt/key logs
- Fake 3-loop tests green
- `:core:*` JVM-only

## Rollback

Revert LoopEngine to `complete()` only; leave time tool registered.
