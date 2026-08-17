# Implement: Phase 5k

1. Fix `ModelInstaller` to rethrow `CancellationException`; make `OkHttpModelGet` cancellable.
2. Add catalog type (id, title, sizeLabel, `ModelPack`) with injected HTTPS/SHA-256.
3. `SettingsViewModel`: per-row status (missing / configured / downloading / installed / error), confirm gate, progress, cancel.
4. `SettingsScreen`: 离线模型 section + confirm dialog (Memory-style `AlertDialog`).
5. Wire in `MainActivity` / `DougieApplication`.
6. Tests: JVM installer cancel; settings VM or catalog tests with fake GET (unconfirmed does not fetch).
7. `./gradlew :core:tool:test :feature:settings:testPlayDebugUnitTest :app:checkChannelLeak` with JDK 17.

## Validation

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :core:tool:test :feature:settings:testPlayDebugUnitTest :app:checkChannelLeak
```

## Risky files

- `ModelInstaller.kt` catch-all (must not eat cancel)
- `SettingsViewModel.Factory` signature (MainActivity)
- Do not register a download AgentTool
