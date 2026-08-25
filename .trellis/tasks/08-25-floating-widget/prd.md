# 悬浮窗气泡打开对话

## Goal

双渠道提供「离开 Chat 仍能一键回去」：Play 用系统通知气泡（用户在系统里打开）；sideload 用真悬浮窗（`SYSTEM_ALERT_WINDOW`）。点击一律打开对话，不 `submit`。

## User value

轻度用户走商店合规路径；重度侧载用户可选真正浮在其他 App 上的气泡。两包不在运行时切换（与 Accessibility 相同，构建期隔离）。

## Background

- 父任务 `08-25-phase-5-system-surfaces`。Tile / 任务进度通知已归档。`chatLaunchIntent` 已有。
- 决策 #17 / §17.4：play 与 sideload Gradle flavors；play 零 Accessibility、零侧载引导文案。`checkChannelLeak` 扫 play merged manifest。
- 代码无 overlay / BubbleMetadata。`BuildConfig.IS_SIDELOAD` 仅在 `:app`。`:feature:settings` 不读 flavor。
- 用户选择：sideload 真悬浮；Play 通知气泡（非 WindowManager overlay），避免 `SYSTEM_ALERT_WINDOW` 阻碍上架。

## Requirements

- R1 点击（气泡或悬浮球）= `chatLaunchIntent`，不 `submit`，不确认 Tool。
- R2 **Play**：不得声明 `SYSTEM_ALERT_WINDOW`，不得包含 overlay `Service`/`WindowManager` 实现类。任务进度通知可带 `BubbleMetadata`（API 29+）；用户在系统「气泡」中自行开启。设置文案只讲系统气泡，**不得**引导下载 sideload。
- R3 **Sideload**：`SYSTEM_ALERT_WINDOW` 仅 sideload manifest；默认关；设置开关 + 跳转系统「上层显示」；授权且开关开才显示可拖动球。未授权不崩。
- R4 `checkChannelLeak`：play 含 `SYSTEM_ALERT_WINDOW` / overlay 类名则失败；sideload 须含 overlay 声明（实现落地后的类名写入检查）。
- R5 无 NotificationListener、无 Accessibility 泄漏（既有检查保留）。

## Out of scope

- Play 包内真 overlay（即使运行时检测也禁止，manifest 已暴露权限）。
- 气泡/悬浮内跑 Loop、截屏、确认卡。
- 定时 Agent、`:cli`、play 文案指向 sideload。

## Acceptance Criteria

- [x] AC1 Play Debug merged manifest 无 `SYSTEM_ALERT_WINDOW`；sideload 有。
- [x] AC2 点气泡/悬浮球打开 Chat，不新开 `taskId`。
- [x] AC3 Play 设置无「请安装侧载包」类文案（源码/字符串）。
- [x] AC4 `./gradlew :app:checkChannelLeak` 通过。
- [x] AC5 Sideload 默认不显示悬浮球；未授上层显示时 Chat 仍可用。
