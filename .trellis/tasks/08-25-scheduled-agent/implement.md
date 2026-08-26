# Implement: 定时提醒

## Order

1. `ScheduleStore` + 纯函数 `nextTriggerEpoch` / `formatScheduleNotice`（JVM 测：过点进明天、一次性删除语义、notice 不含草稿）。
2. `DougieScheduleReceiver` + `ScheduleAlarms` + manifest 权限/receiver。
3. `ChatLaunch` extra + `ChatLaunchTest`（id extra 名不含 `prompt`/`key`）。
4. `ChatRoute(initialDraft)`；MainActivity 解析 extra。
5. Settings slot：时间（时/分）、草稿字段、每日重复 Switch、列表删除；保存后 `ScheduleAlarms.sync`。
6. `Application.onCreate` / Boot：`sync`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testPlayDebugUnitTest :feature:chat:testDebugUnitTest :app:checkChannelLeak
```

真机：一次性到点通知 → 点进 Chat 见草稿、未自动发送；每日开关后改系统时间或等次日；重启后仍注册。

## Risky files

- `app/src/main/AndroidManifest.xml`
- `ChatLaunch.kt` / `ChatScreen.kt` / `MainActivity.kt`
- 新 Receiver（勿 `exported=true`）

## Before `task.py start`

User approved this planning summary.
