# 侧载 py_eval 沙箱文件

## Goal

第二刀：同一 `py_eval` 可读写应用私有 `filesDir/py_sandbox/` 的相对路径。文件**跨多次调用保留**（卸装或清应用数据才没）。仍无网、无外部存储。Play 不变。不教 0.6B。

## Background

- 第一刀 JSON isolate 已上：L4 才注册；误删 `os`/`importlib` 会让真机 numpy 失败。
- `IsolatedPyGuard` 把 `open(` 当 host，本刀改为运行时关路径。
- 用户采纳：沙箱持久，便于下一轮再读 csv。

## Requirements

- R1 根目录 `filesDir/py_sandbox/`，mkdir 若不存在。相对路径 only；`..`、绝对路径、`content:`/`file:`、出根 → `PY_EVAL_HOST`。跨 `py_eval` 保留文件。无单独「清空沙箱」设置（卸装/清数据即可）。
- R2 `open` / `io.open` / pandas 读写 / `os.listdir|mkdir|remove|rename` 全部经同一解析。Kotlin 不再因 `open(` 拒绝。仍禁 `urllib`/`socket`/`subprocess`。勿从 `sys.modules` 删除 `os`/`importlib`/`ctypes`。
- R3 `data` JSON 不变。确认文案：可用沙箱文件处理数据，不能上网或读应用外文件。确认后才会执行；拒绝则跳过。
- R4 沙箱合计 **32MiB**；写入将超限 → 新中文错误（建议：沙箱文件过多，已拒绝写入。）。不 Logcat 脚本/`data`/文件内容。不教 0.6B。

## Out of scope

- SAF / 相册 / 外部存储；联网写入沙箱；pip；Play；0.6B；点第三方 App；设置页清空按钮。

## Acceptance Criteria

- [x] AC1 L4：`open('t.csv','w')` 写入两行数字再 `pandas.read_csv` 求和得 3；第二次调用不写文件仍能读到该 csv。`open('/etc/passwd')` 与 `'../x'` 失败。
- [x] AC2 `urllib` 仍 host。Play leak 过。本地 prompt 无 `py_eval`。
- [x] AC3 Fake/Python 测出根与超限。
