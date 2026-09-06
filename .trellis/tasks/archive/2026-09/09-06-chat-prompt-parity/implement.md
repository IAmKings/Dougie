# Implement

1. `:core:llm` 拼装函数 + 单测：人设中文且无工具名；有 memory / SCREEN / 相册 meta 时的块；无像素。
2. `OpenAICompatibleProvider` 改用该函数；更新断言 `Known facts` / 附件行的测例，并断言 system 含 Dougie 中文人设、不含 `clipboard_read`。
3. `ChatLlmProvider.promptFor` 使用拼装；若 Android 模块不便测 `promptFor`，把拼接纯函数放到 `:core:llm` 测本地串。
4. Spec：`directory-structure.md` 写明共用拼装、中文人设、工具名待下一刀。
5. 验证：`JAVA_HOME` 17  
   `./gradlew :core:llm:test :app:checkChannelLeak`

## Rollback

恢复英文 `SYSTEM_PROMPT`；本地改回仅 `task.input`。
