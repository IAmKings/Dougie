# 规划器环境上下文

## Goal

复合指令修复后真机复测：云端模型不调工具，反问「Anchor 的包名」与「记录 tab 在哪」。这是诚实提问——别名→包名清单从未进入模型上下文，模型也没有屏幕分辨率去估算底部 Tab 坐标。修复：照 standingRules 的既有通路注入环境块（可打开名单 + 屏幕分辨率 + 自主执行提示），让模型具备自主行动的信息基础。

## Confirmed facts

- standingRules 通路现成：`OpenAICompatibleProvider` 与 `ChatLlmProvider` 均有 `() -> String` ctor lambda，消费点是 `systemPrefix`；两处调用方（DougieApplication/ChannelHooks.localChatProvider）都能拿到 preferenceStore。
- 可打开名单（用户显式配置的工具授权数据）与屏幕分辨率属通用设备信息；云端路径下随 prompt 出境，与既有工具清单出境同级，PRD 记录即可。
- 复制/剪贴板、声明门等既有护栏不受影响；14 条冻结契约的 eval 走独立资源，不读环境块。

## Requirements

- R1 `ChatPromptAssembler.systemPrefix`/`localPrompt` 增加 `environment: String = ""` 参数，非空时插在常驻规则之后、工具清单之前。
- R2 `OpenAICompatibleProvider` 与 `ChatLlmProvider` 增加 `environment: () -> String = { "" }` ctor 参数（含 companion get），消费于各自 systemPrefix 调用；promptFor 签名同步。
- R3 app main 新增 `PlannerEnvironment.build(openAppsJson, widthPixels, heightPixels)`：可打开名单取前 15 条渲染为「别名=包名」+ 屏幕分辨率 + 底部 Tab 估算提示 + 「已知这些信息后直接执行，无需询问包名或位置」；名单空且分辨率为 0 → 空串。
- R4 `DougieApplication` 云 provider 与 sideload `ChannelHooks.localChatProvider` 传入 environment lambda（live 读 preferenceStore.openAppsJson 与 resources.displayMetrics）。
- R5 环境块总长上限 600 字符；不动冻结契约、不动 LoopEngine。

## Acceptance Criteria

- [ ] AC1 JVM 测试（app PlannerEnvironmentTest）：名单+分辨率渲染完整；空名单/零分辨率分别省略对应段；超 15 条截断。
- [ ] AC2 JVM 测试（core/llm）：systemPrefix 含 environment 且位于常驻规则后、工具清单前；缺省为空不影响既有断言。
- [ ] AC3 红态：AC2 的位置断言在未修复实现上失败。
- [ ] AC4 `:core:llm:test :app:testSideloadDebugUnitTest :app:testDebugUnitTest :app:checkChannelLeak :app:compileSideloadDebugKotlin` 绿；冻结契约不动。
- [ ] AC5 真机复测：「打开Anchor，点击底部的记录tab」→ 模型自主调用 app_intent（用清单包名）→ tap_swipe 确认卡；不再反问包名/位置。结果记回 device-checklist。

## Out of scope

- 屏幕内容感知（截图/DOM 级坐标）；别名表出境的用户告知文案（设置页，另行评估）。
