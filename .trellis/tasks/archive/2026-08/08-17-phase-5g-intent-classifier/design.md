# Design: Phase 5g Intent Classifier

## Boundaries

| Piece | Module |
|-------|--------|
| `IntentModelLayout`, `IntentEngine`, `IntentClassifierTool` | `:core:tool` |
| `AndroidIntentPort` | `:tool:system` (file presence under `filesDir/models/intent/`) |
| Default engine | `UnwiredIntentEngine` until llama.cpp slice |

## Contracts

`IntentModelLayout.isPresent`: `model.gguf` exists and length > 0.

`IntentEngine.isReady()` / `suspend fun classify(text): IntentHit`

`IntentClassifierTool` L0, required `text`. Success:

`{"ok":true,"intent":"...","slots":{...},"route":"...","confidence":0.9}`

`confidence < 0.5` → `INTENT_LOW_CONFIDENCE`.

## Rollback

Unregister `intent_classifier`.
