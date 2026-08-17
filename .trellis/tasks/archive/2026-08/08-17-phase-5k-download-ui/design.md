# Design: Phase 5k download UI

## Boundaries

| Piece | Module |
|-------|--------|
| Catalog + sizes + pack ids | `:core:tool` (`OfficialModelCatalog` shape; URLs injected) |
| `ModelInstaller` / SHA-256 | `:core:tool` (5j) |
| OkHttp GET + cancel | `:tool:system` `OkHttpModelGet` |
| Settings rows + confirm dialog | `:feature:settings` |
| Wire `filesDir` + installer + catalog | `:app` `DougieApplication` / `MainActivity` |

`:feature:settings` must not open OkHttp. It receives `ModelInstaller` + destRoot + catalog.

## Data flow

```
User taps 下载 → AlertDialog (size) → confirm
  → ViewModel sets userConfirmed
  → ModelInstaller.install (coroutine)
  → OkHttpModelGet writes .part + onProgress
  → rename; layout isPresent → UI 已安装
Cancel → cancel coroutine → Call cancel / ensureActive → delete .part
```

## Contracts

- Pack ids: `asr`, `tts`, `intent`. `relativeDir` matches `AsrModelLayout.DIR` / `TtsModelLayout.DIR` / `IntentModelLayout.DIR`.
- File names match layouts (`model.int8.onnx`+`tokens.txt`; TTS three files; `model.gguf`).
- `install` must **rethrow** `CancellationException` (today it is swallowed into `MODEL_DOWNLOAD_FAILED`).
- `OkHttpModelGet` must cancel the OkHttp `Call` when the coroutine is cancelled and `ensureActive()` in the copy loop.
- Catalog URLs from Gradle/`local.properties`; blank URL → disabled row. Tests inject a fake catalog + fake `ModelHttpGet`.

## Compatibility

Play and sideload both show the three rows this slice. Sideload APK bundling is a later task; if files already exist, rows show 已安装.

## Rollback

Remove settings section and ViewModel factory args; leave 5j installer in place.

## Logging

Do not log download URLs (query strings) or `.part` paths (existing logging spec).
