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
- Native llama JNI must not log prompts or completions

`LoopEngine`, `EgressGateway`, and `OpenAICompatibleProvider` currently log nothing. When adding logs, use tool **name** + `taskId` + `loopCount` only. `AuditLog` may persist `taskId`, `toolName`, and `outcome` (`SUCCESS`/`FAILED`) — never Prompt, API keys, calendar event titles/bodies, clipboard text, coordinates, capture pixels, full app-intent URIs (no query strings), microphone audio, speech transcripts, TTS utterance text, or intent-classifier text/slots. Never log `MemoryEntry.content`, retrieved facts, the assembled system prompt, or `snapshot_json`.

`PreferenceStore` stores `api_key` in EncryptedSharedPreferences (`dougie_provider_secure`). Never write the key to Logcat, plaintext `SharedPreferences`, or Compose preview dumps.

Release builds must not log raw LLM HTTP bodies (`PRD.md` §9.3 / review #10).
