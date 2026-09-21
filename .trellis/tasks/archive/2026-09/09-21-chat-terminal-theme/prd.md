# 终端风换肤

## Goal

设置增加「终端风」开关（默认关）。打开后只把 **对话页** 换成黑底等宽 + ANSI 角色色；关闭则 Chat 仍是现网 stitch 气泡。其它页（设置/任务/记忆）不动。不解析真实 ANSI 转义。

## User value

想看终端感对话时可以打开；默认外观不变。

## Background

- 用户确认：Chat 专用开关；黑底等宽 + 绿/黄/红角色色；其它页 stitch；关 = 现状。
- `PRD.md` §3.1：等宽 + ANSI 配色模拟 + 打字机。打字机已做。属 `:feature:chat`，禁止 mosaic。
- 工具结果块已有 `Monospace` + `TerminalBg` `#0D0F0F`。用户/Agent 气泡正文不是等宽。无 escape 解析。
- 主题三选刚落地（`ThemePreference` + `recreate`）。本刀用立即写入的 Boolean，Chat 收集 `StateFlow` 即可，**不必** `recreate()`。

## Requirements

- R1 设置 **终端风** 开关，默认关。立即生效，不必保存配置。`save()` 必须抄回，避免冲掉。
- R2 开：仅 Chat 表面（顶栏、feed、气泡、思考、工具卡、确认覆盖、输入条）黑底；正文等宽；角色色 ANSI 模拟（用户青、助手绿、思考/执行黄、失败红）。关：现网 stitch，无新等宽。
- R3 不解析 `\x1b[`。不改 History/Settings/Memory/Permissions/Debug 配色。不改打字机节奏、`listKey`、进入动效、主题三选、悬浮球、版本号。
- R4 开着终端风时，浅色/深色仍作用于其它页；Chat 以终端色板为准。

## Out of scope

- mosaic、真实 ANSI 解析、全 App 换肤、规则 D、Kokoro、Compose UI 测试、升版本。

## Technical notes

- `ProviderSettings.terminalTheme: Boolean = false`；`setTerminalTheme`；存储键 `terminal_theme`。
- `MainActivity` 把 `prefs.terminalTheme` 传进 `ChatRoute`。Chat 内 `DougieColors.TerminalSkin`（或等价）覆盖 Local/参数，不要抽 `:core:ui`。
- JVM：`fromStored` 式布尔即可；无 Compose UI 测试。

## Acceptance Criteria

- [x] AC1 默认关：Chat 仍是 stitch 气泡（浅/深随主题），工具块等宽不变。
- [x] AC2 打开后对话页黑底等宽，用户/助手/思考/失败色可辨（青/绿/黄/红系）；设置/任务仍是 stitch。
- [x] AC3 关掉立刻回到 stitch。保存配置不冲掉开关。主题三选仍只管浅/深。
- [x] AC4 打字机/进入动效/`listKey` 单测仍过。
