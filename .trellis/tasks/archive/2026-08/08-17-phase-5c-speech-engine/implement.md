# Implement: Phase 5c Speech Engine

1. Add `AsrModelLayout`, `SpeechEngine`, `SpeechRecorder`, `SpeechSession` + JVM tests.
2. `SpeechInputTool` treats blank transcript as empty-speech error.
3. `AudioRecordSpeechRecorder`; `AndroidSpeechPort` delegates to `SpeechSession`.
4. Gitignore `*.onnx` and `**/jniLibs/`.
5. Update directory-structure + error-handling.
6. `JAVA_HOME` JDK 17; `./gradlew :core:tool:test :app:checkChannelLeak`.
