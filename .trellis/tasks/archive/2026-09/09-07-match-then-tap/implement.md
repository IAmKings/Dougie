# Implement

1. `IntentRouteAnswers`：是否 match-then-tap 短语；`screen_match` 成功 JSON → tap args。
2. `LoopEngine.completeFromMatchThenTapIfMatched`（念出来之后、MiniRBT 之前）。
3. `LoopEngineTest`：Fake ScreenMatchTool + Fake tap 工具；确认后 x,y；几点不命中；found=false 零 tap。
4. `ChatPromptAssemblerTest`：本地仍无 `tap_swipe`。
5. Spec：`directory-structure.md` Loop 合同补这一条。
6. `./gradlew :core:runtime:test :core:llm:test :tool:accessibility:test :app:checkChannelLeak`（JDK 17）。

真机：sideload、无障碍、先截屏，说「点一下」确认；再说「匹配一下」不应出点击卡。
