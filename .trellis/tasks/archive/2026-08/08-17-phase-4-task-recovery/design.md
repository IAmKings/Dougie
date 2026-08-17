# Design — Phase 4 Task Recovery Reliability

## Boundaries

```text
:core:model        UserFacingErrors.INTERRUPTED; optional updatedAt on AgentTask
:core:runtime      TaskStore, AuditLog, TaskManager persist+recover, retry hook optional
:core:tool         IdempotencyStore injected into CalendarCreateTool
:core:llm          retry IOException in stream/generate (2 extra attempts)
:data:tasks        SQLite TaskStore + IdempotencyStore + AuditLog
:feature:history   list UI
:feature:chat      Retry on FAILED; onOpenHistory
:app               recover() onCreate; wire stores
```

## Contracts

```kotlin
interface TaskStore {
  suspend fun upsert(task: AgentTask)
  suspend fun listRecent(limit: Int = 50): List<AgentTask>
}

fun recoverInterrupted(store: TaskStore): AgentTask?
// latest non-terminal → FAILED INTERRUPTED → upsert → return for Chat
```

Serialize AgentTask with kotlinx.serialization (add plugin to `:core:model` if needed) or a dedicated `TaskSnapshot` JSON in the data layer so core:model stays free of serialization if easier. Prefer snapshot DTO in `:data:tasks` / `:core:runtime` to avoid annotating every model type.

Idempotency:
```kotlin
interface IdempotencyStore {
  fun get(key: String): String?
  fun put(key: String, resultJson: String)
}
```
Sync OK if SQLite on background dispatcher.

Audit:
```kotlin
fun record(taskId: String, toolName: String, outcome: String)
```

LLM retry: wrap `stream()` collection start; do not retry after first TextDelta emitted (avoid duplicate user-visible tokens). Retry only if failure before any event.

## Recovery UX

App start → recoverInterrupted → TaskManager.seed(task) if non-null.
User taps 重试 → submit(task.input) new taskId (new idempotency keys). Old calendar event keys remain so **same toolCallId+taskId** won't replay; new submit has new taskId so create could happen again if user retries the whole prompt — that's correct (new user intent). Case 09 is **restore of the same in-flight toolCall**, not retry of the whole chat.

## UI

History: reverse chronological; status badge; error line; tap could open read-only (optional). Bottom nav 任务 selected.

Retry button on last AgentMessage failure in Chat.

## Trade-offs

| Choice | Why |
|--------|-----|
| Fail interrupted tasks instead of auto-loop | PRD: no stream resume |
| Retry network only before first token | Avoid duplicate streaming bubbles |
| SQLiteOpenHelper like memory | Consistent; Room catalog unused |

## Rollback

Null TaskStore = current in-memory behavior.
