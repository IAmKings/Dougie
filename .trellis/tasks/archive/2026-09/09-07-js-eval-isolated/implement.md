# Implement

1. `RiskLevel.L4`；`PolicyEngine` + `PolicyEngineTest`.
2. `JsEvalPort` / `JsEvalTool` / Fake；`:core:tool:test`.
3. `:tool:js` + sideload `ChannelTools.register`；Play 空。
4. Settings L4 开关；ConfirmCard / `toolDisplayName`；`UserFacingErrors`.
5. `ChatPromptAssemblerTest` 本地无 `js_eval`。
6. `checkChannelLeak` 扩 play 拒 `:tool:js` / quickjs。
7. Spec：RiskLevel、Don't 用 LLM URL、Don't 教 js_eval.

`./gradlew :core:model:test :core:tool:test :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak`（JDK 17）
