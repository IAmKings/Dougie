# Design: py_eval JSON isolate

## Placement

| Piece | Where |
|-------|--------|
| `PyEvalTool` / `PyEvalPort` / `FakePyEvalPort` / size+host guard | `:core:tool` JVM |
| Chaquopy + freeze numpy/pandas + `AndroidPyEvalPort` | new `:tool:py`, **sideload only** |
| Register iff L4 | sideload `ChannelTools.register` |
| Play empty | play `ChannelTools` unchanged besides unused `scriptPrivileged` already present |

Chaquopy attaches to **exactly one** Android module (`:tool:py`). `:app` `sideloadImplementation(project(":tool:py"))`. Native ABI: **arm64-v8a only**.

## Register / unregister

```
if (scriptPrivileged()) {
  tools[PyEvalTool.NAME] = PyEvalTool(AndroidPyEvalPort())
} else {
  tools.remove(PyEvalTool.NAME)
}
```

Settings switch: `setEnabled` then `(application as DougieApplication).refreshChannelTools()`.

`descriptor` is always L4 (tool only exists when privileged).

## Execute

Mirror `JsEvalTool`: canonicalize `data`, assert size, deny host tokens in source, `withTimeout(15_000)`, `port.evaluate(script, dataJson)` returns JSON text of last value.

Android port:

1. One worker thread; create/reuse interpreter carefully (Chaquopy `Python.getInstance()` is process-global — still serialize eval).
2. Inject `data = json.loads(...)`.
3. `exec`/`eval` user script as a program; last expression → `json.dumps` (numpy scalars via `float()`/`int()` in wrapper if needed).
4. Before eval: replace/disable `builtins.open`, `os`, `socket`, `subprocess`, `ctypes`, `urllib` (import hook or emptied modules). Fail → `PY_EVAL_HOST`.
5. `null`/non-JSON → `PY_EVAL_NO_VALUE`. Timeout → `PY_EVAL_TIMEOUT`.

Do not pass Java objects into the script. Do not expose Android.

## Copy

- Settings body: 打开后 JavaScript 按完整脚本取最后一次表达式；远程模型可调用 Python 做数据处理（numpy/pandas）。仍无文件或网络。每次都要确认。默认关闭。
- Confirm: 用 Python 处理数据，不读写文件、不上网。确认后才会执行；拒绝则跳过。

## Leak

`checkChannelLeak`: play classpath no `:tool:py` / `chaquopy` / `com.chaquo`; play zip no `libpython` / `chaquopy` / `numpy`. Do not scan bare `Python` (too broad). Scan `AndroidPyEvalPort` / `PyEvalTool` in play ChannelTools source like js.

## Tests

- `PyEvalToolTest` + Fake (sum, no return needed if program mode, host, timeout, missing engine).
- `ChatPromptAssemblerTest` remote may list `py_eval`, local omits.
- `ChatUiStateTest` 运行 Python + confirm copy.
- `PlayShortcutCopyTest` + `checkChannelLeak`.
