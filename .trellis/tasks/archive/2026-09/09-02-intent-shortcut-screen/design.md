# Design: 截屏意图短路径

## Boundaries

| Piece | Where |
|-------|--------|
| 意图 → 工具 + 空参 + 模板 | `:core:runtime` `IntentRouteAnswers` |
| 前台 / 投影 / 执行 | 现有 `ScreenCaptureTool` + `executeToolPass` |
| 工具卡中文名 | `:feature:chat` `toolDisplayName` |
| 附件 / pin | 不改 `ChatAttachmentSession`、`TaskManager.clearPin` |

`:cli` 默认无 `intentPort`，行为不变。

## Data flow

```text
attachments 或 attachedCaptureId → 整段 return null（LLM）
classify ≥ 0.5 且 intent=screen_capture
  → toolNameFor → screen_capture
  → parseShortcutArgs → "{}"
  → executeToolPass
       Halt（非前台 / 无授权 / fatal）→ FAILED，不 LLM
       Success → 「已截取屏幕。」COMPLETED LOCAL_INTENT
```

与查询短路径相同：无槽位；`completionPath` 在即将 `executeToolPass` 时写入。

## Template

成功 JSON 必须有非空 `capture_id`，以及可解析的 `width`、`height`（与工具契约一致）。用户可见句固定为「已截取屏幕。」不回显 id/宽高。

## Chat

工具卡标题「截取屏幕」；结果摘要可仍为 metadata JSON（现有 `toolResultSummary` 非 battery 原样显示）。不新增预览图、不 `addAttachment`。

任务结束后仍 `clearPin()`：下一句「这是什么」不会自动带上这一帧；用户要用作曲家截屏芯片。这是与 LLM `screen_capture` 对齐的既有行为，不是回归。

## Compatibility

- 已 pin 且任务未带 `attachedCaptureId`：工具仍返回 pin 元数据、不重截（现网）。短路径不特殊处理。
- 投影同意仍是系统一次性授权；短路径不绕过前台门。
