# Implement — Phase 1a Cloud LLM Battery Tool

## Checklist

1. Extend `:core:model` with `EgressPolicy`, `LlmRequest`/`Message`/`ToolDescriptor`, `LlmError`/`PolicyError`.
2. Add `EgressGateway` in `:core:runtime`; LoopEngine calls gateway instead of `LlmProvider.generate(LoopContext)` — adapt Fake via a small adapter that still uses tool-trace count **or** teach Fake to consume `LlmRequest` (prefer request-based Fake: if battery tool result not yet in messages, return ToolCall).
3. `OpenAICompatibleProvider` + OkHttp; MockWebServer tests in `:core:llm`.
4. Gateway tests: cloud provider + `allowCloud=false` never hits a recording interceptor.
5. `:tool:system` `DeviceBatteryTool`; `:app` registers it as `"battery"`.
6. `:data:preferences` encrypted store; `:feature:settings` Compose form; NavHost Chat↔Settings.
7. INTERNET in app manifest; Chat maps egress/network errors to `AgentMessage`.
8. `ContextBuilder` minimal: system one-liner + user + tool results; token budget can be a stub maxTokens.
9. `./gradlew :core:runtime:test :core:llm:test :app:assembleDebug`

## Validation

```bash
./gradlew :core:runtime:test :core:llm:test :app:assembleDebug
```

Manual: Settings enable cloud + paste key → Chat 电量问题 → 设备真实百分比。再关 allowCloud → 再发送应拦截。

## Review gates

- No API key in logs or plaintext XML prefs files
- `:core:*` Gradle still JVM-only
- Chat still shows Thinking + Tool cards

## Rollback

If OpenAI JSON parsing is unstable, keep Gateway + Battery and leave provider behind Fake for debug only; do not ship allowCloud default true.

## Follow-up

Phase 0 was not git-committed (user did not ask). Phase 1a lands on the same working tree.
