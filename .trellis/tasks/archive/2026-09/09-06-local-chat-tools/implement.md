# Implement

1. `ChatPromptAssembler` 增加 descriptors 参数与工具清单块；更新 `ChatPromptAssemblerTest` / OpenAI system 测例。
2. `:core:llm` 工具 JSON 解析器 + 单测。
3. `ChatLlmProvider` 注入 descriptors；流式结束后解析；扩 stub 仅当 AAR 需要。
4. `DougieApplication` / sideload `ChannelHooks` 注入同一 descriptors。
5. Spec：directory-structure（ChatLlmProvider ToolCall）、logging（不 log 提示）。
6. 验证：`JAVA_HOME` 17  
   `./gradlew :core:llm:test :core:runtime:test :app:checkChannelLeak`

## Rollback

去掉 descriptors 注入与解析；assembler 回到无人设后工具块。
