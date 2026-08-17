# Design: Phase 5i llama NDK

## Boundaries

| Piece | Location |
|-------|----------|
| Optional CMake | `:tool:system` when `third_party/llama.cpp` exists |
| `llama_jni.cpp` | `tool/system/src/main/cpp/` |
| Kotlin | unchanged `LlamaJni` |

## Contracts

`OUTPUT_NAME llama` so `System.loadLibrary("llama")` works.

Sampling: temp 0.7, top-p 0.8, presence 1.5, CPU only.

Cache `llama_model` per path; new `llama_context` per complete.

## Rollback

Remove `externalNativeBuild` block; JNI file unused.
