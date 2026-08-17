# Implement — Phase 3a Calendar Clipboard Policy

## Checklist

1. Model: RiskLevel, AWAITING_CONFIRMATION, descriptor fields, UserFacingErrors for skip/reject.
2. PolicyEngine + LoopEngine confirm gate; default isGranted `{ true }` so existing tests unchanged unless they register L2.
3. Tests: L2 confirm/reject; permission deny; calendar create idempotency via Fake port.
4. CalendarPort / ClipboardPort; Android impls in `:tool:system`; register four tools + existing battery/time.
5. OpenAI tools from descriptors (description + properties).
6. Chat ConfirmCard + ViewModel confirm/reject; inputEnabled false while awaiting.
7. `:feature:permissions` + nav from chat.
8. Manifest READ/WRITE_CALENDAR; request from Permission Center.

## Validation

```bash
./gradlew :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:assembleDebug
```

## Review gates

- L0 Fake 3-loop green
- Reject does not call CalendarPort.create
- No clipboard/calendar payloads in logs
- core JVM-only

## Rollback

Unregister L2 tools; PolicyEngine always Allow.
