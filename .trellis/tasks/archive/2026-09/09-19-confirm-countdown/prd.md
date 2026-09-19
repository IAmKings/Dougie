# 确认卡倒计时

## Goal

等确认时卡上全程显示「未操作将在 N 秒后视为拒绝」，N 从 60 往下。到点仍由引擎视为拒绝。现网 60s 超时没有提示。

## User value

高风险确认不会在沉默一分钟后突然失败，用户能看见还剩多少时间。

## Background

- §11.5：超时未操作视为拒绝。`confirmTimeoutMs = 60_000` → `CONFIRM_REJECTED`。确认/拒绝/终止、点压暗、进出场动效不变。
- `AgentTask` 无截止时刻。ChatRoute 卸掉再回来若只在 Compose 里从 60 数，会和引擎对不上。进程被杀时中断任务会标 FAILED，不会残留确认卡。
- `snapshot_json` `ignoreUnknownKeys`：可选新字段不是 SQLite bump。

## Requirements

- R1 进入 `AWAITING_CONFIRMATION` 时引擎写下 `confirmDeadlineAt = now + 60s`。卡上全程一行「未操作将在 N 秒后视为拒绝」（N 为剩余整秒，60→1）。N=0 且卡还在： 「未操作即将视为拒绝」。底栏回来用同一截止时刻，不从 60 重数。
- R2 UI 不调用 `reject()`。到点仍由引擎拒绝并走现网离场。确认/拒绝/终止随时可点。点压暗仍不关。不改 60s。
- R3 不改进出场动效、文案（倒计时这行除外）、`listKey`。无 Compose UI 测试。不 bump SQLite。

## Acceptance Criteria

- [x] AC1 「打开微信」弹出确认：卡上立刻有倒计时，大约从 60 秒往下。确认/拒绝/终止仍立刻生效。
- [x] AC2 等确认时去任务再回对话：剩余秒数接着上次，不跳回 60。
- [x] AC3 等到超时：失败文案仍是「该操作需你确认后才执行」，覆盖层走离场。点压暗仍不关。
- [x] AC4 JDK 17：`./gradlew :core:runtime:test :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

改 60s、超时改成取消整轮、点压暗=拒绝、规则 E、SQLite bump。

## Key Decisions

- 全程显示「未操作将在 N 秒后视为拒绝」。
- 截止时刻在任务上，不在 Compose remember。
- UI 不自己拒绝。
