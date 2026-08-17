# Implement: Phase 5i llama NDK

1. `llama_jni.cpp` + `CMakeLists.txt`.
2. Optional `externalNativeBuild` in `:tool:system`.
3. gitignore `third_party/llama.cpp`; spec note.
4. JDK 17: `./gradlew :core:tool:test :app:checkChannelLeak` without llama.cpp tree.
