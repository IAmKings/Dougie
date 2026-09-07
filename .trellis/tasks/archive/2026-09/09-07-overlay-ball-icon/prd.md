# 优化侧载悬浮球图标形态

## Goal

侧载悬浮球收起为 **纯图标圆球**（品牌蓝底 + `dougie_logo`），不再显示「Dougie」文字。展开菜单与截屏/开对话行为不变。Play 无 overlay。

## Background

`DougieOverlayService` 收起目前是 `TextView` + `app_name` + `#3D5198`。用户选定纯图标圆球，不要胶囊字条。Chat 的 Super/本地/Noob 不跟到悬浮球。脚本 runner 另开任务。

`dougie_logo` 矢量填充 `#344C9A`，与球底几乎同色，球上须 **白色着色**，不改 launcher/Chat 用的 xml。

## Requirements

- R1 收起：约 48dp 正圆、可拖动；可见 Dougie 标、无「Dougie」字。`contentDescription` = `app_name`。
- R2 点按展开仍仅 **截取屏幕 / 打开对话**；截屏藏球、`PendingIntent`、不 `submit`、满 4 张规则不变。
- R3 仅 sideload。复用 `dougie_logo` vector，不 PNG、不用 Super/Noob。
- R4 实现后更新 frontend spec：collapsed chrome 从「`app_name` 字」改为「圆球 + logo，读屏仍 Dougie」。

## Out of scope

- 脚本/目标循环、L3、菜单加项、Play overlay、改 Chat 头像或桌面图标。

## Acceptance Criteria

- [x] AC1 真机 sideload：收起是圆图标球；点开两菜单；截屏/开对话与现网一致。
- [x] AC2 源码收起不是仅 `TextView`+`app_name`；`:app:testPlayDebugUnitTest` `:app:checkChannelLeak` 过。
