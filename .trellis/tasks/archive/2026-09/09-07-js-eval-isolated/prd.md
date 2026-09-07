# 隔离 QuickJS 与 L4 开关占位

## Goal

侧载 `js_eval` 在隔离 QuickJS 里跑模型给的脚本：只变换入参 JSON，无文件、无网络、无 Android API。每次 L2 确认卡展示参数（含脚本）。设置有 **L4 脚本特权** 开关（默认关），本刀打开也不增加宿主 API。Play 不注册、不带引擎。产品文案不写「逃逸」。

## Background

- 安全边界：宿主不提供的能力就不存在。L4 是未来特权白名单的占位，不是关沙箱。
- `RiskLevel` 现为 L0–L3；L2/L3 已 `NeedsConfirmation`。确认卡已渲染 `argsJson`。
- 不教 0.6B；远程全表可含 `js_eval`。不用 JS 点屏幕。模型不得用脚本指定任意下载 URL。

## Requirements

- R1 增加 `RiskLevel.L4`；`PolicyEngine` 对 L2/L3/**L4** 均确认。本刀 `js_eval` 标 **L2**（隔离也要确认）。L4 无对应工具。
- R2 侧载注册 `js_eval`：`script`、`data`（JSON 文本）。`JsEvalPort` 隔离执行；超时；无 `fetch`/文件/Java 桥。结果 `{ok, value}` 或致命中文错误。L4 开/关不改变执行面。
- R3 每次确认；卡上可见脚本。`js_eval` 确认说明改为隔离运行、不读写文件、不上网（不要套用「写入设备数据」）。拒绝零执行。
- R4 侧载设置 L4 开关默认关 + 中文说明。Play 无开关、无 `js_eval`、无 QuickJS。`checkChannelLeak` 拒 play 的 `JsEvalTool` / quickjs / `:tool:js`。
- R5 不写入 `LOCAL_TEACH_NAMES`。不 Logcat 脚本/`data`。Chat 工具名 **运行脚本**。

## Out of scope

- 特权 https/文件/Intent/无障碍；Play 引擎；本地模型写 JS；JS 调 `tap_swipe`。

## Acceptance Criteria

- [x] AC1 侧载：远程或测试调用 `js_eval` → 确认卡含 script → 确认后纯计算成功；拒绝零执行。脚本无法联网/读文件。
- [x] AC2 L4 开关默认关，打开不改变本刀结果。Play leak 过。
- [x] AC3 JVM：port Fake/契约；超时与非法脚本失败；本地 prompt 无 `js_eval`；`PolicyEngine` L4 要确认。
