# Implement — MVP App Intent Tool

## Checklist

1. UriAllowlist + AppIntentTool + Fake port tests (https ok, tel reject, idempotency, background).
2. AndroidAppIntentPort startActivity; Main/Application context.
3. Register in DougieApplication tools map.
4. OpenAI descriptor test includes app_intent.
5. LoopEngine L2 test optional if Fake tool registered in one test.

## Validation

```bash
./gradlew :core:runtime:test :core:tool:test :core:llm:test :app:assembleDebug
```

## Review gates

- tel/file never reach port
- L0 battery tests green
- no URI query in logs

## Rollback

Remove tool registration.
