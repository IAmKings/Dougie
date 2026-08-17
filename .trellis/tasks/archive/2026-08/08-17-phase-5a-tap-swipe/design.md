# Design: Phase 5a TapSwipe

## Boundaries

| Module | Owns | Must not own |
|--------|------|----------------|
| `:tool:accessibility` | `GesturePort`, `AndroidGesturePort`, `HighRiskForeground`, real `TapSwipeTool`, service instance | Play APK, LoopEngine, Compose |
| `:app` sideload `ChannelTools` | Wire port + `IdempotencyStore` + consent | Gesture math |
| `:app` play `ChannelTools` | Empty register (same signature) | Any accessibility import |
| `:core:model` | Optional user-facing error constants | Android |

## Contracts

`GesturePort`:

- `isConnected(): Boolean`
- `foregroundPackage(): String?`
- `tap(x: Int, y: Int): Boolean`
- `swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean`

Tool args JSON:

- `action`: `"tap"` | `"swipe"` (required)
- `x`,`y`: integers ≥ 0
- swipe also needs `x2`,`y2` ≥ 0; `durationMs` default 300, clamp 50–2000

`idempotencyKey = taskId + toolCallId`. Store only successful JSON.

## Data flow

Consent (already) → Policy L3 confirm (already) → `validateArguments` → consent + connected + denylist → idempotency get → `dispatchGesture` → put.

## Tradeoffs

- Port + JVM fake instead of Robolectric: keeps `:tool:accessibility` tests on the JVM classpath used today.
- Package denylist is conservative substring + known payment/bank/password tokens, not a live Play store taxonomy.

## Compatibility / rollback

Play flavor unchanged except unused `IdempotencyStore` parameter on `ChannelTools.register`. Rollback: restore stub execute that returns `NOT_ENABLED`.
