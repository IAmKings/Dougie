# Implement

1. Assembler 名单 + exampleArgs + 中文标签。
2. `ChatPromptAssemblerTest`：descriptors 含 `screen_match`/`speech_output`/`tap_swipe`；本地含带参示例，不含 `tap_swipe`；远程仍含。
3. Spec：`directory-structure.md` taught 名单补这两件。
4. `./gradlew :core:llm:test :app:checkChannelLeak`（JDK 17）。

真机：关出境、先截或附屏再说匹配；打字「把你好念出来」看 `speech_output`（不要用按住说话，以免宿主叠音）。
