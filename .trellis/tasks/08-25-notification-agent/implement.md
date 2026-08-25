# Implement: 任务进度通知

## Order

1. Pure `formatTaskNotice(AgentTask?): String?` + JUnit in `app/src/test` (no `Intent` extras).
2. `AndroidPermissions.POST_NOTIFICATIONS` if used by permissions module.
3. Manifest `uses-permission POST_NOTIFICATIONS`.
4. `TaskProgressNotifier` + start collect in `DougieApplication.onCreate` after `TaskManager` exists.
5. Permission Center row API 33+; wire request like calendar in `PermissionsScreen`.
6. One-shot request from `MainActivity` when `task` is busy and permission missing (API 33+).
7. Confirm capture notification id/channel do not collide.

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testPlayDebugUnitTest :feature:permissions:testDebugUnitTest :app:checkChannelLeak
```

`:feature:permissions` may still have no tests — then skip that module or add a mapper test only if you extract item-building. Do not add Compose UI tests.

Manual: send a chat, leave app, see status line; deny notification permission, app still chats; tap notice → Chat.

## Risky files

- `DougieApplication.kt` (lifecycle / leak)
- `PermissionsViewModel.kt` / `PermissionsScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `checkChannelLeak` (must stay Listener-free)

## Before `task.py start`

User approved this planning summary.
