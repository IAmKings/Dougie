# Implement: Phase 5a TapSwipe

1. Add `GesturePort` + `HighRiskForeground` + `AndroidGesturePort`; bind service instance in `onServiceConnected` / `onUnbind`.
2. Replace stub `TapSwipeTool` with real execute + `validateArguments`; inject store + port + consent.
3. Sideload `ChannelTools` passes `taskStores.idempotencyStore`; play signature matches, no accessibility imports.
4. `DougieApplication.register` / `refreshChannelTools` pass the store.
5. Tests: consent, disconnected, denylist, tap/swipe once, idempotent second call, bad args.
6. Validate: `JAVA_HOME` JDK 17 then `./gradlew :tool:accessibility:test :app:checkChannelLeak`.
7. Update `.trellis/spec/backend/directory-structure.md` and `error-handling.md` after green.

Rollback: revert `:tool:accessibility` + ChannelTools wiring; keep flavors.
