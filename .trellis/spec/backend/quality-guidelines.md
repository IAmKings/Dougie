# Quality Guidelines

> How JVM/core and Android-port code is verified in Dougie. Documented from the repo as it is — there is no ktlint, detekt, GitHub Actions, or coverage gate.

## Overview

- Language: Kotlin 2.0.21, JVM 17 (`compilerOptions.jvmTarget` / `kotlinOptions.jvmTarget = "17"`). Gradle commands need OpenJDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` on this machine).
- Tests: JUnit 4 (`org.junit.Test`) + `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `advanceUntilIdle`). HTTP: OkHttp `MockWebServer` in `:core:llm`.
- There is **no** ktlint, detekt, Android Lint `lint {}` block, or CI workflow. Reviewers run Gradle on the touched modules plus `:app:checkChannelLeak` when Play/Sideload classpath, manifests, or model assets change.
- User-visible failure copy is Chinese constants on `UserFacingErrors` (`core/model/.../AgentException.kt`). Tests assert those strings, not English paraphrases.

## Forbidden Patterns

- `android.*` inside `:core:*` (breaks JVM tests and `:cli` reuse). Put `BatteryManager` / `CalendarContract` / `ClipboardManager` / MediaProjection / JNI loaders in `:tool:system` or `:tool:accessibility`.
- Silent `FakeLlmProvider` on the app chat path. Fake is for JVM tests (`LoopEngineTest`) and `:cli` only.
- Mapping `CancellationException` / OkHttp `call.cancel()` to `LLM_FAILED` or `MODEL_DOWNLOAD_FAILED`. Rethrow cancel; `TaskManager.cancel()` is `UserFacingErrors.CANCELLED`.
- Committing weights or native blobs: `*.onnx` (except the tiny testdata allowlisted in `.gitignore`), `*.gguf`, `**/jniLibs/`, `third_party/llama.cpp/`, `/eval/` wav dumps.
- Logging prompts, keys, HTTP bodies, PCM, transcripts, fact `content`, or `snapshot_json` — see `logging-guidelines.md`.
- Registering `ModelInstaller` / `ModelImporter` as `AgentTool`. The LLM must not pick download URLs.
- Putting `TapSwipeTool` or `DougieAccessibilityService` on the Play classpath. `checkChannelLeak` fails if play merged manifest contains `AccessibilityService` / `TapSwipeTool` / `NotificationListenerService` / `SYSTEM_ALERT_WINDOW` / `DougieOverlayService` / `TYPE_APPLICATION_OVERLAY`, or the play APK contains `models/asr`, `models/tts`, `*.onnx`, `models/intent`, or `*.gguf`. It also fails if play or sideload merged manifest is missing `DougieChatTileService` or `android.service.quicksettings.action.QS_TILE`, or if sideload is missing `SYSTEM_ALERT_WINDOW` / `DougieOverlayService`. Manifest `POST_NOTIFICATIONS` is **not** a leak.
- `com.android.library` on `:core:*`. Same for `:cli`: no Android plugin, not in the APK, mosaic **0.14.0** only (0.18.0 is Kotlin 2.2 metadata vs repo 2.0.21).

## Required Patterns

- New loop/gateway/sanitizer tests go in `:core:runtime/src/test`. Provider SSE tests go in `:core:llm/src/test`. Tool contracts go in `:core:tool/src/test` with `Fake*Port` doubles, not Android instrumentation. CLI console tests go in `:cli/src/test`.
- Test method names describe the contract (`fakeTaskCompletesAfterExactlyThreeToolLoops`, `cloudProviderBlockedWhenAllowCloudFalse`).
- Inject `Dispatchers.Default` (or a test dispatcher) into `LoopEngine` / `TaskManager`. Never run the loop on Main.
- Tool JSON is the contract: success payloads stay small (`battery_percent`, `ok`+`backend`, `capture_id` without pixels). Unknown tools and uncoercible args fail via `ToolCallSanitizer` before `execute`.
- Play vs sideload: `sideloadImplementation(project(":tool:accessibility"))` only. Play `ChannelHooks.seedBundledModels` is a no-op.

## Testing Requirements

| Change | Minimum tests | Command (`JAVA_HOME` OpenJDK 17) |
|--------|---------------|------------------|
| Loop / gateway / policy / sanitizer | Named cases in `LoopEngineTest`, `EgressGatewayTest`, `PolicyEngineTest`, `ToolCallSanitizerTest` | `./gradlew :core:runtime:test` |
| OpenAI SSE / vendor body | `OpenAICompatibleProviderTest` | `./gradlew :core:llm:test` |
| Tool JSON, gates, model install/import | Matching `*ToolTest` / `ModelInstallerTest` / `ModelImporterTest` | `./gradlew :core:tool:test` |
| Memory gate / FTS behavior | `MemoryGateTest`; Android SQLite stays in `:data:memory` | `./gradlew :core:memory:test` |
| Vendor presets | `LlmVendorsTest` | `./gradlew :core:model:test` |
| Play/Sideload leak, Tile, notice, overlay, or model assets | `checkChannelLeak` (Tile both flavors; Play has no Accessibility / TapSwipe / NotificationListener / overlay permission or `DougieOverlayService`; sideload has overlay). Shade copy: `TaskNoticeTest`. Play settings copy: `PlayShortcutCopyTest` | `./gradlew :app:testPlayDebugUnitTest` and `./gradlew :app:checkChannelLeak` |
| `:cli` Agent Console | `FakeBatteryLoopTest` (3-loop fake battery + `--log-only` flag) | `./gradlew :cli:test` and `./gradlew :cli:run --args='--log-only'` |

There are no Compose UI / Espresso tests and no jacoco threshold. Do not add a CI lint job as a substitute for the module tests above.

Full-eval ASR (`eval/asr/*.wav`, CER ≤ 5%) is gitignored. `FullEvalSet.isPresent()` skips when missing; fixture `passed` is not a claim that Rule D is done.

## Code Review Checklist

- [ ] `:core:*` still JVM-only; Android types stayed in `:tool:*` / `:data:*` / `:app`; `:cli` has no Android plugin and is not on the APK classpath
- [ ] `lastError` is a `UserFacingErrors` Chinese string, not a stack trace or HTTP body
- [ ] No new Logcat of prompts, keys, tool secret args, audio, or facts
- [ ] Cancel / timeout paths do not become `LLM_FAILED`
- [ ] Play APK cannot see Accessibility, NotificationListener, overlay (`SYSTEM_ALERT_WINDOW` / `DougieOverlayService`), or ONNX/GGUF; both flavors still declare the QS Tile; sideload declares overlay; `POST_NOTIFICATIONS` is allowed (run `checkChannelLeak` if flavors, manifests, assets, or JNI changed)
- [ ] Tests use Fake ports / Fake LLM / MockWebServer, not live cloud
- [ ] New user-facing strings added to `UserFacingErrors` and asserted in a test
