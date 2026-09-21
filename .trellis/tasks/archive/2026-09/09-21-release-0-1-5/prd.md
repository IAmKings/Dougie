# 版本 0.1.5 发版

## Goal

默认版本升到 0.1.5（`versionCode` 105），打 `v0.1.5` 标签并推送，触发 GitHub Release。

## User value

0.1.4 之后已合上打字机、双主题、终端风、Compose 五态测试、Kokoro 规则 B 评测入口。打 0.1.5 才能从 GitHub Releases 装到真机。不宣称 Kokoro 已启用或商店已上架。

## Background

- 标签 `v主.次.补丁` 触发 `.github/workflows/release.yml`。`versionCode` = 主×10000+次×100+补丁 → 0.1.5 为 **105**。流程与 v0.1.4 相同。
- README 两处写着当前 v0.1.4。不改签名、不改包名、不改产品代码。

## Requirements

- R1 仓库默认 `versionName` `0.1.5`、`versionCode` 105。README 当前版本链接改到 v0.1.5。
- R2 提交后打 annotated tag `v0.1.5`（说明 `Dougie 0.1.5`），推送 `master` 与该 tag。
- R3 不改功能代码、keystore、Play/侧载拆分。不上架商店。

## Acceptance Criteria

- [x] AC1 `app/build.gradle.kts` 默认 0.1.5 / 105；README 两处当前版本为 v0.1.5。
- [x] AC2 远程存在 tag `v0.1.5`，GitHub Actions Release 已启动。

## Out of scope

上架商店、改签名、规则 D、把 Kokoro 设为默认 TTS。

## Key Decisions

- 补丁号 +1。流程与 v0.1.4 相同。
