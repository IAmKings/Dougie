# Implement: Phase 5b SpeechInput

1. `AndroidPermissions.RECORD_AUDIO` + `UserFacingErrors` for foreground / model / engine.
2. `SpeechPort` + `SpeechInputTool` + JVM tests + PolicyEngine test.
3. `AndroidSpeechPort` in `:tool:system`; never call `AudioRecord`.
4. Manifests, Permissions UI, `DougieApplication` register.
5. Spec: directory-structure, error-handling, logging (no PCM/audio).
6. Validate: `JAVA_HOME` JDK 17; `./gradlew :core:tool:test :core:runtime:test :app:checkChannelLeak`.
