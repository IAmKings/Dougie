# Journal - kuluoluo (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-09-17

---



## Session 87: 思考中发送位改为终止

**Date**: 2026-09-17
**Task**: 思考中发送位改为终止
**Branch**: `master`

### Summary

忙碌时输入栏右钮改为终止并取消本轮；完成后恢复发送，播报中仍是停止播报。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `6971535` | (see git log) |

### Status

[OK] **Completed**


## Session 88: Confirm 卡弹出动效

**Date**: 2026-09-18
**Task**: Confirm 卡弹出动效
**Branch**: `master`

### Summary

Confirm 卡从对话列表拿出来盖在列表上，250ms FastOutSlowIn 底部上滑并压暗；点压暗不关闭；第一次组合不重播。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `a642344` | (see git log) |

### Status

[OK] **Completed**


## Session 89: 思考→工具卡状态切换

**Date**: 2026-09-19
**Task**: 思考→工具卡状态切换
**Branch**: `master`

### Summary

思考芯片收成循环标记、新工具卡只淡入不位移；同一张卡从正在调用切到已调用也是 150ms LinearOutSlowIn。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `372b81b` | (see git log) |

### Status

[OK] **Completed**


## Session 90: 工具不确定进度条

**Date**: 2026-09-19
**Task**: 工具不确定进度条
**Branch**: `master`

### Summary

准备/正在调用的工具卡显示不确定进度条，完成后消失；确认覆盖层不画。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `eb131b6` | (see git log) |

### Status

[OK] **Completed**


## Session 91: Chat↔任务页共享元素转场

**Date**: 2026-09-19
**Task**: Chat↔任务页共享元素转场
**Branch**: `master`

### Summary

点任务卡时卡接到该轮用户气泡 300ms；底栏和返回淡入；其它页仍立刻切。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `139c750` | (see git log) |

### Status

[OK] **Completed**


## Session 92: Confirm 卡离场动效

**Date**: 2026-09-19
**Task**: Confirm 卡离场动效
**Branch**: `master`

### Summary

确认、拒绝、终止后覆盖层 250ms 下滑收起并淡出压暗；不推迟 TaskManager。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `ed94759` | (see git log) |

### Status

[OK] **Completed**


## Session 93: 版本 0.1.3 发版

**Date**: 2026-09-19
**Task**: 版本 0.1.3 发版
**Branch**: `master`

### Summary

默认版本升到 0.1.3（versionCode 103），打 tag v0.1.3 并推送，触发 GitHub Release。

### Git Commits

| Hash | Message |
|------|---------|
| `98ceb20` | (see git log) |

### Status

[OK] **Completed**


## Session 94: 确认卡倒计时

**Date**: 2026-09-19
**Task**: 确认卡倒计时
**Branch**: `master`

### Summary

等确认时卡上显示剩余秒数，截止时刻写在任务上所以回对话不重置；到点仍由引擎拒绝。真机验收通过。

### Git Commits

| Hash | Message |
|------|---------|
| `ce4f0b5` | (see git log) |

### Status

[OK] **Completed**


## Session 95: 版本 0.1.4 发版

**Date**: 2026-09-19
**Task**: 版本 0.1.4 发版
**Branch**: `master`

### Summary

默认版本升到 0.1.4（versionCode 104），打 tag v0.1.4 并推送，把确认卡倒计时打进 GitHub Release。

### Git Commits

| Hash | Message |
|------|---------|
| `87dde0f` | (see git log) |

### Status

[OK] **Completed**


## Session 96: 规则 E 评测续作

**Date**: 2026-09-19
**Task**: 规则 E 评测续作
**Branch**: `master`

### Summary

开发者页打开从 filesDir jsonl 只读复看规则 E counts，并给出当前包名的 adb 取出命令；缺/坏文件保持空白，不泄露 utterance。

### Git Commits

| Hash | Message |
|------|---------|
| `982178b` | (see git log) |

### Status

[OK] **Completed**


## Session 97: Chat 工具卡展开

**Date**: 2026-09-20
**Task**: Chat 工具卡展开
**Branch**: `master`

### Summary

对话工具卡默认收起，有 resultJson 才展开看缩进结果；电量成功仍保留短摘要。确认卡和任务页展开未改。

### Git Commits

| Hash | Message |
|------|---------|
| `013dee0` | (see git log) |

### Status

