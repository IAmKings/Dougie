# 真机签字记录

- 现场日期：2026-08-18
- 签字日期：2026-08-24（kuluoluo 将 Case 07 / 09 / 14 标为签名通过）
- 机型：PJZ110（一加），Android 16 / API 36
- 包：play debug `com.dougie.app`（Case 11 崩溃修复后已重装；捕获 720×1584）
- 父任务交叉项：**已勾**（§16.1 十四 Case 均为通过）

| Case | 结论 | 渠道 | 现象 |
|------|------|------|------|
| 01 | 通过 | play | 普通聊天出终答；开发者页 COMPLETED、lastError 无 |
| 02 | 通过 | play | 电量工具卡 + 终答与电量一致 |
| 03 | 通过 | play | 日历查询工具卡 + 终答基于工具结果 |
| 04 | 通过 | play | 确认卡后写入一条 DougieE2E 日程 |
| 05 | 通过 | play | 路径 A `PERMISSION_DENIED`；路径 B `CONFIRM_REJECTED` |
| 06 | 通过 | play | `LLM_TIMEOUT` 文案 + 重试（新 taskId） |
| 07 | 通过 | play | 真机无法让已注册 Tool 超过 15s（协议允许）。旁证 `LoopEngineTest.toolTimeoutFailsTaskWithReadableError`；Case 02 证明正常 Tool 路径可用。2026-08-24 签名通过。 |
| 08 | 通过 | play | 杀进程后 `INTERRUPTED` + 可重试、不续传 LLM |
| 09 | 通过 | play | 确认后立即 COMPLETED，无法在创建飞行中 force-stop。同标题确认 3 次，日历仍 **1 条**。2026-08-24 签名通过。 |
| 10 | 通过 | play | 强制停止后记忆页可见事实；第二轮有来源。问句曾被误存（代码已收紧）；列表空是 ViewModel 未 refresh（已修） |
| 11 | 通过 | play | 捕获 720×1584 + match、未崩。关权限「未授权」。后台再截：`应用不在前台，无法截取屏幕。` 未崩。二次截屏需重新投屏授权（一次性 token）。 |
| 12 | 通过 | play+sideload | 两包并存且可分别打开。本会话未重跑 `checkChannelLeak` |
| 13 | 通过 | play | 出站关+保存后拦截；开出站+保存后可对话 |
| 14 | 通过 | play | 中断提示由 Case 08 覆盖；未点重试时日历不凭空多一条（Case 09 同标题仍 1 条）。创建中途杀掉测不到。2026-08-24 签名通过。 |

## 本轮代码缺口

- 记忆页 `refresh()`、问句不进 MemoryGate：已在源码，随本次重装进机。
- Case 11 真机 AC 齐：捕获、拒绝、后台前台约束均过；token 一次性。
