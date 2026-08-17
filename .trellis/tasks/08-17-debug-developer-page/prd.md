# Debug / 开发者页面

## Goal

设置页增加 **开发者** 一行，进入 Debug 页：看当前任务状态和最近 Tool 审计（仅 `taskId`、工具名、成败）。满足根 `PRD.md` §3.1 UI。不展示 Prompt、密钥、转录、剪贴板、坐标、Tool 参数。

## Background

- Chat / History 已有任务展示；无独立开发者页。
- `AuditLog` 目前只 `record`；表 `audit_log` 已有 `task_id` / `tool_name` / `outcome` / `created_at`。
- 入口已拍板：设置页，不新增底栏项。
- `:cli` 不进 APK。文案 Dougie。

## Requirements

- 设置页「开发者」打开 Debug；返回回到设置。
- 当前任务：`taskId`、`status`、`loopCount`、`lastError`（可空）；无任务时中文空态。
- 最近审计行（如 50 条）：工具名 + `SUCCESS`/`FAILED` + `taskId`，新在上。
- UI 模型不得包含 `resultJson`、prompt、API key、音频、意图槽位。
- `:feature:debug` 只依赖 `:core:runtime` / `:core:model`，不调系统 API、不开 OkHttp。
- play 与 sideload 都有该入口。

## Out of scope

- 底栏第五项、`:cli`、Chat 终端风主题、日志导出、远程上报、Memory 原文、Tool args/result JSON。

## Acceptance Criteria

- [x] 设置 → 开发者能打开；空态与有任务/有审计均可读。
- [x] 单测：Debug UI 模型不含 `resultJson` / prompt。
- [x] `./gradlew :feature:debug:testDebugUnitTest :app:checkChannelLeak`（JDK 17）通过。
