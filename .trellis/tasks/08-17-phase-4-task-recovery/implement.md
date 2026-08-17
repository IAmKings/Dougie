# Implement — Phase 4 Task Recovery Reliability

## Checklist

1. TaskStore + recoverInterrupted + TaskManager persist on emit + seed on start.
2. IdempotencyStore; CalendarCreateTool; JVM test new tool instance hits store.
3. OpenAI/network retry before first event.
4. AuditLog from LoopEngine after tool outcome (name + taskId + SUCCESS/FAILED only).
5. Chat retry + History module + bottom 任务.
6. Update `database-guidelines.md` with task/idempotency/audit tables.

## Validation

```bash
./gradlew :core:runtime:test :core:tool:test :core:llm:test :app:assembleDebug
```

## Review gates

- Fake 3-loop green
- Interrupted recovery does not call LLM
- Audit has no clipboard/calendar body
- core JVM-only

## Rollback

Skip persist if JSON encode throws; loop still runs.
