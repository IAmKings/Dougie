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
