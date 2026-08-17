# Design: Phase 5d Sherpa JNI

## Boundaries

| Piece | Module |
|-------|--------|
| `SherpaSpeechEngine` | `:core:tool` (inject `nativeAvailable` + `decode`) |
| `SherpaJni` + trimmed `com.k2fsa.sherpa.onnx` JNI bindings (Apache-2.0, v1.13.4, no getOfflineModelConfig) | `:tool:system` |
| Default `AndroidSpeechPort` engine | `SherpaSpeechEngine` wired to `SherpaJni` |

## Contracts

`isReady` = `AsrModelLayout.isPresent(modelDir) && nativeAvailable()`

Decode config: `modelType=paraformer`, `provider=cpu`, tokens + model.int8.onnx absolute paths.

Do not class-load `OfflineRecognizer` until `nativeAvailable()` is true (static `loadLibrary` in companion).

## Rollback

Default `AndroidSpeechPort` engine back to `UnwiredSpeechEngine`.
