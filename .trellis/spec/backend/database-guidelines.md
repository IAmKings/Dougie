# Database Guidelines

> Local persistence for Dougie memory (Phase 2) and task recovery (Phase 4). Both use **SQLiteOpenHelper**, not androidx Room. Catalog Room/KSP entries in `gradle/libs.versions.toml` stay unused until a module actually adopts Room.

## Overview

- `:core:memory` and `:core:runtime` are Kotlin JVM only. They own `MemoryStore` / `TaskStore` / `IdempotencyStore` / `AuditLog` plus in-memory implementations for tests.
- Android persistence:
  - `:data:memory` — `RoomMemoryStore` (`SQLiteOpenHelper`, class name kept from the design).
  - `:data:tasks` — `DougieTaskStores` wrapping `agent_tasks`, `idempotency`, and `audit_log`.
- Do not log fact `content`, FTS queries, task `snapshot_json`, calendar bodies, clipboard text, prompts, or API keys (`logging-guidelines.md`).

## Memory schema (`dougie_memory.db`, version `1`)

```sql
CREATE TABLE memory_facts (
  docid INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  type TEXT NOT NULL,          -- "fact"
  content TEXT NOT NULL,
  source TEXT NOT NULL,        -- taskId · user quote (truncated)
  confidence REAL NOT NULL,
  created_at INTEGER NOT NULL, -- epoch ms
  updated_at INTEGER NOT NULL
);

CREATE VIRTUAL TABLE memory_facts_fts USING fts4(
  content,
  tokenize=unicode61
);
```

`memory_facts.docid` is the FTS4 `rowid`. Writes dual-insert/update/delete both tables in one transaction. `embedding` is not stored (always null in `MemoryEntry` this phase).

Provider flag `memory_enabled` is **not** in SQLite. It is `PreferenceStore` key `memory_enabled` (default `true`) on EncryptedSharedPreferences file `dougie_provider_secure`.

### Memory query patterns

- `list()`: `SELECT ... FROM memory_facts ORDER BY updated_at DESC`
- `search(query, limit)`: FTS `MATCH` on each `searchNeedles(query)` token, **plus** `content LIKE '%' || needle || '%'` fallback for CJK.
- Token budget for LLM inject is applied in `LoopEngine` (max 5 facts / 800 chars), not in SQL.

## Task recovery schema (`dougie_tasks.db`, version `1`)

```sql
CREATE TABLE agent_tasks (
  task_id TEXT PRIMARY KEY NOT NULL,
  snapshot_json TEXT NOT NULL, -- TaskSnapshotCodec of AgentTask
  status TEXT NOT NULL,
  updated_at INTEGER NOT NULL  -- epoch ms; listRecent ORDER BY this DESC
);

CREATE TABLE idempotency (
  idempotency_key TEXT PRIMARY KEY NOT NULL, -- taskId + toolCallId
  result_json TEXT NOT NULL
);

CREATE TABLE audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id TEXT NOT NULL,
  tool_name TEXT NOT NULL,
  outcome TEXT NOT NULL,       -- SUCCESS | FAILED
  created_at INTEGER NOT NULL
);
```

- `TaskManager` calls `TaskStore.upsert` on every loop `emit`. JSON encode failures are skipped; the loop still runs.
- App start: `recoverInterrupted(store)` — if the latest row is not COMPLETED/FAILED, mark FAILED with `UserFacingErrors.INTERRUPTED` and `TaskManager.seed`. Do **not** resume the LLM stream.
- `calendar_create` reads/writes `idempotency` via `IdempotencyStore` (INSERT OR IGNORE). A new tool instance with the same store must not call `CalendarPort.createEvent` again for the same key.
- `AuditLog.record` writes only `task_id`, `tool_name`, `outcome`, `created_at`. Never prompt text, keys, calendar titles, clipboard, coordinates, or image bytes.

JVM tests use `InMemoryTaskStore` / `InMemoryIdempotencyStore` / `NoOpAuditLog`. `TaskManager` defaults `taskStore` to null (no persist).

## Migrations

Version 1 is create-only for both databases. `onUpgrade` drops and recreates. Replace with additive migrations before shipping a second version.

## Naming Conventions

- Tables: snake_case (`memory_facts`, `agent_tasks`, `idempotency`, `audit_log`)
- Columns: snake_case; Kotlin models use camelCase (`createdAt`, `taskId`)
- Database files: `dougie_memory.db`, `dougie_tasks.db`

## Common Mistakes

- Putting Room / `android.database` in `:core:*` — keep JVM tests on in-memory stores.
- Logging MATCH queries, fact text, or `snapshot_json` (may contain tool args).
- Auto-continuing an interrupted task with a new LLM call.
- Writing calendar event bodies or clipboard text into `audit_log`.
- Silent Fake LLM on the app chat path.
