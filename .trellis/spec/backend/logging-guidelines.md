# Logging Guidelines

> What must never appear in logs.

## Forbidden

- Full LLM prompts or completions (`PRD.md` §9.3)
- API keys, Keystore material, `Authorization` header values
- Raw LLM HTTP request/response bodies and SSE `data:` payloads (Release and Debug)
- Tool arguments that contain secrets
- Raw microphone PCM / WAV / audio byte arrays, and ASR transcripts in Logcat or `AuditLog`
- TTS utterance text (what `speech_output` speaks)
- Intent classifier input text, intent labels, slots, or route JSON
- Native intent JNI must not log features, labels, or input text.
- Model download URLs (especially query strings) and partial `.part` paths in Logcat
- SAF content URIs and import source paths in Logcat
- Task-progress notification body: user `input`, `finalAnswer`, `lastError`, `streamingText`, tool `argsSummary`, or `resultJson` (shade may show `TaskStatus` + `loopCount` + last **tool name** only)
- Overlay ball / Play bubble chrome: user prompt, `lastError`, loop status, or tool args — sideload `DougieOverlayService` shows **Dougie** (`app_name`) only

`LoopEngine`, `EgressGateway`, and `OpenAICompatibleProvider` currently log nothing. When adding logs, use tool **name** + `taskId` + `loopCount` only. `AuditLog` may persist `taskId`, `toolName`, and `outcome` (`SUCCESS`/`FAILED`) — never Prompt, API keys, calendar event titles/bodies, clipboard text, coordinates, capture pixels, full app-intent URIs (no query strings), microphone audio, speech transcripts, TTS utterance text, or intent-classifier text/slots. The Debug developer page (`:feature:debug`) may display the same fields plus `createdAt` and live `status` / `loopCount` / `lastError` from `AgentTask`. It must not dump tool args, `resultJson`, prompts, keys, transcripts, or `snapshot_json`. Never log `MemoryEntry.content`, retrieved facts, the assembled system prompt, or `snapshot_json`. Smoke probes must not write transcripts, PCM, or classifier labels to Logcat or `AuditLog`. The system shade uses `formatTaskNotice`: FAILED is `任务失败 · 循环 n`, not `lastError`.

`PreferenceStore` stores `api_key` in EncryptedSharedPreferences (`dougie_provider_secure`). Never write the key to Logcat, plaintext `SharedPreferences`, or Compose preview dumps.

Release builds must not log raw LLM HTTP bodies (`PRD.md` §9.3 / review #10).
