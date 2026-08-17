# Design: Phase 5m eval harness

## Boundaries

| Piece | Module |
|-------|--------|
| `cer`, accuracy | `:core:tool` JVM |
| Tiny gold JSON | `core/tool/src/test/resources/eval/` |
| Optional full set | gitignored `eval/` at repo root |

No Android, no OkHttp, no AgentTool.

## Contracts

- CER = Levenshtein(chars) / max(1, ref.length); both sides NFC-normalized, whitespace stripped for Chinese-centric compare (keep digits/latin).
- Intent item: `{ "id", "goldIntent", "modelJson" }` for parser tests without native complete.
- Report: `passed = cer <= 0.05` / `accuracy >= 0.90` on the **fixture** set (not a claim that production 500-clip is done).

## Rollback

Delete eval helpers and test resources.
