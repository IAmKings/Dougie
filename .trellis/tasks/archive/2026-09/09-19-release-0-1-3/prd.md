# 版本 0.1.3 发版

## Goal

默认版本升到 0.1.3（`versionCode` 103），打 `v0.1.3` 标签并推送，触发 GitHub Release 构建 Play / 侧载包。

## User value

0.1.2 之后的对话动效可以装到真机上：Confirm 进出、思考→工具切换、进度条、Chat↔任务转场。

## Background

- 标签 `v主.次.补丁` 触发 `.github/workflows/release.yml`。`versionCode` = 主×10000+次×100+补丁 → 0.1.3 为 **103**。Gradle 默认 `versionName` / `versionCode` 在 `app/build.gradle.kts`；CI 用 `-PdougieVersionName/Code` 覆盖。
- README 两处写着当前 v0.1.2。不改签名、不改包名、不 bump DB、不改产品代码。

## Requirements

- R1 仓库默认 `versionName` `0.1.3`、`versionCode` 103。README 当前版本链接改到 v0.1.3。
- R2 提交后打 annotated 或轻量 tag `v0.1.3`，推送 `master` 与该 tag，触发 Release workflow。
- R3 不改功能代码、keystore、Play/侧载拆分。

## Acceptance Criteria

- [x] AC1 `app/build.gradle.kts` 默认 0.1.3 / 103；README 两处当前版本为 v0.1.3。
- [x] AC2 远程存在 tag `v0.1.3`，GitHub Actions Release 已启动。

## Out of scope

上架商店、改签名、规则 E、确认倒计时、新功能。

## Key Decisions

- 补丁号 +1，不是 0.2.0。
- 流程与 v0.1.2 相同：改默认版本 → 提交 → tag → push。
