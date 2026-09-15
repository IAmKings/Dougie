# Design: 任务卡完成时刻

## Boundary

只改 `:feature:history` 映射与元信息行。不改 codec、DB、Chat、Debug。

## Formatter

```kotlin
fun formatCompletedAt(
    endedAt: Long?,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String?
```

- `endedAt == null` → null
- 本地 `LocalDate` 比较：`0` 天 → `今天 HH:mm`；`1` 天 → `昨天 HH:mm`；同年 → `M月d日 HH:mm`；否则 `yyyy年M月d日 HH:mm`
- `HH:mm` 用该时刻的本地钟点，秒丢弃（`DateTimeFormatter.ofPattern("HH:mm")`）
- `toHistoryItem` 用默认 now/zone；测试传入固定 `nowMs` + `ZoneId.of("Asia/Shanghai")`

`HistoryItem.completedAtLabel: String? = null`。元信息 `listOfNotNull(durationLabel, providerLabel, completedAtLabel)`。

## Don't

- `DateUtils.getRelativeTimeSpanString`
- `updated_at`
- 在 Compose 里 `SimpleDateFormat`
- 依赖 `:core:runtime` 的日历 formatter
