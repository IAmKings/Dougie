# Implement py_eval JSON

Depends on parent knife 1 only. Knife 2 (sandbox files) must not land in this PR.

## Checklist

1. `UserFacingErrors.PY_EVAL_*` Chinese strings; `PyEvalPort` / `PyEvalTool` / guard (reuse or twin `IsolatedJsGuard` size+host; Python extra tokens `import os`, `open(`).
2. `FakePyEvalPort`: numpy-sum of array without `return`; host/timeout.
3. `:tool:py` Android library + Chaquopy freeze numpy/pandas, arm64 only, `AndroidPyEvalPort`.
4. Sideload `ChannelTools` register/remove; settings toggle → `refreshChannelTools`; strings; confirm + display name.
5. Play leak: gradle + `PlayShortcutCopyTest`; `ChatPromptAssemblerTest`.
6. Do not add `py_eval` to `LOCAL_TEACH_NAMES`.

## Validate

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:llm:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

Sideload device: L4 on, numpy sum `1,2` → 3; L4 off, remote tools list has no py_eval.

## Rollback

Remove `:tool:py` from `settings.gradle.kts` / `sideloadImplementation` if Chaquopy breaks Play merge.

## Start gate

`implement.jsonl` / `check.jsonl` curated. User must approve this planning summary before `task.py start`.
