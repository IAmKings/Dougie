# Error Handling

> How Agent errors reach Chat in Dougie.

## Overview

Core failures become `AgentTask.status = FAILED` and `lastError` set to a **user-facing Chinese string** from `UserFacingErrors`. Chat prefixes that string with `任务失败：`. Do not put stack traces or HTTP bodies in `lastError`.

## Error Types

| Type | When | User text |
|------|------|-----------|
| `EgressBlockedException` | Cloud provider + `allowCloud=false` | `云端调用已被拦截。请先在设置中授权数据出境。` |
| `MissingApiKeyException` | Cloud allowed but key blank | `尚未配置 API 密钥。请在设置中填写密钥后再试。` |
| LLM / tool timeout | `withTimeout` in `LoopEngine` | `模型响应超时…` / `工具执行超时…` |
| Network (`IOException`) | OkHttp failure in provider | `网络请求失败，请检查连接后重试。` |
| Other LLM HTTP/parse | Non-success or bad JSON | `模型调用失败，请稍后重试。` |
| Unknown tool | Sanitizer / unregistered name | `模型调用了未知工具，已拒绝执行。` |
| Unrepairable tool args | Sanitizer cannot coerce a typed field, or a required property is missing | `工具参数无效，已拒绝执行。` |
| `TaskManager.cancel()` | User/runtime cancels the loop job | `任务已取消。` |
| Missing Android permission | `PolicyEngine` denies before execute | `未授权，已为你跳过该操作` |
| L2 confirm reject / timeout | User rejects Confirm Card, or 60s gate timeout | `该操作需你确认后才执行` |
| Clipboard read while background | `ClipboardReadTool` foreground check | `应用不在前台，无法读取剪贴板。` |

`AgentException.userMessage` is what LoopEngine copies into `lastError`. Gateway throws before `LlmProvider.stream` is collected, so blocked egress never becomes a network error. Do not map OkHttp `call.cancel()` to `LLM_FAILED`.

## Don't: Silent Fake fallback

When egress is blocked, fail the task. Do not swap in `FakeLlmProvider` on the app chat path.
