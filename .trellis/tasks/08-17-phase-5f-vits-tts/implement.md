# Implement: Phase 5f VITS TTS

1. `TtsModelLayout` / `SherpaTtsEngine` + JVM tests (layout + native gate + offline preferred).
2. Trimmed `Tts.kt` bindings; `SherpaJni.speak` (generate + AudioTrack).
3. Wire `DougieApplication` offline engine to `SherpaTtsEngine`.
4. Spec: directory-structure (tts layout, no ONNX in git).
5. JDK 17: `./gradlew :core:tool:test :app:checkChannelLeak`.
