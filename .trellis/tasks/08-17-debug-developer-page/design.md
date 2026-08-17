# Design: Debug developer page

## Boundaries

| Piece | Module |
|-------|--------|
| Screen + VM | `:feature:debug` |
| Live task | `TaskManager.task` |
| Audit read | extend `AuditLog` with `listRecent`; `SqliteAuditLog` query |
| Entry | `:feature:settings` row → `:app` route |

`:feature:settings` only navigates (`onOpenDebug`); it does not own audit queries.

## Data flow

```
Settings 「开发者」 → DebugRoute
  TaskManager.task → taskId / status / loopCount / lastError
  AuditLog.listRecent(50) → rows (taskId, toolName, outcome, createdAt)
```

## Contracts

```
data class AuditEntry(val taskId: String, val toolName: String, val outcome: String, val createdAt: Long)
interface AuditLog {
  fun record(...)
  suspend fun listRecent(limit: Int = 50): List<AuditEntry>
}
```

`NoOpAuditLog.listRecent` → empty. Never persist or display tool args.

## Compatibility

Play + sideload. No flavor split.

## Rollback

Remove `:feature:debug`, settings row, `listRecent`.
