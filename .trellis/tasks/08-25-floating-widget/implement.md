# Implement: 渠道拆分悬浮 / 气泡

## Order

1. Extend `checkChannelLeak` **first** (play forbids overlay permission/class; will fail until sideload files exist — add sideload files in the same change set).
2. `app/src/sideload/AndroidManifest.xml` merge: `SYSTEM_ALERT_WINDOW` + `DougieOverlayService`.
3. Sideload overlay service + toggle via `ChannelHooks` settings slot from `MainActivity`/`SettingsRoute` callback (do not put overlay types in `:feature:settings`).
4. Play: `TaskProgressNotifier` BubbleMetadata when `!IS_SIDELOAD` && API 29+.
5. Play settings copy: 系统通知气泡（自行在系统设置开启）. Zero sideload URLs/package names.
6. JVM tests: `formatTaskNotice` unchanged; optional test that play strings file has no `sideload` / `上层显示` if those live in sideload resources only.

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak
```

Manual Play: task notice; system may offer bubble. Manual sideload: enable overlay + permission; ball over another app; tap Chat.

## Risky files

- `app/build.gradle.kts` (`checkChannelLeak`)
- `app/src/sideload/` (new)
- `TaskProgressNotifier.kt` (play bubbles only)
- Settings wiring / `ChannelHooks`

## Before `task.py start`

User approved this planning summary.
