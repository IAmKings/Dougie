# 截屏意图短路径

## Goal

无附件时，高置信 `screen_capture` 走现有 `screen_capture` 工具，成功后中文终答结束、不调云端。聊天展示与 LLM 调同一工具对齐：工具卡 + 气泡，不钉作曲家。

## Background

- MiniRBT 未做短路径的标签只剩 `screen_capture`、`speech_input`（`unknown` 始终走 Loop）。本切片只做截屏。
- `IntentRouteAnswers.toolNameFor("screen_capture")` 现为 null。已有附件或 `attachedCaptureId` 时整段短路径跳过。
- `ScreenCaptureTool` 为 L1：前台 + MediaProjection；已 pin 则只返回该帧元数据。JSON 仅 `capture_id`/宽高。`isFatal` Halt 文案为 `SCREEN_NOT_FOREGROUND` / `PERMISSION_DENIED`。
- `TaskManager` 任务结束 `clearPin()`。作曲家「截取屏幕」是另一条附件路，本切片不接。
- Chat `toolDisplayName` 对未映射工具显示英文名；其它短路径工具已有中文标签。

## Requirements

- R1 无附件、无 `attachedCaptureId`、分类器就绪且置信 ≥ `IntentModelLayout.MIN_CONFIDENCE`、标签 `screen_capture` → `parseShortcutArgs` 为 `"{}"` → 现有 `executeToolPass` → 成功则 `formatFinalAnswer` 为「已截取屏幕。」+ `completionPath = LOCAL_INTENT`，`LlmProvider.stream`/`generate` 次数为 0。
- R2 不在前台、无投影授权、其它现有 Halt：任务 `FAILED`，不回落 LLM；`completionPath` 仍为 `LOCAL_INTENT`。
- R3 模板需要成功 JSON 含非空 `capture_id` 以及 `width`/`height`；缺字段 → `TOOL_FAILED`，不回落 LLM。终答不含像素、不含 capture_id（避免气泡堆 id）。
- R4 查询 / 写操作 / 开 App 短路径不变。`speech_input` / `unknown` 仍走 Loop。
- R5 Chat 工具卡：`screen_capture` →「截取屏幕」。不把该帧写入作曲家附件，不在气泡渲染像素。

## Acceptance Criteria

- [x] AC1 高置信「截个屏」类输入（无附件）：`COMPLETED`，`finalAnswer` 为「已截取屏幕。」，LLM 调用次数 0，开发者页 `本地意图`，trace 仅 `screen_capture`。
- [x] AC2 非前台 Halt：`FAILED` + 既有 `SCREEN_NOT_FOREGROUND`，LLM 0 次。
- [x] AC3 无投影授权 Halt：`FAILED` + 既有 `PERMISSION_DENIED`，LLM 0 次。
- [x] AC4 带 SCREEN 附件或 `attachedCaptureId` 的「截屏」仍跳过短路径（可跳过 `classify`），走现有 Loop。
- [x] AC5 工具卡显示「截取屏幕」；`speech_input` 仍 `toolNameFor` null。

## Out of scope

- `speech_input` 短路径
- 改 MiniRBT 权重 / 语料 / Release
- 把工具截屏钉进作曲家 4 张附件
- 气泡或 `AgentTask` 携带像素；AuditLog / Logcat 写 utterance / intent / capture 像素
- 为短路径单独改 MediaProjection / FGS
