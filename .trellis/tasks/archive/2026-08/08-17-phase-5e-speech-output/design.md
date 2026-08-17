# Design: Phase 5e Speech Output

## Boundaries

| Type | Module |
|------|--------|
| `TtsEngine`, `PreferOfflineTtsPort`, `SpeechOutputTool` | `:core:tool` |
| `AndroidSystemTtsEngine` | `:tool:system` |
| Default offline engine | `UnwiredTtsEngine` (`isReady=false`) until VITS slice |

## Contracts

`TtsEngine.isReady()` / `suspend fun speak(text: String): TtsOutcome`

`PreferOfflineTtsPort.speak`: offline if ready else fallback. Fallback rejects length > 80. Map `NETWORK` to 联网拒绝.

`SpeechOutputTool` L0, `text` required string.

Success: `{"ok":true,"backend":"offline"|"system"}`.

## Rollback

Unregister `speech_output`.
