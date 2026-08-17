# Design: Phase 5h Llama Intent Engine

## Boundaries

| Piece | Module |
|-------|--------|
| `LlamaIntentEngine`, `IntentJsonParser`, `IntentPrompt` | `:core:tool` (inject `nativeAvailable` + `complete`) |
| `LlamaJni` | `:tool:system` — `loadLibrary("llama")` + `nativeComplete` |
| Default `AndroidIntentPort` engine | `LlamaIntentEngine` wired to `LlamaJni` |

## Contracts

`isReady` = `IntentModelLayout.isPresent(modelDir) && nativeAvailable()`

`complete(modelDir, prompt)` returns raw model text. Parser takes the first JSON object.

Sampling (documented for later NDK): temperature 0.7, top-p 0.8, presence-penalty 1.5, thinking off.

## Rollback

`AndroidIntentPort` default engine back to `UnwiredIntentEngine`.
