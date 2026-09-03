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
| LLM model unavailable | HTTP 4xx body `unavailable` / `not supported` / `RegionError` (OpenCode Go Flash 常见) | `该模型当前不可用，请更换模型后重试。` |
| LLM empty final | Loop 2+ `stop` with blank `content` after tools (SSE no `TextDelta`) | `模型没有给出回复，请重试。` 任务 `FAILED`，保留已成功 Tool 卡，可重试。禁止 `COMPLETED` + 空 `finalAnswer`（Chat 不会画 Agent 气泡）。 |
| Unknown tool | Sanitizer / unregistered name | `模型调用了未知工具，已拒绝执行。` |
| Unrepairable tool args | Sanitizer cannot coerce a typed field, or a required property is missing | `工具参数无效，已拒绝执行。` |
| `TaskManager.cancel()` | User/runtime cancels the loop job | `任务已取消。` |
| Missing Android permission | `PolicyEngine` denies before execute | `未授权，已为你跳过该操作` |
| L2 confirm reject / timeout | User rejects Confirm Card, or 60s gate timeout | `该操作需你确认后才执行` |
| Clipboard read while background | `ClipboardReadTool` foreground check | `应用不在前台，无法读取剪贴板。` |
| Screen capture while background | `ScreenCaptureTool` foreground check (before starting FGS). Overlay pin uses `pinCurrentScreen(requireForeground = false)` and does not go through this tool gate. | `应用不在前台，无法截取屏幕。` |
| Screen capture without MediaProjection token | `ScreenCaptureTool` consent check | `未授权，已为你跳过该操作` |
| Chat attachments already at 4 | Composer / overlay add, or shortcut `screen_capture` when `AgentTask.attachments.size >= 4` | `最多附上 4 张` |
| Screen match failed / low confidence | `ScreenMatchTool` must not guess | `未能匹配屏幕内容，已停止以免误操作。` |
| Disallowed app intent URI | Allowlist rejects tel/sms/file/javascript/content/intent/mailto and unknown schemes | `该链接不被允许打开。` |
| App `package:` / extra `package` not on user list | `AppIntentAllowlist` + settings 可打开的应用 | `该应用未加入可打开名单。` |
| App intent while background | `AppIntentTool` foreground check | `应用不在前台，无法打开应用或链接。` |
| App intent resolve/start fail | No matching activity / launch exception | `无法打开该应用或链接。` |
| Tap/swipe without sideload consent | `TapSwipeTool` consent gate | `未完成侧载知情同意，无法执行屏幕操作` |
| Tap/swipe without Accessibility | Service instance null | `未开启无障碍服务，无法执行屏幕操作` |
| Tap/swipe on bank/pay/password app | `HighRiskForeground` | `该应用不允许自动点击或滑动。` |
| Speech while background | `SpeechInputTool` foreground check | `应用不在前台，无法使用语音输入。` |
| Speech model file missing | `filesDir/models/asr/` layout absent. Chat mic stays visible, does not record; attachment line + **去下载** opens Settings. Permission dialog is not shown first. | `离线语音模型尚未就绪，无法识别。` |
| Chat TTS pack missing | Host `speakFinal` / **播报** hidden unless `isReplyTtsReady`. Autoplay fail still uses attachment line + **去下载**. Voice picker disabled until TTS row 已安装. | `语音回复暂不可用` |
| Speech engine not wired | `UnwiredSpeechEngine` / `isEngineReady() == false` | `离线语音引擎尚未接入，无法识别。` |
| Speech capture empty | Recorder returned zero samples | `没有听到有效语音，请靠近麦克风后重试。` |
| TTS fallback too long | Offline TTS unready and `text` longer than 80 chars | `离线语音未就绪，只能播报短提示。` |
| TTS network voice | System voice `isNetworkConnectionRequired` | `系统语音需要联网，已拒绝播报。` |
| TTS speak failed | Engine init/speak failed | `语音播报失败，请稍后重试。` |
| Chat final-answer autoplay unready / failed | Host `speakFinal` after `COMPLETED` (`speakReply`); offline TTS missing or speak failed. Task stays `COMPLETED`; attachment line only. Never system TTS for the official reply. | `语音回复暂不可用` |
| Intent model missing | `filesDir/models/intent/{model.onnx,tokenizer.json,labels.txt}` absent (historical `model.gguf` is not a layout) | `离线意图模型尚未就绪，无法分类。` |
| Intent engine not wired | `UnwiredIntentEngine` / `isEngineReady() == false` | `离线意图引擎尚未接入，无法分类。` |
| Intent low confidence | `confidence < 0.5` | `意图不够明确，请补充说明或改用云端模型。` |
| Intent infer failed | Native logits empty or ORT session fail | `离线意图推理失败，请稍后重试。` Probe still succeeds on low confidence. |
| Model download not confirmed / not https | `userConfirmed=false` or non-https URL | `未确认下载，已跳过获取离线模型。` |
| Model hash mismatch | SHA-256 of payload ≠ spec (download or import) | `离线模型校验失败，已删除不完整文件。` |
| Model download failed | HTTP/IO error | `离线模型下载失败，请检查网络后重试。` |
| Model import / scan failed | Missing pack files, unmatched extra hashes, or copy failure | `离线模型导入失败，请选择与官方清单一致的全部文件。` plus `缺少：` layout names when files are absent |
| Model directory missing | Download or tree write with no SAF tree | `请选择模型目录` |
| Model directory grant lost | Prefs still have URI after reinstall / revoked persistable permission | `请再次选择模型目录` |
| Model directory write failed | DocumentFile create/copy failed | `无法写入模型目录，请重新选择有写入权限的文件夹。` |
| Model smoke probe | Settings 测试: ASR short silence `transcribe` no throw; TTS `generatePcm` non-empty, no play; intent `classify("现在几点")` no throw (low confidence OK). Missing layout/JNI uses existing 尚未就绪 / TTS_FAILED / INTENT_* copy | Success: `语音识别测试通过。` / `语音合成测试通过。` / `意图分类测试通过。` |
| Model smoke probe timeout | Probe exceeds 90s (ASR/TTS) or 180s (intent); UI must leave 测试中 | `离线模型测试超时，请稍后重试。` |

`AgentException.userMessage` is what LoopEngine copies into `lastError`. Gateway throws before `LlmProvider.stream` is collected, so blocked egress never becomes a network error. Do not map OkHttp `call.cancel()` to `LLM_FAILED`.

## Don't: Silent Fake fallback

When egress is blocked, fail the task. Do not swap in `FakeLlmProvider` on the app chat path.