[OK] **Completed**


## Session 98: 用户气泡语音来源标注

**Date**: 2026-09-20
**Task**: 用户气泡语音来源标注
**Branch**: `master`

### Summary

speakReply 的用户气泡下方标注「语音转写」；键盘发送不加；sharedBounds 仍只包气泡正文。

### Git Commits

| Hash | Message |
|------|---------|
| `c2e26b2` | (see git log) |

### Status

[OK] **Completed**


## Session 99: PRD 与现状对齐

**Date**: 2026-09-20
**Task**: PRD 与现状对齐
**Branch**: `master`

### Summary

对照 v0.1.4 标明 Phase 5 已交付与未做；平台与 minSdk 26 对齐；向量记忆不再写进 MVP 非目标。

### Git Commits

| Hash | Message |
|------|---------|
| `340854b` | (see git log) |

### Status

[OK] **Completed**


## Session 100: Chat 终答打字机

**Date**: 2026-09-20
**Task**: Chat 终答打字机
**Branch**: `master`

### Summary

一次性终答按字打出（≤800ms）；SSE 前缀立即跟上；历史轮、失败、动画缩放 0 直接全文。

### Git Commits

| Hash | Message |
|------|---------|
| `1d78193` | (see git log) |

### Status

[OK] **Completed**


## Session 101: 明暗双主题

**Date**: 2026-09-21
**Task**: 明暗双主题
**Branch**: `master`

### Summary

跟随系统或设置里浅色/深色；色板仍是 stitch 紫蓝。立即生效，保存配置不冲掉。

### Git Commits

| Hash | Message |
|------|---------|
| `c3d76ef` | (see git log) |

### Status

[OK] **Completed**


## Session 102: Chat 终端风

**Date**: 2026-09-21
**Task**: Chat 终端风
**Branch**: `master`

### Summary

设置终端风开关默认关，只换对话页；顶栏头像叠主色以免深底看不清。

### Git Commits

| Hash | Message |
|------|---------|
| `fbb59572747ade3deecc0fcd99f37beb1116b260` | (see git log) |

### Status

[OK] **Completed**


## Session 103: Compose UI 五态测试

**Date**: 2026-09-21
**Task**: Compose UI 五态测试
**Branch**: `master`

### Summary

Chat User/Thinking/Tool/Confirm/Final/FAILED 用 Robolectric Compose 语义断言；产品交互未改。

### Git Commits

| Hash | Message |
|------|---------|
| `47030c2` | (see git log) |

### Status

[OK] **Completed**


## Session 104: Kokoro 规则 B 真机评测

**Date**: 2026-09-21
**Task**: Kokoro 规则 B 真机评测
**Branch**: `master`

### Summary

开发者页可测单线程 Kokoro RTF；真机 p95Rtf=2.2515 未过门，产品 TTS 仍为 VITS。

### Git Commits

| Hash | Message |
|------|---------|
| `f9850a6` | (see git log) |

### Status

[OK] **Completed**


## Session 105: 版本 0.1.5 发版

**Date**: 2026-09-21
**Task**: 版本 0.1.5 发版
**Branch**: `master`

### Summary

默认版本升到 0.1.5（versionCode 105），打 tag v0.1.5 并推送，触发 GitHub Release。

### Git Commits

| Hash | Message |
|------|---------|
| `aa5588c` | (see git log) |

### Status

[OK] **Completed**

### Next Steps

- 待 Actions 出 APK；不自动开规则 D / 商店 / 多模态


## Session 106: 动作验证层（Verification Layer）落地

**Date**: 2026-10-02
**Task**: 动作验证层（Verification Layer）落地
**Branch**: `master`

### Summary

建立 verified 三态契约（ToolResult/ToolTraceEntry）、前台观察原语 ForegroundProbePort/ForegroundVerifier、app_intent 打开后验证（失败非致命回灌 LLM、捷径确定性重试一次）、TaskSnapshotCodec 持久化 verified；检查发现并修复探针 SAM 非 null 导致无观察渠道假报验证失败的高危问题。四批 work commits（39c4f0c/1732e2e/cc36ee9/77b013a/91669c4），全量 659 项测试 + checkChannelLeak 绿，14 条冻结契约未动。后续任务：tap_swipe 截屏差分验证、无障碍树状态提取器、sms_compose 树包含性检查。

### Git Commits

