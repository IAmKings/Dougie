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
| Disallowed app intent URI | Allowlist rejects tel/sms/file/javascript/content/intent/mailto and unknown schemes | `该链接不被允许打开。` |
| App intent while background | `AppIntentTool` foreground check | `应用不在前台，无法打开应用或链接。` |
| App intent resolve/start fail | No matching activity / launch exception | `无法打开该应用或链接。` |
| Tap/swipe without sideload consent | `TapSwipeTool` consent gate | `未完成侧载知情同意，无法执行屏幕操作` |
| Tap/swipe without Accessibility | Service instance null | `未开启无障碍服务，无法执行屏幕操作` |
| Tap/swipe on bank/pay/password app | `HighRiskForeground` | `该应用不允许自动点击或滑动。` |
| Speech while background | `SpeechInputTool` foreground check | `应用不在前台，无法使用语音输入。` |
| Speech model file missing | `filesDir/models/asr/model.int8.onnx` + `tokens.txt` absent | `离线语音模型尚未就绪，无法识别。` |
| Speech engine not wired | `UnwiredSpeechEngine` / `isEngineReady() == false` | `离线语音引擎尚未接入，无法识别。` |
| Speech capture empty | Recorder returned zero samples | `没有听到有效语音，请靠近麦克风后重试。` |
| TTS fallback too long | Offline TTS unready and `text` longer than 80 chars | `离线语音未就绪，只能播报短提示。` |
| TTS network voice | System voice `isNetworkConnectionRequired` | `系统语音需要联网，已拒绝播报。` |
| TTS speak failed | Engine init/speak failed | `语音播报失败，请稍后重试。` |

`AgentException.userMessage` is what LoopEngine copies into `lastError`. Gateway throws before `LlmProvider.stream` is collected, so blocked egress never becomes a network error. Do not map OkHttp `call.cancel()` to `LLM_FAILED`.

## Don't: Silent Fake fallback

When egress is blocked, fail the task. Do not swap in `FakeLlmProvider` on the app chat path.
