# Implement: Quick Settings Tile

## Order

1. Extract `chatLaunchIntent` + flags/extras constants in `:app`.
2. Add JUnit in `app/src/test` (Roboelectric not required): assert component, flags, extra, absence of key-like extras. Enable `testImplementation` junit if `:app` has no test config.
3. Add `DougieChatTileService`; wire `onClick` → collapse + start activity. Handle API 34 PendingIntent if required to compile.
4. Manifest: TileService + `MainActivity` `singleTop`.
5. Hoist Chat route so Tile extra forces `AppRoute.Chat` on `onCreate`/`onNewIntent`.
6. Read `checkChannelLeak` in `app/build.gradle.kts`; if TileService trips it, fix the scanner (must still catch Accessibility / TapSwipe / models).
7. Build play + sideload Debug; confirm merged manifests list the service.

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak
```

If `extractDebugAnnotations` fails, add `-x extractDebugAnnotations`.

Manual (device): 编辑快捷设置 → 添加 Dougie → 从设置页点 Tile → 应回到对话。

## Risky files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/dougie/app/MainActivity.kt`
- `app/build.gradle.kts` (`checkChannelLeak`)

## Rollback

Revert the files above. Do not ship a Tile that calls `TaskManager.submit`.

## Before `task.py start`

- Parent map updated; this child has prd/design/implement.
- `implement.jsonl` / `check.jsonl` curated (not seed-only).
- User approved this planning summary.
