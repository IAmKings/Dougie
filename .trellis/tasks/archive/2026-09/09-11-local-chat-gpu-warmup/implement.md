# Implement

## Checklist

1. `:core:llm` 纯函数 `shouldWarmLocalEngine(cloudConfigured, localReady)` + `SelectingLlmProviderTest`（或同模块小测）锁 AC6。
2. `ChatLlmProvider`：`warmup()`（Default/`synchronized` → `ensureEngine`，吞异常，不碰 `inFlight`）；`releaseIfIdle()`（`inFlight == 0` 才 `close`）。`stream()` 仍 `ensureEngine`。
3. 侧载 `DougieApplication`：collect settings + activeChatSku；满足谓词则 warmup，云端开则 releaseIfIdle。Play ChannelHooks / Application 不得引用 LiteRT。
4. 设置换档已写 prefs 后，collect 会触发；确认不必在 ViewModel 里二次调用。
5. Spec：`directory-structure.md` `:tool:chatllm`；`logging-guidelines.md` 预热不打路径。
6. `./gradlew :core:llm:test :app:checkChannelLeak`（JAVA_HOME 17）。

## Validation

真机侧载未开云：冷启动 2B 进 Chat 打字再发，等待短于整段 ~15s；热后再发无二次 load；切 0.6B 第一句走 0.6B。开云端启动不应占 GPU。

## Risky / rollback

- warmup 误加 `inFlight` → 设置无法换档。
- collect 在 Main 调 `ensureEngine` → 卡启动。
- 云端开着仍 warmup → 2B 白占显存。
- 回滚：删 warmup/collect，恢复仅首次 stream 加载。
