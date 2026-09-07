# Implement

1. `ChatPromptAssembler`：`localTeachable` + 协议三例；`localPrompt` 用过滤清单。
2. `ChatPromptAssemblerTest`：全表入、本地出只有三名；`systemPrefix` 仍全表；身份行仍无人设工具名。
3. `LocalToolCallParser`：可选 markdown 围栏；测 battery / clipboard_read JSON。
4. Spec：`directory-structure.md` Decision 条写清本地清单 ≠ 远程全表。
5. `./gradlew :core:llm:test :app:checkChannelLeak`
6. 真机侧载关出境：三句 AC1。

## Rollback

还原 assembler / parser / spec。
