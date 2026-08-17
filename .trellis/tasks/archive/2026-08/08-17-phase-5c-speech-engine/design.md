# Design: Phase 5c Speech Engine Seam

## Boundaries

| Type | Module |
|------|--------|
| `AsrModelLayout`, `SpeechUtterance`, `SpeechRecorder`, `SpeechEngine`, `SpeechSession`, `UnwiredSpeechEngine` | `:core:tool` |
| `AudioRecordSpeechRecorder`, `AndroidSpeechPort` | `:tool:system` |

## Contracts

`AsrModelLayout.isPresent(dir)`: `model.int8.onnx` and `tokens.txt` exist and length > 0.

`SpeechEngine.isReady()` / `transcribe(utterance): String`

`SpeechRecorder.capture(): SpeechUtterance` (float PCM + sampleRate)

`SpeechSession` implements `SpeechPort`. Default Android engine is `UnwiredSpeechEngine` (`isReady=false`).

Capture duration: 3000ms, 16kHz mono PCM16 → float32 / 32768.

## Data flow

Policy RECORD_AUDIO → foreground → model files → engine ready → AudioRecord → transcribe → text JSON.

## Rollback

Restore 5b `AndroidSpeechPort` stub (`isEngineReady=false`, `listen` errors).
