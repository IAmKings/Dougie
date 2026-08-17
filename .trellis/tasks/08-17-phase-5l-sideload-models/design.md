# Design: Phase 5l sideload bundled models

## Boundaries

| Piece | Module |
|-------|--------|
| Asset → filesDir copy | `:app` sideload only (`BundledModelSeed` or similar) |
| Layout names | existing `AsrModelLayout` / `TtsModelLayout` |
| Gradle leak | `:app` `checkChannelLeak` inspects play APK zip |
| Real weights | gitignored `app/src/sideload/assets/models/{asr,tts}/` |

Play sourceSet must not reference those assets.

## Data flow

```
sideload Application.onCreate
  → if ASR/TTS layout missing and assets present
  → copy to filesDir/models/asr|tts
  → engines see isPresent
```

Do not go through `ModelInstaller` / HTTPS for bundled files. Do not register an AgentTool.

## Contracts

- Asset paths match layout file names (`model.int8.onnx`, `tokens.txt`, `model.onnx`, `lexicon.txt`).
- Copy to temp then rename; incomplete copy must not satisfy `isPresent`.
- Intent `model.gguf` is never in sideload assets this slice.

## Compatibility

Play still uses 5k download. Sideload settings rows show 已安装 once seeded.

## Rollback

Remove sideload assets sourceSet and seed call; leak check additions.

## Logging

Do not log asset/file absolute paths in Logcat.
