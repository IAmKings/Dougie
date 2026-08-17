# Implement: Phase 5e Speech Output

1. `TtsEngine` / `PreferOfflineTtsPort` / `SpeechOutputTool` + JVM tests.
2. `AndroidSystemTtsEngine` (reject network voices).
3. Wire in `DougieApplication`.
4. Spec: directory-structure, error-handling, logging (no spoken text in AuditLog — already).
5. JDK 17: `./gradlew :core:tool:test :app:checkChannelLeak`.
