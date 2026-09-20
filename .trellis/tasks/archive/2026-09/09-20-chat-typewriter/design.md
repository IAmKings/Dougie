# Design: Chat 终答打字机

## Boundaries

| Module | Owns |
|--------|------|
| `:feature:chat` | `nextTypewriterShown`（或同名）谓词 + Agent 气泡显示切片 |
| LoopEngine / LLM | 不改 `streamingText` / `finalAnswer` |

## Contracts

```
fun nextTypewriterShown(
  shown: String,
  target: String,
  firstFrame: Boolean,
  reduceMotion: Boolean,
  elapsedMs: Long,
): String
```

- `firstFrame || reduceMotion` → `target`
- `target == shown` → `shown`
- `target.startsWith(shown)` && `target.length - shown.length` is a small SSE step (e.g. ≤ 8 code points) → `target` (no extra pace)
- `target.startsWith(shown)` && larger jump → reveal `shown` + step(elapsed) up to `target`, step sized so remaining ≤ 800ms at ~24ms/scalar
- else (not a prefix) → `target`

SSE “small step” avoids delaying token stream; COMPLETED dump is a large jump.

`remember(listKey)` holds `shown`. First composition of that key in this ChatScreen is `firstFrame`.

TTS / 播报 uses `item.text` (full target), not `shown`.

## Compatibility

`listKey`、进入动效、citations、durationLabel 不变。Past `toPastChatItems` still full text; firstFrame snaps.

## Risks

CJK vs UTF-16 surrogates: iterate Unicode scalars (`codePointCount` / `offsetByCodePoints`), not raw `Char` index, so emoji/surrogates do not split.
