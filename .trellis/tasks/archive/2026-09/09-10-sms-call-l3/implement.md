# Implement SMS compose + dial

## Checklist

1. `PhoneNumber` + `TelecomPort` / Fake + `SmsComposeTool` / `PhoneDialTool` L3, idempotency.
2. `AndroidTelecomPort` SENDTO/DIAL, NEW_TASK, foreground check.
3. Register both flavors in `DougieApplication`. Manifest queries. UserFacingErrors for launch fail.
4. Chat names + confirm. Assembler tests omit locally.
5. Spec: directory-structure, error-handling, logging (no SMS body), component-guidelines, quality leak (no SEND_SMS).

## Validate

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:llm:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

Device: confirm → SMS app filled; dialer filled; reject → nothing.

## Start gate

Approve this summary before `task.py start`.
