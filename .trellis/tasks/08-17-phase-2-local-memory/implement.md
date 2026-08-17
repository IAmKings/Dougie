# Implement — Phase 2 Local Memory FTS

## Checklist

1. Model types + `AgentTask.retrievedMemories`.
2. `:core:memory` module: store interface, InMemory (keyword split / contains), MemoryGate + tests.
3. LoopEngine: search at PREPARING if enabled; ingest on COMPLETED.
4. OpenAI `buildRequestJson` appends memory block; do not log it.
5. `:data:memory` Room DB; app wires RoomMemoryStore.
6. Preference `memoryEnabled`.
7. `:feature:memory` + MainActivity route from bottom bar (Chat must accept onOpenMemory).
8. Fill `.trellis/spec/backend/database-guidelines.md` after implementation (Room facts + FTS).

## Validation

```bash
./gradlew :core:memory:test :core:runtime:test :app:assembleDebug
```

Manual: 说「我叫小明」→ 完成 → 记忆页可见 → 新对话问「我叫什么」时请求应带上该事实（需已授权云端）。

## Review gates

- `:core:*` JVM-only
- No Fake fallback
- Secrets not stored
- Memory UI says Dougie not Waku

## Rollback

Skip ingest if Gate throws; loop still completes.
