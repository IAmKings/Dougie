# Logging Guidelines

> What must never appear in logs.

## Forbidden

- Full LLM prompts or completions (`PRD.md` §9.3)
- API keys, Keystore material, `Authorization` header values
- Raw LLM HTTP request/response bodies (Release and Debug)
- Tool arguments that contain secrets

`LoopEngine`, `EgressGateway`, and `OpenAICompatibleProvider` currently log nothing. When adding logs, use tool **name** + `taskId` + `loopCount` only.

`PreferenceStore` stores `api_key` in EncryptedSharedPreferences (`dougie_provider_secure`). Never write the key to Logcat, plaintext `SharedPreferences`, or Compose preview dumps.

Release builds must not log raw LLM HTTP bodies (`PRD.md` §9.3 / review #10).
