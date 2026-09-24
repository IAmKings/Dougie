# 发版 0.1.6

## Goal

把 `master` 上已验收、但还没进入 v0.1.5 安装包的能力打成下一个 GitHub Release。这个任务不增加新行为。

## What the release contains

相对 v0.1.5：

- 设置「常驻规则」
- 定时「晨间简报」
- 设置「隐私」
- 开发者页可测最小档工具契约；打开应用两条未过门，产品朗读仍是 VITS，不上架

## Requirements

- R1 `versionName` 改为 `0.1.6`，`versionCode` 改为 106。
- R2 README 的「已发布安装包」改成 v0.1.6，并去掉「这些还没打进 Release」那句。截图说明可以留着，直到重拍。
- R3 不改工具表、出境默认值、常驻规则默认关、晨间简报不自动发送。
- R4 打标签 `v0.1.6` 由仓库的发版流程出 Play 与侧载 APK。本任务不把上架写进说明。

## Acceptance Criteria

- [x] AC1 默认构建的版本名是 0.1.6，版本号是 106。
- [x] AC2 README 安装表指向这一版，并仍写明尚未上架商店。
- [x] AC3 `checkChannelLeak` 通过。Play 包仍没有端侧权重、无障碍、JS、Python。

## Out of scope

重拍截图、Play Console、打开应用落空提示。
