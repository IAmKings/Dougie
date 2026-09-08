# Design

## Mode

Same `js_eval`. `privileged()` from sideload prefs.

| Pref | Wrap | Risk on confirm |
|------|------|-----------------|
| false | `JSON.stringify((function(data){ script })(JSON.parse(data)))` | L2 |
| true | `var data = JSON.parse(...)` then evaluate `script` as program; stringify last completion value | L4 |

`descriptor` is a getter so LoopEngine picks L4 when the switch is on.

## Port

`JsEvalPort.evaluate(script, dataJson, asProgram: Boolean = false): String`

Android: one QuickJS on one worker. Program: set `data`, `evaluate(script)`, if Java `null`/unsupported → `JS_EVAL_NO_VALUE`; else `JSON.stringify` via a second eval of a quoted JSON-able value, or stringify in JS:

```
(function(){var data=JSON.parse(quotedData);return JSON.stringify(eval(quotedScript));})()
```

Direct `eval` (not `(0,eval)`) so `data` is in scope. Port uses `wrapForExecute`: leading `return` still uses the L2 function wrap. No `console`.

Fake: `asProgram` + `data.reduce` without `return` still sums the array.

## UI

`confirmToolBody`: if risk L4 (or tool L4), program copy; else existing isolation copy.

## Leak

Unchanged: Play no `:tool:js` / `AndroidJsEvalPort` / quickjs.
