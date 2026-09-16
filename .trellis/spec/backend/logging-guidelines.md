# Logging Guidelines

> What must never appear in logs.

## Forbidden

- Full LLM prompts or completions (`PRD.md` §9.3), including `ChatPromptAssembler.systemPrefix` / `localPrompt` and prior-turn (`priorTurns`) text
- API keys, Keystore material, `Authorization` header values
- Raw LLM HTTP request/response bodies and SSE `data:` payloads (Release and Debug)
- Tool arguments that contain secrets
- Raw microphone PCM / WAV / audio byte arrays, and ASR transcripts in Logcat or `AuditLog`. `AsrEvalReport.toString()` is counts/rates only — never `reference` or `hypothesis`
- TTS utterance text (what `speech_output` speaks) and host reply playback (`finalAnswer` / PCM). `KokoroEvalReport.toString()` is counts/rates only — never utterance `text` or PCM
- Intent classifier input text, intent labels, slots, or route JSON. `IntentRuleEReport.toString()` is counts/rates only — never utterance `text` or intent labels. Debug `ruleEMessage` is that counts string plus relative `eval/intent/predictions.jsonl` (or existing `INTENT_MODEL_MISSING` / `INTENT_ENGINE_NOT_READY`); never gold text, predicted labels, or 「已达标」. `AppIntentRuleEEval` / `runForward` must not Logcat classify input.
- Native intent JNI must not log features, labels, or input text. Embed JNI (`g_embed_session` in the same `dougie_intent` `.so`) must not log tokens, pool vectors, model paths, or input text.
- Model download URLs (especially query strings) and partial `.part` paths in Logcat
- SAF content URIs and import source paths in Logcat
- Gallery/camera content URIs, JPEG/base64 payloads, and capture gray bytes in Logcat or notification extras
- Task-progress notification body: user `input`, `finalAnswer`, `lastError`, `streamingText`, tool `argsSummary`, or `resultJson` (shade may show `TaskStatus` + `loopCount` + last **tool name** only)
- Overlay ball / Play bubble chrome: user prompt, `lastError`, loop status, or tool args — sideload collapsed chrome is the logo disc (TalkBack `app_name` / Dougie only); expanded menu labels are fixed product strings (`截取屏幕` / `打开对话`), never the user utterance
- Fact `content`, FTS MATCH queries, embedding `BLOB` / float vectors, cosine scores, and embed query text
- Isolated `js_eval` `script` / `data` payloads (Logcat, `AuditLog`, shade)
- Isolated `py_eval` `script` / `data` payloads and sandbox file bytes (Logcat, `AuditLog`, shade)
- SMS compose `to` / `body` and dial `number` (Logcat, `AuditLog`, shade). Confirm Card may show `argsJson` in Chat only. History **展开** chrome is raw `toolName` + 成功/失败/进行中 only — never `argsSummary`, `resultJson`, calendar titles, clipboard text, or SMS body.
- Schedule reminder notification: user draft/prompt (body is `定时提醒 · HH:mm` only)
- Custom conversation titles and `snapshot_json` when deleting a window (`deleteConversation` / `deleteByConversation`)
- History-search query text, conversation hit `user` / `assistant`, `sourceLabel`, or custom window titles (`searchCompletedTurns` / LoopEngine retrieve)

`LoopEngine`, `EgressGateway`, and `OpenAICompatibleProvider` currently log nothing. When adding logs, use tool **name** + `taskId` + `loopCount` only. `AuditLog` may persist `taskId`, `toolName`, and `outcome` (`SUCCESS`/`FAILED`) — never Prompt, API keys, calendar event titles/bodies, clipboard text, coordinates, capture pixels, full app-intent URIs (no query strings), SMS body, phone numbers, microphone audio, speech transcripts, TTS utterance text, or intent-classifier text/slots. The Debug developer page (`:feature:debug`) may display the same fields plus `createdAt` and live `status` / `loopCount` / `lastError` / `completionPath` (`completionPath?.toUserLabel() ?: "无"` → 本地意图 / 本地 LLM / 远程 LLM / 无) from `AgentTask`, plus Rule E `ruleEMessage` counts/rates and the relative predictions path. LoopEngine writes `LOCAL_LLM` when `provider.isLocal` on the LLM loop. It must not dump tool args, `resultJson`, prompts, keys, transcripts, classifier labels, or `snapshot_json` (including `startedAt` / `endedAt` — persist them, do not Logcat the JSON). Never log `MemoryEntry.content`, retrieved facts, conversation hit text, embedding vectors, cosine scores, embed queries, `ChatPromptAssembler` output, assembled prompts, prior-turn text, or `snapshot_json`. Smoke probes must not write transcripts, PCM, classifier labels, or embedding vectors to Logcat or `AuditLog`. Sideload LiteRT-LM spike (`ChatLlmProbe`) and `ChatLlmProvider` must not Logcat the prompt, completion, parsed tool JSON, or weight path (filename on screen is OK). Engine `warmup()` / `releaseIfIdle()` must not log paths either. The system shade uses `formatTaskNotice`: FAILED is `任务失败 · 循环 n`, not `lastError`.

`PreferenceStore` stores `api_key` in EncryptedSharedPreferences (`dougie_provider_secure`). Never write the key to Logcat, plaintext `SharedPreferences`, or Compose preview dumps.

Release builds must not log raw LLM HTTP bodies (`PRD.md` §9.3 / review #10).
