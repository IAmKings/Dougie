# 侧载 py_eval：JSON + 冻包

## Goal

侧载在 **脚本特权（L4）打开** 时注册工具 `py_eval`，供远程高智能 LLM 做表/数组计算：CPython + 构建期冻进 APK 的 **numpy、pandas**。`script` + `data`（JSON，含 `1,2` → `[1,2]`）进，最后一次可 JSON 序列化的值出。无文件、无网、无 Android。关掉特权则从工具表移除。Play 不带。不教 0.6B。

## Background

- 父任务 `09-08-py-runtime` 第二刀才做沙箱文件。
- `js_eval` 始终注册；L4 只改 JS wrap。CPython 标准库有 `open`/`urllib`，不能同样处理。
- `tools` 是可变 map；`refreshChannelTools()` 已在无障碍同意时调用。脚本特权开关目前只写 prefs，**不**刷新工具表——本刀必须在开关时刷新。
- `toolDescriptors` 每次读 `tools.values`，远程 `system` 随表变化。本地 `LOCAL_TEACH_NAMES` 不含 `js_eval`，也不含 `py_eval`。
- Chaquopy MIT，可在 **一个** Android library（建议 `:tool:py`）冻包；仅 `sideloadImplementation`。

## Requirements

- R1 仅当 `ScriptPrivilegePrefs` 为真时 `ChannelTools.register` 放入 `py_eval`；否则 `tools.remove`。开关 `onCheckedChange` 写 prefs 后调用 `DougieApplication.refreshChannelTools()`。参数与 `js_eval` 同形：`script`、`data`。`RiskLevel.L4`。超时 **15s**。`{ok,value}` 或 `PY_EVAL_*` 中文致命错误。
- R2 冻 numpy、pandas。禁运行时 pip。禁文件/网/Intent/无障碍/JNI/`tap_swipe`。`open`/`urllib`/`socket`/`subprocess`/`ctypes` 等失败，文案走 host 类错误。大小上限与 JS 同量级（8KiB 脚本 / 32KiB data）。
- R3 每次确认。`toolDisplayName`：**运行 Python**。确认文案：用 Python 处理数据，不读写文件、不上网；确认后才执行，拒绝则跳过。仍展示 `argsJson`。
- R4 设置「脚本特权」说明改为同时覆盖：JS 按完整脚本取最后表达式；打开后远程可调用 Python 数据处理。Play 无开关无引擎。
- R5 Play classpath/APK 无 `:tool:py` / chaquopy / CPython / numpy。`checkChannelLeak` 与 `PlayShortcutCopyTest` 扩规则。不教 0.6B。不 Logcat 脚本/`data`。

## Out of scope

- 沙箱文件 I/O（第二刀）。
- SciPy / sklearn / torch / 任意 PyPI。
- Play Python；0.6B 写 Python；Python 调 `js_eval`/`tap_swipe`。
- `print` 当结果通道。
- 非 `arm64-v8a` 的 Python 原生（本刀 sideload 只带 arm64，减小包体）。

## Acceptance Criteria

- [x] AC1 侧载、特权开：确认后 `import numpy as np; float(np.array(data).sum())` + `1,2` → value 3；拒绝零执行。远程库存含 `py_eval`。
- [x] AC2 特权关：`tools` 无 `py_eval`，远程库存无该名；开关后再开不必杀进程即可出现。`open`/`urllib` 失败。Play leak 过。
- [x] AC3 JVM Fake：成功 / 超时 / host / 无值；`ChatPromptAssemblerTest` 本地无 `py_eval`；`PlayShortcutCopyTest` Play 源码无 Python 工具。
