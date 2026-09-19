# 版本 0.1.4 发版

## Goal

默认版本升到 0.1.4（`versionCode` 104），打 `v0.1.4` 标签并推送，触发 GitHub Release。

## User value

确认卡倒计时在 0.1.3 标签之后才合上，发 0.1.4 才能装到真机。

## Background

- 标签 `v主.次.补丁` 触发 `.github/workflows/release.yml`。`versionCode` = 主×10000+次×100+补丁 → 0.1.4 为 **104**。流程与 v0.1.3 相同。
- README 两处写着当前 v0.1.3。不改签名、不改包名、不改产品代码。

## Requirements

- R1 仓库默认 `versionName` `0.1.4`、`versionCode` 104。README 当前版本链接改到 v0.1.4。
- R2 提交后打 annotated tag `v0.1.4`（说明 `Dougie 0.1.4`），推送 `master` 与该 tag。
- R3 不改功能代码、keystore、Play/侧载拆分。

## Acceptance Criteria

- [x] AC1 `app/build.gradle.kts` 默认 0.1.4 / 104；README 两处当前版本为 v0.1.4。
- [x] AC2 远程存在 tag `v0.1.4`，GitHub Actions Release 已启动。

## Out of scope

上架商店、改签名、规则 E、新功能。

## Key Decisions

- 补丁号 +1。流程与 v0.1.3 相同。
