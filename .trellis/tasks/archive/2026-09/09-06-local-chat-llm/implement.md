# Implement: 侧载本地 Chat LLM

## Checklist

1. `ChatModelLayout` + catalog 聊天行 + `isInstalled`；测 SHA 后钉死 HF INT4 URL。
2. `SelectingLlmProvider` + JVM 测：云端优先、无云端用本地、无本地则走云端以便拦截。
3. `:tool:chatllm`：Engine 单例 + `LlmProvider.stream` → `TextDelta`；不 Logcat 文本。
4. 侧载 `DougieApplication` 注入；Play 不注入。`localLlmReady` 只看聊天布局。
5. 设置行 + 现有确认下载；probe 不写补全到 Logcat。
6. `IntelligenceAvailableTest` 保持：远程失败仍 NOOB（不做同轮切换）。
7. JDK 17：`./gradlew :core:llm:test :core:tool:test :feature:chat:testDebugUnitTest :feature:settings:testDebugUnitTest :app:checkChannelLeak`

## Risky

- 把 LiteRT 打进 Play 或把 `.litertlm` 打进 APK。
- `localLlmReady=true` 来自意图 ONNX。
- 同轮云端失败后重试本地。
- 每句重新 `Engine.initialize()`（GPU 冷加载 ~4s）。
