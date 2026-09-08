# Implement

1. `UserFacingErrors.JS_EVAL_NO_VALUE`；`JsEvalPort` / Fake / `JsEvalTool(privileged)`；L2 回归 + L4 无 return 得 3。
2. `AndroidJsEvalPort` 程序模式；`ChannelTools` 传入 `ScriptPrivilegePrefs.isEnabled`。
3. `confirmToolBody` L4 文案；设置说明改为打开后按完整脚本、取最后表达式。
4. `ChatPromptAssemblerTest` 本地仍无 `js_eval`。
5. Spec：L4 改变执行面（程序 vs 函数体）；仍无宿主 API。

`./gradlew :core:tool:test :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak`
