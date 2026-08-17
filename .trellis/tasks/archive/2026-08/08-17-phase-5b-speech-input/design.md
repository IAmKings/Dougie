# Design: Phase 5b SpeechInput

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | `SpeechPort`, `SpeechInputTool` |
| `:tool:system` | `AndroidSpeechPort` — foreground + `filesDir/models/asr/encoder.onnx` existence; never opens mic until engine exists |
| `:feature:permissions` | Microphone row |
| `:app` | Wire tool + `RECORD_AUDIO` manifest |

## Contracts

`SpeechPort`:

- `isAppForeground(): Boolean`
- `isModelPresent(): Boolean`
- `isEngineReady(): Boolean`
- `suspend fun listen(): String` — transcript only; tests count calls

Success JSON: `{"ok":true,"text":"..."}`.

Model path (private dir, both flavors): `filesDir/models/asr/encoder.onnx`. Play APK does not ship it. Sideload bundling is a later slice.

This slice `AndroidSpeechPort.isEngineReady()` is always false (no sherpa JNI yet).

## Data flow

Policy RECORD_AUDIO → foreground → model present → engine ready → listen → text into Loop (never audio).

## Rollback

Remove SpeechInputTool registration and permission row.
