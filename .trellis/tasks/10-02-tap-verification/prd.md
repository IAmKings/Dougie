# tap_swipe 点击后验证（规划中，未开工）

## Goal

为 `tap_swipe` 补上动作验证层的 FAIL 分支：点击后用确定性本机信号判断「点击确实产生了可见效果」，失败作为观察反馈重规划。本任务处于规划状态：**谓词选型需要真机证据，不许盲选实现**。

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
