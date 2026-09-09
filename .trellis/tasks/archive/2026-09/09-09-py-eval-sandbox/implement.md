# Implement py_eval sandbox

Depends on archived `09-08-py-eval-json`. Persist files; no wipe-per-call.

## Checklist

1. `UserFacingErrors.PY_EVAL_QUOTA` 沙箱文件过多，已拒绝写入。
2. `PyEvalPort.evaluate(..., sandboxRoot: String)` — Fake uses junit temp; Android passes `filesDir/py_sandbox`.
3. `py_eval_runtime` jail + quota; cwd = sandbox; restore open for jailed paths only.
4. `IsolatedPyGuard` stop blocking `open(` / `import os`; keep net tokens + ctypes.
5. Confirm + settings strings. `FakePyEvalPort` / Python loop tests for `../` and quota.
6. Spec directory-structure + error-handling.

## Validate

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :feature:chat:testDebugUnitTest :app:checkChannelLeak
```

Device: two py_eval calls, second reads csv from first.

## Start gate

User must approve this summary before `task.py start`.
