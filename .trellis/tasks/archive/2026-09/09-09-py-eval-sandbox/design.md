# Design: py_eval sandbox files

## Path jail

Kotlin `AndroidPyEvalPort` mkdir `File(app.filesDir, "py_sandbox")` and pass the absolute path into `evaluate(script, dataJson, sandboxRoot)`.

Python `py_eval_runtime`:

- `os.path.realpath` join(root, user_path); if not `resolved == root or resolved.startswith(root + sep)` → host.
- Reject user paths that are absolute, have `..` before resolve, or contain `://`.
- `open`/`io.open` rewrite the first file argument through the jail, then call the real open (saved as `_real_open` before patch).
- Patch `os.listdir/mkdir/remove/rename/chdir` similarly; `chdir` only to jail subdirs; default cwd for eval = sandbox root (`os.chdir` once per evaluate, restored in `finally`).
- Quota: walk sandbox size before write; if size + incoming would exceed 32MiB → `RuntimeError("quota")` → `PY_EVAL_QUOTA`.

Do not delete `os`/`ctypes`/`importlib` from `sys.modules`.

## Guard

`IsolatedPyGuard`: drop `open(` and `io.open` tokens. Keep `urllib`, `subprocess`, `socket`, `import os` can stay blocked **or** allow `import os` now that os is jailed — allow `import os` so `os.listdir('.')` works. Drop `from os` / `import os` from HOST list.

Keep `ctypes` in HOST? ctypes can bypass jail. Keep ctypes blocked in source + finder.

## Fake

`FakePyEvalPort` with optional temp dir: script containing `t.csv` write/read sums to 3; `../` throws HOST.

## Copy

确认：可用沙箱文件处理数据，不能上网或读应用外文件。确认后才会执行；拒绝则跳过。
设置脚本特权说明可补一句：打开后还可读写应用内脚本沙箱。
