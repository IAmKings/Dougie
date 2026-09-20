# Design: 用户气泡语音来源标注

## Boundaries

| Module | Owns |
|--------|------|
| `:feature:chat` | `UserMessage.sourceLabel` 映射与绘制 |
| `:app` / ASR | 不改 `voiceUsedThisDraft` → `speakReply` |
| `:core:model` / codec | 不新增字段；继续用已有 `speakReply` |

## Data flow

```
AgentTask.speakReply
  → UserMessage.sourceLabel = "语音转写" | null
  → UserBubble caption under bubble, Arrangement.End
```

`sharedBoundsFor(userBubbleSharedKey)` stays on the bubble `Text` modifier, not the caption.

## Contracts

```
data class UserMessage(
  text: String,
  listKey: String,
  sourceLabel: String? = null,
)
```

Copy locked: `语音转写`. Do not prefix `来源：` (that is Agent citation). Do not put transcript text on the caption.

## Compatibility

Old snapshots without `speakReply` decode as false → no label. Keyboard send unchanged. TTS autoplay still uses `speakReply`.

## Risks

Mixed typed+voice in one draft still sets `speakReply` (existing). Label will show; acceptable, not a new flag.
