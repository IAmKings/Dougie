# Quick Settings Tile 打开对话

## Goal

用户把 Dougie 加到系统快捷设置后，点 Tile 打开 App 对话页（Chat），无需先从桌面找图标。

## User value

离开 Chat 时仍能一键回到 Agent 主界面；不绕过确认卡、出境策略或权限。

## Background

- 父任务 `08-25-phase-5-system-surfaces`。用户确认 Android 系统入口优先于 `:cli`；本回合第一项为 Quick Settings Tile。
- 根 `PRD.md` §15 Phase 5 列出 Quick Settings；§3.2 禁止自动读取全部通知。
- 证据：`app/src/main/AndroidManifest.xml` 仅 `MainActivity` LAUNCHER；无 `TileService`。`MainActivity` 默认 `AppRoute.Chat`（`MainActivity.kt`）。`:app` 无 `src/test`。`:feature:*` 不得跑 Loop / 系统 API。
- 依赖：无。不依赖通知 / 悬浮窗 / 定时 child。

## Requirements

- R1 声明 `TileService`（`android.service.quicksettings.TileService`），`exported=true`（系统绑定所必需），中文 Tile 标签 **Dougie**。
- R2 点击 Tile 启动 `MainActivity` 并落到对话页；不 `submit()`、不读 API 密钥、不写 Preference。
- R3 Play 与 sideload 均包含同一 Tile（非侧载专属）。
- R4 不引入 NotificationListener、`SYSTEM_ALERT_WINDOW`、Accessibility、mosaic、`:cli`。
- R5 `onClick` 不记录 Prompt / 密钥 / tool args。

## Out of scope

- 从 Tile 提交任务、选预设 Prompt、显示 Loop 状态。
- Notification Agent、Floating Widget、Scheduled Agent。
- 修改 Chat 视觉主题 / 终端风。
- Compose UI 自动化测试（仓库无此套件）；真机加 Tile 为手工验收。

## Technical notes

- 实现放在 `:app`（系统服务 + 启动 Activity），不放 `:feature:chat`。
- 抽出无 Context 副作用的 Intent 构造便于 JVM 单测（若 `:app` 补 `src/test` 过重，则用纯 Kotlin 函数 + JUnit，不依赖 Robolectric）。
- `MainActivity` 建议 `singleTop` + `onNewIntent`，避免重复堆叠；Tile 使用 `FLAG_ACTIVITY_NEW_TASK | SINGLE_TOP | CLEAR_TOP`。
- 验证：`JAVA_HOME` OpenJDK 17；`assemblePlayDebug` / `assembleSideloadDebug` merged manifest 含 TileService；`checkChannelLeak` 仍绿。

## Acceptance Criteria

- [ ] AC1 用户可在快捷设置中添加 **Dougie** Tile（Play Debug 与 Sideload Debug）。
- [ ] AC2 点击 Tile 打开已有或新建的 `MainActivity`，可见对话页（底栏「对话」）。
- [ ] AC3 点击不创建新 `AgentTask`（`taskId` 不因点击而变化，除非用户随后在 Chat 发送）。
- [ ] AC4 `./gradlew :app:checkChannelLeak` 通过（JDK 17）。
- [ ] AC5 play merged manifest 无 `NotificationListenerService` / `AccessibilityService` / `TapSwipeTool`。