| Hash | Message |
|------|---------|
| `91669c4` | (see git log) |

### Status

[OK] **Completed**


## Session 107: 树状态提取器与短信编辑验证落地

**Date**: 2026-10-02
**Task**: 树状态提取器与短信编辑验证落地
**Branch**: `master`

### Summary

State Extractor 段落地：TreeSnapshot/TreeSnapshotPort（活动窗口文本快照，300 节点上限，不出工具调用栈）+ TreeVerifier（空白不敏感包含轮询）；sms_compose 验证正文出现在编辑器窗口（收件人由 ACTION_SENDTO URI 保证不设针，避免联系人姓名假阴性）；验证失败非致命不缓存、JSON 无用户内容（测试断言防泄漏）；sideload 经 AccessibilityTreeSnapshotPort+TreeProbeBridge 接线，Play 恒 verified=null。两个 work commits（4725439/d747b40），538 项测试 + checkChannelLeak + 冻结契约全绿，检查代理总裁决 PASS 零修复。剩余后续任务：tap_swipe 截屏差分验证。

### Git Commits

| Hash | Message |
|------|---------|
| `d747b40` | (see git log) |

### Status

[OK] **Completed**


## Session 108: 任务终态一致性（P0）修复落地

**Date**: 2026-10-03
**Task**: 任务终态一致性（P0）修复落地
**Branch**: `master`

### Summary

深度审查后第一个修复任务：markCancelled 包 NonCancellable（取消终态落库，红→绿验证）、recoverInterrupted 全量翻 FAILED（不再只救最新一条）、流式 persist 节流（interval 构造参数默认 250ms，终态必落盘，60 delta 写盘从 60+ 次降到常数次）、lastPersistAtMs 按任务重置；VerifiedResultJson helper 去重、toolCallId 常量化、MAX_VERIFY_FAILURES 跨工具语义注释；新增 src/testSideload 变体专属 bridge 接线测试（ContextWrapper(null) 技巧，防 SAM 接线类回归）。三个 work commits（a4e573b/e783d74/fd2699a+加固），全量测试+checkChannelLeak+play 变体编译全绿，检查代理总裁决 PASS 零必须修复。待办：数据保留任务（idempotency/audit TTL）、prompt 预算与注入防护任务、tap 验证真机证据。

### Git Commits

| Hash | Message |
|------|---------|
| `fd2699a` | (see git log) |

### Status

[OK] **Completed**


## Session 109: 数据与输入卫生（P1）落地

**Date**: 2026-10-03
**Task**: 数据与输入卫生（P1）落地
**Branch**: `master`

### Summary

深度审查第二个修复任务：idempotency 表 DB v1→2 增量迁移（加 created_at，废除 DROP 全表）+ 24h TTL 惰性清理、audit_log 行数上限 500 惰性清理、任务/会话删除级联清理幂等行（LIKE 前缀同事务）；ToolCallSanitizer STRING>10K 拒绝、OBJECT>64K 或深度>8 拒绝（拒绝不截断，js/py 32KiB 自限保持其下）；LocalToolCallParser 重复检测改结构化比较（键序/空白无关，勘误发现 ChatLlmProvider.kt:84 是活调用方）。Sanitizer/Parser 新测试均红→绿验证。检查代理 PASS 零阻断；spec 数据库约定五处同步更新，真机走查清单落任务目录。待办：prompt 预算与注入防护、tap 验证真机证据。

### Git Commits

| Hash | Message |
|------|---------|
| `0fd112c` | (see git log) |

### Status

[OK] **Completed**


## Session 110: prompt 预算与注入防护（P1，方案 A）落地

**Date**: 2026-10-03
**Task**: prompt 预算与注入防护（P1，方案 A）落地
**Branch**: `master`

### Summary

深度审查第三个修复任务：clampToolResult 2000 字符截断接入本地 traces 与云端 role=tool content 双路径；systemPrefix 的 Known facts/相关历史对话 各自前置 UNTRUSTED_DATA_NOTE（一处改动双路径生效）；localPrompt 按用户方案 A 保留用户原输入（「用户这次的提问：」+ 800 字符截断，userBlock 两分支合并，LOCAL_AFTER_TOOL_RESULTS 文案同步）；2 处冲突断言按新契约更新，新增 3 项测试均红→绿验证；14 条冻结契约不动全绿。检查代理 PASS 零阻断。至此深度审查的全部 P0/P1 修复完成；剩余：各项真机走查（各任务 device-checklist.md）+ tap 验证谓词证据。

