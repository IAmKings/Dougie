# Design

## Boundaries

- `:core:model` — `RiskLevel.L4`
- `:core:tool` — `JsEvalTool` + `JsEvalPort` + `FakeJsEvalPort` (JVM, no Android)
- `:tool:js` — sideload QuickJS (`sideloadImplementation` only). Prefer Cash App `app.cash.quickjs` (Apache-2.0) so Android AAR stays off Play classpath; JVM tests use Fake or the JVM artifact **only in test**.
- Play `ChannelTools` must not import `:tool:js` / `JsEvalTool`.

## Tool

- Name `js_eval`, L2, required `script` + `data` (strings). Cap length (e.g. 8KiB script / 32KiB data).
- Execute: `JSON.parse(data)` into isolate as `data`; eval `script`; result must be JSON-serializable (`value`). No Java/Kotlin host objects.
- Timeout ~2s → `JS_EVAL_TIMEOUT`. Engine missing → `JS_ENGINE_NOT_READY`.
- Privilege pref is **not** read by this tool.

## Policy / UI

- `PolicyEngine`: L4 same as L3 (always confirm) for future privileged tool.
- ConfirmCard: existing `argsJson`; `toolDisplayName` 运行脚本; body copy for `js_eval` only: 隔离运行脚本，不读写文件、不上网.
- Sideload Settings (`ChannelHooks`) L4 switch + copy. Pref `scriptPrivilege` default false.

## Leak

Play classpath no `:tool:js` / `quickjs`. Play APK zip no `libquickjs` / `AndroidJsEvalPort`. `JsEvalTool` in `:core:tool` may ship unregistered on Play. Sideload may contain the .so.
