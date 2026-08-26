# Design: 本机闹钟 + 通知开 Chat

## Boundaries

| Piece | Where |
|-------|--------|
| Persist + AlarmManager + BootReceiver + notify | `:app` `src/main`（Play/sideload 共用） |
| Settings 列表 UI | `:app` slot（同 `shortcutLayer`），**不要**把 `AlarmManager` 放进 `:feature:settings` |
| 预填输入框 | `:feature:chat` `ChatRoute(initialDraft)`；MainActivity 用 `scheduleId` 查 store |
| Loop | 不改 `TaskManager.submit` 门闩 |

不引入 WorkManager 依赖。每日重复 = 响一次再 `set` 次日，不是 `PeriodicWorkRequest`。

## Data

`ScheduleItem(id, hour, minute, daily, draft, oneShotEpochMillis?)`

- 一次性：存触发墙钟 `epochMillis`（本地时区换算）。响后删除。
- 每日：只存 `hour`/`minute`；下次 = 今天该时刻若已过则为明天。
- 文本文件 `filesDir/dougie_schedules.txt`（字段分隔 + 草稿 Base64；不进 `ProviderSettings` / 不跟 API key 同一 Encrypted 文件）。到点先把草稿写入 `dougie_schedule_pending.txt` 再删一次性条目，点通知用 id 取出预填。
- 上限 8。

## Alarms

- `AlarmManager` + `BroadcastReceiver` `DougieScheduleReceiver` `exported=false`。
- 能精确则 `setExactAndAllowWhileIdle`；否则 `setAndAllowWhileIdle`，设置页提示「系统可能推迟」。
- Manifest：`RECEIVE_BOOT_COMPLETED`、`SCHEDULE_EXACT_ALARM`（不声明 `USE_EXACT_ALARM`）。
- `BootReceiver` 重读 JSON 并全部 `set`。
- Alarm `PendingIntent`：API 31+ `FLAG_IMMUTABLE`（广播）。通知点击：`FLAG_IMMUTABLE` + `OPEN_CHAT` + `EXTRA_SCHEDULE_ID`（UUID 字符串，**不是** prompt）。

## Notifications

- 新渠道 `dougie_schedule`，名称「定时提醒」，id **49**（任务进度 48、截屏 FGS 47）。
- Title **Dougie**；body `定时提醒`（可加时刻 `07:30`，不加草稿）。
- 不挂 BubbleMetadata（避免再踩 immutable 气泡坑；任务进度气泡保持现状）。
- API 33+ 无 `POST_NOTIFICATIONS` 则到点不崩、不发。

## Chat prefill

- `ChatLaunch.EXTRA_SCHEDULE_ID`
- `requestsChat` 仍为 `OPEN_CHAT`；有 scheduleId 时同样切到 Chat。
- `ChatScreen` composer：`LaunchedEffect(initialDraft)` 写入 `draft` 一次；消费后 MainActivity 清 extra，避免旋转重复覆盖用户编辑。
- 查不到 id → 空白输入框。

## Leak / rollback

- `checkChannelLeak` 不必新增 overlay 针。Play 允许 `RECEIVE_BOOT_COMPLETED` / `SCHEDULE_EXACT_ALARM`。
- 回滚：删 Receiver/权限/JSON/设置 slot；Chat extra 还原。
