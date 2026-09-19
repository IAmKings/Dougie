# Database Guidelines

> Local persistence for Dougie memory (Phase 2) and task recovery (Phase 4). Both use **SQLiteOpenHelper**, not androidx Room. Catalog Room/KSP entries in `gradle/libs.versions.toml` stay unused until a module actually adopts Room.

## Overview

- `:core:memory` and `:core:runtime` are Kotlin JVM only. They own `MemoryStore` / `TaskStore` / `IdempotencyStore` / `AuditLog` plus in-memory implementations for tests.
- Android persistence:
  - `:data:memory` — `RoomMemoryStore` (`SQLiteOpenHelper`, class name kept from the design).
  - `:data:tasks` — `DougieTaskStores` wrapping `agent_tasks`, `idempotency`, and `audit_log`.
- Do not log fact `content`, FTS queries, task `snapshot_json`, calendar bodies, clipboard text, prompts, or API keys (`logging-guidelines.md`).

## Memory schema (`dougie_memory.db`, version `2`)

```sql
CREATE TABLE memory_facts (
  docid INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  type TEXT NOT NULL,          -- "fact"
  content TEXT NOT NULL,
  source TEXT NOT NULL,        -- taskId · user quote (truncated)
  confidence REAL NOT NULL,
  created_at INTEGER NOT NULL, -- epoch ms
  updated_at INTEGER NOT NULL,
  embedding BLOB               -- little-endian float32; NULL = not embedded
);

CREATE VIRTUAL TABLE memory_facts_fts USING fts4(
  content,
  tokenize=unicode61
);
```

`memory_facts.docid` is the FTS4 `rowid`. Writes dual-insert/update/delete both tables in one transaction. `embedding` is optional. `HybridMemoryStore` fills it when `EmbeddingPort.isReady()`; otherwise it stays NULL and search is FTS/`LIKE` only.

Provider flag `memory_enabled` is **not** in SQLite. It is `PreferenceStore` key `memory_enabled` (default `true`) on EncryptedSharedPreferences file `dougie_provider_secure`.

### Memory query patterns

- `list()`: `SELECT ... FROM memory_facts ORDER BY updated_at DESC` (includes `embedding`)
- `search(query, limit)`: FTS `MATCH` on each `searchNeedles(query)` token, **plus** `content LIKE '%' || needle || '%'` fallback for CJK. `HybridMemoryStore` may prepend cosine hits (≥ 0.45) when the embedder is ready, then fill with those keyword hits, dedupe by `id`, cap at `limit`.
- `HybridMemoryStore.search` must **not** `await backfillMissing()`. Use rows that already have embeddings; if any `embedding` is NULL and `idleScope` is set, `launch` backfill on that scope. App start also `launch(Dispatchers.Default) { backfillMissing() }`. First search after a pack becomes ready may miss old NULL rows until idle backfill finishes.
- Token budget for LLM inject is applied in `LoopEngine` (max 5 facts / 800 chars), not in SQL.
- Do not brute-scan `list()` for vector search beyond the PRD <1K fact budget. No sqlite-vec.

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
- `AgentTask.conversationId` lives in `snapshot_json` (default `"default"` when missing). `listByConversation` scans all snapshots in `updated_at ASC` and filters in memory — do not add a SQL column in v1, and do **not** impersonate the current thread with `listRecent(50)`.
- `TaskStore.searchCompletedTurns(query, excludeTaskIds, limit=3)` scans all snapshots newest-first (`SELECT task_id, snapshot_json … ORDER BY updated_at DESC`, same full-table decode as `listByConversation`). Match `conversationSearchNeedles(query)` against `input` + `finalAnswer` only (`COMPLETED`, non-blank `finalAnswer`, `taskId` not in exclude). Needles are Latin tokens `≥2` and whole CJK runs `≥2` — **not** memory `searchNeedles` overlapping bigrams (`这个` from 「uno这个玩法」 must not pull 刘备/咖啡闲聊). Drop stopword-only runs (`这个` / `什么` / …). Empty needles / blank query → empty list. **Do not** `snapshot_json LIKE`, add FTS, or bump `dougie_tasks.db`. Hits are not written to `memory_facts`.
- `TaskStore.searchHistory(query, limit)` uses that same newest-first full-table decode and the same `conversationSearchNeedles`. Match `COMPLETED` **or** `FAILED` against `input` + `finalAnswer` + `lastError` (`matchesHistoryNeedles`; no `toolTrace`). Empty needles / blank query → empty list, not `listRecent(50)`. History UI may UNION title-matched windows; the store does not see prefs titles. **Do not** change `searchCompletedTurns`, `snapshot_json LIKE`, add FTS, or bump `dougie_tasks.db`.
- `TaskStore.deleteByConversation(id)` uses that same full-table snapshot scan (not `listRecent(50)`), collects matching `task_id`s, then `DELETE FROM agent_tasks WHERE task_id IN (?,?,…)`. Blank or `"default"` returns 0 and deletes nothing. Empty IN does not run SQL. Do **not** add a `conversation_id` column or bump `dougie_tasks.db`. Do not GC `memory_facts`, `audit_log`, or `idempotency`.
- `TaskStore.deleteByTaskId(taskId)` deletes that primary key (`DELETE FROM agent_tasks WHERE task_id=?`). Blank → 0. InMemory also drops `recentIds`. Do not GC `memory_facts`, `audit_log`, or `idempotency`.
- `AgentTask.priorTurns` is instantaneous LLM context. `TaskSnapshotCodec` **must omit** it; do not write history text into `snapshot_json`.
- `AgentTask.startedAt` / `endedAt` (epoch ms) live in `snapshot_json` only. `TaskManager.submit` writes `startedAt`; persist / `markCancelled` / `recoverInterrupted` call `stampEndedAtIfTerminal` (set `endedAt` once, never overwrite). `confirmDeadlineAt` is also optional in the same codec (put only when non-null); LoopEngine writes it on `AWAITING_CONFIRMATION` and clears it on confirm / reject / cancel / timeout / interrupt. Do **not** add SQL columns, do **not** bump `dougie_tasks.db`, and do **not** use `updated_at` as duration or completed-at. Old snapshots missing keys stay null (History omits duration and completed-at; Confirm overlay omits the countdown line).
- Current open conversation id is prefs `current_conversation_id`, not a `conversations` table. Custom window titles are prefs `conversation_titles_json` (JSON object id→name). Do **not** add a `conversations` table or `conversation_id` SQL column, do **not** bump `dougie_tasks.db`, and do **not** put titles on `AgentTask` / `TaskSnapshotCodec` / `priorTurns`. `TaskManager.deleteConversation` deletes store rows, then `setTitle(id, "")`. If the pointer is that id, switch to `"default"` and load it (`openConversation` semantics). Busy or `"default"` is a no-op.
- `TaskManager.deleteTask(taskId)` is busy → null (including deleting an old card). Otherwise `deleteByTaskId`. If that `conversationId` then has 0 rows and is not `"default"`: `setTitle("")`, and if the pointer is that id switch to `"default"` and load it. If the live `_task.taskId` was deleted, seed the current window's latest remaining row or null and `reloadTranscript`. Deleting a past row in the current window still `reloadTranscript`. Default emptied stays `"default"` (empty default; do not clear the default title). Do not log input / titles / `snapshot_json`.
- App start: `recoverInterrupted(store)` — if the latest row is not COMPLETED/FAILED, mark FAILED with `UserFacingErrors.INTERRUPTED`. `TaskManager.seed` only when that task’s `conversationId` equals the current prefs pointer. Do **not** resume the LLM stream.
- `calendar_create` reads/writes `idempotency` via `IdempotencyStore` (INSERT OR IGNORE). A new tool instance with the same store must not call `CalendarPort.createEvent` again for the same key.
- `AuditLog.record` writes only `task_id`, `tool_name`, `outcome`, `created_at`. Never prompt text, keys, calendar titles, clipboard, coordinates, or image bytes.
- `AuditLog.listRecent(limit)` (default 50) returns `AuditEntry` rows newest first (`ORDER BY created_at DESC, id DESC`). `NoOpAuditLog` and SAM lambdas default to empty.

