# Design: Phase 5f VITS TTS

## Boundaries

| Piece | Module |
|-------|--------|
| `TtsModelLayout`, `SherpaTtsEngine` | `:core:tool` (inject `nativeAvailable` + `speak`) |
| Trimmed `com.k2fsa.sherpa.onnx` `OfflineTts` / VITS config (Apache-2.0, v1.13.4). No `getOfflineTtsConfig`. No `companion loadLibrary`. | `:tool:system` |
| `SherpaJni.speak` | loadLibrary already cached; `OfflineTts.newFromFile` + `generate` + `AudioTrack` |
| App wiring | `PreferOfflineTtsPort(SherpaTtsEngine(...), AndroidSystemTtsEngine)` |

## Contracts

`isReady` = `TtsModelLayout.isPresent(modelDir) && nativeAvailable()`

VITS: `provider=cpu`, `numThreads=1`, `sid=0`, `speed=1.0`. Play float PCM via `AudioTrack` (speech usage), then `release()` the recognizer-equivalent `OfflineTts`.

Do not class-load `OfflineTts` until `SherpaJni.isAvailable()` is true (same gate as ASR).

## Rollback

App offline engine back to `UnwiredTtsEngine`.
