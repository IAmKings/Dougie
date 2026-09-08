# L4 直接跑 QuickJS 程序

## Goal

侧载打开 **脚本特权（L4）** 后，同一工具 `js_eval` 把远程模型给的 `script` 当 **程序** 跑：最后一次表达式的值 `JSON.stringify` 进 `{ok,value}`。关掉开关则保持现有 L2 函数体（必须 `return`）。仍无文件/网络/Android。Play 不注册。不教 0.6B。

## Background

- 引擎已是 QuickJS；L2 合同把脚本包进 `function(data){…}`，模型写的整段脚本（最后一行是表达式、没有 `return`）会失败。
- `ScriptPrivilegePrefs` 默认关，本刀之前工具不读它。`PolicyEngine` 已对 L4 确认。
- 已采纳：结果 = 最后表达式 JSON；不接 `console` 当输出。

## Requirements

- R1 侧载 `JsEvalTool` 注入 `privileged: () -> Boolean`（读 `ScriptPrivilegePrefs`）。关：现有 `wrapProgram` + `RiskLevel.L2`。开：先注入全局 `data`（仍 `canonicalizeData`），再把 `script` 当程序求值；`descriptor.riskLevel` 为 **L4**（确认卡显示 L4）。
- R2 程序模式结果：最后表达式可 JSON 序列化 → `{ok,value}`；`undefined` / 无法序列化 / 语法错误 → 现有或新增中文致命错误（无值：`隔离脚本没有可序列化的结果。`）。超时 2s、host 词、大小上限不变。
- R3 确认卡：L4 时文案说明「完整脚本、结果取最后表达式、仍不读写文件不上网」；仍展示 `argsJson`。拒绝零执行。L2 文案不变。
- R4 开关切换后执行面立即变化（读 prefs，不必杀进程）。打开仍不增加 fetch/文件/Intent/无障碍。Play 无开关无引擎。不教 0.6B。不 Logcat 脚本/`data`。

## Out of scope

- 宿主 https/文件/Intent/`tap_swipe`；Play QuickJS；0.6B 写 JS；`console` 作结果通道。

## Acceptance Criteria

- [x] AC1 开关关：`return data.reduce(...)` + `1,2` 仍得 3；无 `return` 的纯表达式按现合同失败。
- [x] AC2 开关开：同一脚本可无 `return`（最后一行 `data.reduce((a,b)=>a+b,0)`）得 3；确认卡 L4 + 程序说明；拒绝零执行。带 `return` 的旧脚本仍可跑。
- [x] AC3 开 L4 仍无 fetch/文件；Play leak 过；本地 prompt 无 `js_eval`；JVM Fake 覆盖两模式。
