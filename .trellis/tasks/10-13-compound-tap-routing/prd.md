# 复合指令点击捷径劫持修复

## Goal

真机十测取证（设备 DB）：「打开Anchor，点击底部的记录tab」「打开微信，点击底部的联系人tab」均未到达云端规划器——`looksLikeMatchThenTap` 以「句子含『点击/点一下/按一下』」为触发条件，复合句被 match-then-tap 评测捷径劫持，本地跑 `screen_match`（合成模板 solid）必然 found:false → 「未能匹配屏幕内容」FAILED。修复：捷径收紧为裸点击短语精确匹配，复合句放行给规划器（app_intent → tap_swipe 序列）。

## Confirmed facts

- 设备 DB：两条复合任务 status=FAILED、completionPath=LOCAL_INTENT、trace 仅 screen_match(found:false)、lastError=未能匹配屏幕内容——云端 LLM 零参与。
- 该捷径为「截屏后点这里」场景的评测脚手架（TemplateLibrary 只有 solid/logo 合成模板，真实用户无可用模板），裸短语触发才有意义。
- 既有测试（IntentRouteAnswersTest:192-202、LoopEngineTest matchThenTap* 系列）钉的全是裸短语——精确匹配零冲突。
- 复合句放行后的期望链路：云端 LLM 规划 app_intent（Anchor 需在可打开名单，否则诚实报「该应用未加入可打开名单」）→ tap_swipe（坐标为模型估算，点偏时验证层落 verified=null 诚实未知）。

## Requirements

- R1 `looksLikeMatchThenTap` 改为裸短语精确匹配：剥离「请/帮我/麻烦」前缀后，整句 ∈ 裸点击短语集（点/点击/点一下/按一下/点这里/点击这里/按这里/点这个/点击这个/按这个/点它/点击它）。尾部分词与标点由既有 normalize 处理。
- R2 含其他动作或宾语的句子（如「打开Anchor，点击底部的记录tab」「点击设置里的开关」）一律不再命中捷径，走 Action Planner。
- R3 既有全部断言不变且保持绿（裸短语行为逐字保留）。

## Acceptance Criteria

- [x] AC1 JVM 测试：两条真机复合句 isMatchThenTapPhrase==false；裸短语集全部 ==true；既有负例（现在几点了/匹配一下/地点/明天三点开会）仍 false。
- [x] AC2 红态：AC1 复合句断言在旧实现上失败。
- [x] AC3 `:core:runtime:test :app:testSideloadDebugUnitTest :app:checkChannelLeak` 绿；APK 重装后真机复测：复合句到达云端规划器（trace 出现 app_intent/tap_swipe 或相应诚实错误）。

## Out of scope

- tap 坐标的屏幕感知（模型估坐标是已知能力边界，点偏落 verified=null）；IntentRouteAnswers.firstQuoted 弯引号扩展。
