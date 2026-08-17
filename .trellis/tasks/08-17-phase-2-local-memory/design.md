# Design — Phase 2 Local Memory FTS

## Boundaries

```text
:core:model        MemoryEntry, MemoryCandidate, GateResult, AgentTask.retrievedMemories
:core:memory       MemoryStore, MemoryGate, InMemoryMemoryStore (JVM)
:data:memory       Room + FTS, RoomMemoryStore : MemoryStore
:data:preferences  memoryEnabled (default true)
:core:runtime      LoopEngine search-before-LLM; after COMPLETED call Gate
:core:llm          include retrievedMemories in OpenAI messages
:feature:memory    Viewer UI
:app               Wire Room store, Nav Chat/Settings/Memory
```

`:core:memory` is Kotlin JVM only. Room stays in `:data:memory`.

## Contracts

```kotlin
data class MemoryEntry(
  val id: String,
  val type: String,          // "fact"
  val content: String,
  val source: String,        // user quote or taskId
  val confidence: Float,
  val createdAt: Long,
  val updatedAt: Long,
  val embedding: ByteArray? = null,
)

interface MemoryStore {
  suspend fun search(query: String, limit: Int = 5): List<MemoryEntry>
  suspend fun upsert(entry: MemoryEntry)
  suspend fun list(): List<MemoryEntry>
  suspend fun delete(id: String): Boolean
  suspend fun clear()
}

class MemoryGate(private val store: MemoryStore, private val enabled: () -> Boolean) {
  suspend fun ingest(userInput: String, assistantText: String?, sourceTaskId: String): GateResult
}
```

`GateResult`: `Stored` | `SkippedSensitive` | `SkippedDisabled` | `SkippedDuplicate` | `SkippedNoFact`.

Cheap extract: keep userInput if it looks like a durable self-fact (contains 我叫/我是/我住/我喜欢 and length < 200), else skip. Also skip tool-only battery questions. This is intentionally heuristic for MVP.

FTS: Room `@Fts4` on `content` (FTS4 is the Room-stable default; FTS5 via `FTS4` tokenizer or `@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)`). Prefer FTS4 unicode61 so Chinese unigrams/keywords still MATCH; if MATCH is weak for CJK, also filter `content.contains` as fallback in search.

## Data flow

```text
submit(input)
  → search memories (if enabled)
  → task.retrievedMemories
  → OpenAI system: "Known facts:\n- ..."
  → loop ...
  → COMPLETED
  → MemoryGate.ingest(input, finalAnswer, taskId)
```

## UI

- Bottom nav 记忆 selected on Memory route.
- List: content + source + relative time.
- Edit: dialog/text field save upsert.
- Delete swipe or icon; 清空 confirmation.
- Switch 启用记忆.

Colors: copy DougieColors tokens (do not extract `:core:ui` unless needed).

## Trade-offs

| Choice | Why |
|--------|-----|
| Facts table only | Proves §15 Phase 2 without conversation schema |
| Heuristic Gate not LLM | PRD forbids per-turn LLM judge |
| Room in :data:memory | Keep core JVM-pure |
| InMemory for tests | No Robolectric required for Gate |

## Rollback

Feature flag `memoryEnabled=false` disables write+inject. Remove Memory route if UI is broken.
