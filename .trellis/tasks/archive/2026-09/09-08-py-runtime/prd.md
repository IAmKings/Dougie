# 侧载真 Python 数据处理

## Goal

远程高智能 LLM 用 **CPython + 冻好的科学计算包** 做表/数组处理。两刀交付，互不捆进同一 PR。

## Task map

| 刀 | 任务 | 可验收结果 |
|----|------|------------|
| 1 | `09-08-py-eval-json` | `py_eval`：`script` + `data` JSON → 最后可序列化值；冻 numpy/pandas；无文件无网 |
| 2 | `09-09-py-eval-sandbox` | 仅读写 `filesDir/py_sandbox`；仍无网 / Intent / 无障碍 / pip |

## Shared constraints

- 只侧载。Play 无 CPython、无 numpy、不注册 `py_eval`。`checkChannelLeak` 拒引擎与包名。
- 不教 0.6B。不 Logcat 脚本/`data`。
- 禁止 pip、禁止模型指定下载 URL。包只在 **构建期冻进 APK**。
- JS 不得调 Python，Python 不得调 `tap_swipe` / Android。
- 确认每次；拒绝零执行。
- 引擎：Chaquopy（MIT），构建期冻包。第一刀子任务 `09-08-py-eval-json`：L4 关则不注册 `py_eval`。

## Out of scope (parent)

- Play 渠道 Python。
- 任意 PyPI、SciPy/sklearn/torch（除非后续单独开刀）。
- 前台脚本 / 目标循环（点第三方 App）。
