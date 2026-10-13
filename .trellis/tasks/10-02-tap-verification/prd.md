# tap_swipe 点击后验证（证据已齐，单边谓词方案）

## Goal

为 `tap_swipe` 补上动作验证层的 FAIL 分支：点击后用确定性本机信号判断「点击确实产生了可见效果」，失败作为观察反馈重规划。真机证据已于 2026-10-13 采集完成（research/evidence/，22 份快照 + 属性级复检），谓词裁决如下。

## Open question（为什么停在这里）

两个候选谓词，各有实质性风险，没有真机数据无法裁决：

- **树文本集差分**（复用 10-02-tree-state-verification 的 `TreeSnapshotPort`）：点击前后各取一次活动窗口 `TreeSnapshot`，文本集不变 → verified=false。风险：像素级效果的点击（开关拨动、相册浏览、进度条）文本集不变 → 假阴性 → 正常点击被误报「未能确认」，伤 UX（重试预算耗尽后任务失败）。开关类控件常带 contentDescription/checked 态可部分缓解，覆盖率未知。
- **模板消失**（match-then-tap 路径）：点击前 `screen_match` 命中的模板点击后应消失。问题一：`ScreenMatchTool` 对 not-found 返回 fatal，「预期消失」语义无法复用现有工具，需 loop 级跨工具编排；问题二：`TemplateLibrary` 只有合成 fixture（solid/logo），无用户注册真实模板的机制，match-then-tap 实质是评测脚手架，真实用户价值≈0。
- 像素差分：闪烁光标/动画造成假阳性（假 PASS 不诚实），直接排除。

## Required evidence（开工前必须拿到）

1. 真机采集：对代表性点击类型（按钮、菜单项、链接、对话框确认、开关、纯图形控件）各取点击前后 `TreeSnapshot.texts` 与文本集差分，量化「文本集不变但点击有效」的比例。
2. 依据数据决定：文本集差分能否作为 verified 谓词；若不能，明确 tap 在本阶段保持 verified=null（诚实未知）是否为终态。

## Constraints（无论选哪条都成立）

- 验证失败必须是可重规划的观察（非致命、不进幂等缓存），复用 `VERIFY_FAILED` 文案与既有循环预算。
- LLM 驱动的任意坐标点击若无可靠后置条件，保持 verified=null（不假报成功也不假报失败）。
- 探针 provider 形态（每次取用、可为 null）；快照不出工具调用栈。
- 不动 14 条冻结契约；tap_swipe 为 sideload-only。

## Out of scope

- 像素差分（假阳性不可接受）；LLM 判断点击是否成功（违反验证层原则）。


## Evidence verdict（2026-10-13 真机裁决）

- 纯文本集差分**不可行**：开关 3/3 样本文本零变化；相册翻页内容不可见。
- **状态指纹（text 集 ∪ checked 数 ∪ selected 数）可行**：开关经 checked 翻转可检（-1）、导航/Tab（139Δ）、对话框（16Δ+6selectedΔ）全可检；负例（空白点击×2、光标移动）三类信号全零，特异性成立。
- 唯一盲区：相册翻页类纯像素效果 → 不可检。
- 策略裁决：**单边谓词**——有变化 → verified=true；无变化 → verified=null（诚实未知）；**永不因无变化报 verified=false**（假阴性会触发有害重试：把拨好的开关拨回去）。残余盲区随 null 落地，无危害。

## Requirements（正式）

- R1 TreeSnapshot 增加 checkedCount/selectedCount（默认 0，既有构造不变）；AccessibilityTreeSnapshotPort BFS 中累计 node.isChecked/isSelected。
- R2 TreeVerifier.awaitStateChange(before, port, timeoutMs, pollIntervalMs): Boolean——与 before 比较 text 集/两计数任一变化即 true；立即查一次，未变化轮询至超时。
- R3 TapSwipeTool 加 treeProbe: () -> TreeSnapshotPort? = { null } 与 tapVerifyTimeoutMs=2000/tapVerifyPollMs=250：dispatch 前取 before 快照，手势成功后 awaitStateChange——true → JSON 注入 verified:true 并写幂等缓存；false/null → verified=null、JSON 与现状字节一致；本工具永不产生 verified=false。幂等重放经 VerifiedResultJson.fromJson 保留标志。
- R4 接线：sideload ChannelTools 给 TapSwipeTool 传 TreeProbeBridge.provider；Play 零改动；确认门/高危前台拦截/幂等键全部不变。
- R5 树快照照旧不出工具调用栈（仅计数进结果，无文本内容外泄）。
- R6 不动 14 条冻结契约、LoopEngine、PolicyEngine。

## Acceptance Criteria

- [x] AC1 JVM 测试：有状态变化（text 集或 checked 计数变化）→ verified==true 且 JSON 含 "verified":true；swipe 同。
- [x] AC2 JVM 测试：无变化 → verified==null 且成功 JSON 与改动前字节一致；无探针 → 同。
- [x] AC3 JVM 测试：awaitStateChange 轮询语义（立即变化/N 次后变化/超时不变）。
- [x] AC4 幂等重放保留 verified=true。
- [x] AC5 红态：摘除 verify 调用后 AC1 失败。
- [x] AC6 全量 :core:tool:test :tool:accessibility:test :app:checkChannelLeak :app:testSideloadDebugUnitTest 绿；真机十测 tap 场景记回 device-checklist（AC6 真机部分待十测；红态：摘除 verify 后 3 项失败）

## Out of scope

- 相册翻页类像素效果的 verified（终态 null）；verified=false 的任何来源；LoopEngine 多步重试联动。
