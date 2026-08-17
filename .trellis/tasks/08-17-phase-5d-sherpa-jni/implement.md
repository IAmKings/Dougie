# Implement: Phase 5d Sherpa JNI

1. Add `SherpaSpeechEngine` + JVM tests.
2. Vendor trimmed sherpa Kotlin JNI bindings; `SherpaJni.load/decode`.
3. `AndroidSpeechPort` default engine = SherpaSpeechEngine.
4. Spec: directory-structure.
5. `./gradlew :core:tool:test :app:checkChannelLeak` (JDK 17).
