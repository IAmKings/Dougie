# Implement

1. `SelectingLlmProvider`：谓词 + 单测（云端开 / 仅本地 / 皆无）。
2. `DougieApplication`：`skipIntentShortcut` 绑该谓词。
3. `LoopEngineTest`：`isLocal=false` + `skipIntentShortcut={true}` + 高置信 `clipboard_read` → LLM、不 classify（AC1）；保留 skip=false 的时间短路径（AC3）；更新本地 skip 测例谓词说明（AC2）。
4. Spec：`.trellis/spec/backend/directory-structure.md` MiniRBT 例外改为「有对话 LLM（云端配置或本地包就绪）则 skip」，删掉「Cloud-on shortcut path unchanged」。frontend 若写死云端仍短路径，一并改。
5. 验证：`JAVA_HOME` 17  
   `./gradlew :core:llm:test :core:runtime:test :app:checkChannelLeak`

## Rollback

还原 `skipIntentShortcut = { provider.isLocal }` 与谓词。
