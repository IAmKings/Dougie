# Implement: Phase 5l

1. Document gitignored sideload asset dirs; optional `!tokens.txt` fixtures if useful.
2. JVM-testable copy helper (or Android `AssetManager` wrapper with fake FS in tests).
3. Call seed from sideload `DougieApplication` / `ChannelHooks` only — play no-op.
4. Extend `checkChannelLeak` (or sibling) to fail if play APK contains `models/asr` or `.onnx`.
5. `./gradlew :core:tool:test :app:checkChannelLeak` JDK 17.

## Risky files

- `app/build.gradle.kts` sourceSets — must not attach models to play
- gitignore — do not force-add `*.onnx`