### Git Commits

| Hash | Message |
|------|---------|
| `4fa5bbe` | (see git log) |

### Status

[OK] **Completed**


## Session 111: E1 复读缺陷修复（真机验收反馈）

**Date**: 2026-10-12
**Task**: E1 复读缺陷修复（真机验收反馈）
**Branch**: `master`

### Summary

真机验收 E1 发现：方案 A 后工具结果轮 prompt 以裸提问结尾，0.6B 最小档复读用户原话（指令在提问之前失去近因约束，stripLeadingQuestion 对整句复读无效）。修复：localPrompt 工具结果轮改为提问前置→工具结果居中→LOCAL_AFTER_TOOL_RESULTS 指令收尾（措辞改「上面已有…」）；协议态/空闲态路径不动；既有 2 处断言更新 + 新增收尾位置断言，红态验证 2 项在旧布局上失败。已重建并安装 APK 到真机（1fc6056b），待用户复测 E1/E2。

### Git Commits

| Hash | Message |
|------|---------|
| `3ad47d6` | (see git log) |

### Status

[OK] **Completed**


## Session 112: 本地多步工具轮放行（E1 二次反馈）

**Date**: 2026-10-12
**Task**: 本地多步工具轮放行（E1 二次反馈）
**Branch**: `master`

### Summary

E1 复测：0.6B 第二轮输出 calendar_create JSON（语义正确）被当文本显示，2B 用文字回应通过。修复：ChatLlmProvider 工具调用分支改为 protocolActive || localAfterToolToolCallAllowed（新助手，教学名单为闸，tap_swipe/sms_compose/未知名不放行）；LOCAL_AFTER_TOOL_RESULTS 措辞允许按需二轮调用。calendar_create 走既有 L2 确认卡，sanitizer/幂等/maxLoops=8/repeat 守卫全部既有把关；冻结契约（纯首轮）不动。红态验证措辞断言。已重建安装 APK（23:54:05），待真机三测：预期 0.6B 的「提醒开会」触发确认卡。

### Git Commits

| Hash | Message |
|------|---------|
| `867c1e1` | (see git log) |

### Status

[OK] **Completed**


## Session 113: 本地回复决策纯函数化（E1 三测缺陷）

**Date**: 2026-10-13
**Task**: 本地回复决策纯函数化（E1 三测缺陷）
**Branch**: `master`

### Summary

E1 三测：MiniCPM-2B 返回原始 JSON 给用户。calendar_query 在教学名单内、裸 JSON 本应放行执行，判定实际输出夹带前后缀文字致整段解析失败落显示兜底——属「工具 JSON 原样显示」类缺口。修复：新增 ChatPromptAssembler.decideLocalReply 纯函数统一四路径（整段可解析/夹带提取/纯文本/空），裸与夹带的名单内 JSON 均提取放行（L2 确认卡把关）、复读拦截、未教学工具优雅文案永不显示原文；ChatLlmProvider.onDone 单点调用。7 项决策测试，红态验证提取逻辑（回退后 2 项失败）。已重建安装 APK（00:07:09），待真机四测。

### Git Commits

| Hash | Message |
|------|---------|
| `247c969` | (see git log) |

### Status

[OK] **Completed**


## Session 114: 截断 JSON 修复（E1 四测取证）

**Date**: 2026-10-13
**Task**: 截断 JSON 修复（E1 四测取证）
**Branch**: `master`

### Summary

四测仍现原始 JSON；不再盲猜，直接 run-as 拉取设备 DB 取证：completionPath=LOCAL_LLM、toolTrace=[]（首轮断流）、finalAnswer 为截断多行 JSON（缺外层收尾括号）——整段解析与完整对象提取都接不住，落纯文本显示。修复：repairTruncatedJsonObject 按括号深度补齐（含补引号/去尾逗号）重解析后走既定分类（复读守卫/protocolActive/教学名单/优雅文案），永不显示原文；4 项以真机精确载荷为蓝本的测试，红态验证摘除修复分支后 3 项失败。APK 已重装（00:18:42），待五测。

### Git Commits

| Hash | Message |
|------|---------|
| `918c362` | (see git log) |

### Status

[OK] **Completed**
