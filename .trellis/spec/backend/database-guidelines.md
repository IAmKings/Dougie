# Database Guidelines

> Local persistence for Dougie Phase 2 memory. Task history Room recovery is Phase 4 and is not in this schema.

## Overview

- `:core:memory` is Kotlin JVM only. It owns `MemoryStore` / `MemoryGate` / `InMemoryMemoryStore`.
- Android persistence lives in `:data:memory`. `RoomMemoryStore` implements `MemoryStore` (class name kept from the design; the implementation is `SQLiteOpenHelper`, not androidx Room).
- This phase uses **SQLiteOpenHelper + FTS4 `tokenize=unicode61`**. Catalog entries for Room/KSP exist in `gradle/libs.versions.toml` for a later swap; `:data:memory` must not depend on them until the module actually uses Room.
- File: `dougie_memory.db`, version `1`.
- Do not log `content`, `source`, or FTS query strings (`logging-guidelines.md`).

## Schema

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

## Query Patterns

- `list()`: `SELECT ... FROM memory_facts ORDER BY updated_at DESC`
- `search(query, limit)`: FTS `MATCH` on each `searchNeedles(query)` token, **plus** `content LIKE '%' || needle || '%'` fallback for CJK. Needles = whitespace tokens + full query + adjacent CJK bigrams.
- `upsert` / `delete` / `clear` keep FTS in sync. UI edit calls `upsert` directly (Gate is only for conversation ingest).
- Token budget for LLM inject is applied in `LoopEngine` (max 5 facts / 800 chars), not in SQL.

## Migrations

Version 1 is create-only. `onUpgrade` drops `memory_facts_fts` then `memory_facts` and recreates. Replace with additive migrations before shipping a second version.

## Naming Conventions

- Tables: snake_case (`memory_facts`, `memory_facts_fts`)
- Columns: snake_case; Kotlin `MemoryEntry` uses camelCase (`createdAt`)
- Database file: `dougie_memory.db`

## Common Mistakes

- Putting Room / `android.database` in `:core:*` — keep JVM tests on `InMemoryMemoryStore`.
- Logging MATCH queries or fact text.
- Skipping Gate on conversation ingest (passwords / `sk-` / `密码是` must not land in `memory_facts`).
- Treating disabled memory as “wipe”: `memoryEnabled=false` stops ingest and LLM inject; rows remain until the user deletes or clears them.
