# Design

`LoopEngine` 在念出来短路径之后、MiniRBT 之前：`completeFromMatchThenTapIfMatched`。

- 短语：normalize 后匹配「点击」「按一下」，或含「点」且不含「几点」（「现在几点了」不得命中）。
- 有 `screen_match` 工具才继续。先 `executeToolPass(screen_match, {template_id:solid})`。
- 解析结果 JSON：`found!=true` → Halt，沿用 match 的 fatal/`SCREEN_MATCH_FAILED`，不调用 tap。
- `found=true` 且 `tools` 含 `tap_swipe`：第二次 pass，`action=tap` + 该 x,y。无该工具 → Halt + 现有 `TAP_SWIPE_CONSENT`（Play / 未同意都不注册）。
- `completionPath=LOCAL_INTENT`。确认拒绝走现有 `CONFIRM_REJECTED`。
- `:core:runtime` 只用字符串工具名。不改 `LOCAL_TEACH_NAMES`、`LOCAL_AFTER_TOOL_RESULTS`。

## Rollback

删除该短路径函数与短语解析。