JVM tests use `InMemoryTaskStore` / `InMemoryIdempotencyStore` / `NoOpAuditLog`. `TaskManager` defaults `taskStore` to null (no persist).

## Migrations

`dougie_memory.db` version 2 is additive: `onUpgrade(1→2)` is `ALTER TABLE memory_facts ADD COLUMN embedding BLOB` only. **Do not DROP** — v0.1.0 already shipped version 1 databases. `dougie_tasks.db` remains version 1 create-only (`onUpgrade` drops and recreates). Replace task-db drops with additive migrations before shipping a second version.

## Naming Conventions

- Tables: snake_case (`memory_facts`, `agent_tasks`, `idempotency`, `audit_log`)
- Columns: snake_case; Kotlin models use camelCase (`createdAt`, `taskId`)
- Database files: `dougie_memory.db`, `dougie_tasks.db`

## Common Mistakes

- Putting Room / `android.database` in `:core:*` — keep JVM tests on in-memory stores.
- Logging MATCH queries, fact text, embedding blobs, or `snapshot_json` (may contain tool args).
- Awaiting `backfillMissing()` inside `search` (blocks Loop). Idle Default only.
- Treating `embed()` empty `FloatArray` as success — that writes an empty BLOB and blocks backfill; Hybrid must fall back to keyword. Device paraphrase AC needs the BGE pack (`AndroidEmbeddingPort`); JVM synonym tests stay Fake. Hash-bag is JVM-only, not a downloadable pack.
- Auto-continuing an interrupted task with a new LLM call.
- Writing calendar event bodies or clipboard text into `audit_log`.
- Using `updated_at` as task duration or completed-at. Wall-clock duration is `endedAt - startedAt` inside `snapshot_json`; History completed-at is `formatCompletedAt(endedAt)`.
- Silent Fake LLM on the app chat path.
- Adding a `conversation_id` SQL column (or bumping `dougie_tasks.db`) to delete a window or search history. Scan `snapshot_json` like `listByConversation` / `searchCompletedTurns` / `searchHistory`.
- Using SQL `LIKE` on `snapshot_json` for Chat history search or History page search.
- Reusing memory `searchNeedles` (CJK bigrams) for `searchCompletedTurns` / `searchHistory`. 「uno这个玩法」 must not cite 刘备/咖啡 because `这个` appears in those answers.
- Treating History `searchHistory` empty needles as `listRecent(50)`.
- Deleting `"default"` rows with `deleteByConversation`, or sweeping Memory / `audit_log` / `idempotency` when a window or a single task is removed. Per-card `deleteByTaskId` of a default-window row is allowed.
