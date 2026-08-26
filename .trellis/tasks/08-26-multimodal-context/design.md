# Design: 多模态上下文接到对话

## Architecture

```
ChatScreen  --onAttachScreen-->  MainActivity / :app
                                      |
                                      v
                         AndroidScreenCapturePort.capture()
                                      |
                                      v
                         ScreenFrameStore.put + pin(frame.id)
                                      |
ChatScreen chip (width x height only)
                                      |
send --> TaskManager.submit(text, attachedCapture=...)
                                      |
AgentTask.attachedCaptureId/width/height  -->  OpenAI systemPrompt 一行
                                      |
Loop: ScreenCaptureTool sees pin --> return metadata, skip port.capture()
      ScreenMatchTool stays store.last()  (pinned frame is last)
```

`:feature:chat` 只增加回调与芯片 UI，不依赖 `:tool:system`。

## Contracts

- **Chip**：`已附上 · {width}×{height}` + 说明「将截取当前屏幕」。无 Bitmap。
- **AgentTask**：可选 `attachedCaptureId: String?`、`attachedWidth`/`attachedHeight`。`TaskSnapshotCodec` 编解码；旧 JSON 无字段视为未绑定。
- **System 附加**（无像素）：若绑定，在既有 `SYSTEM_PROMPT` 后追加一行，例如：`User attached screen capture_id=<id> (<w>x<h>). Use screen_match on this frame. Do not call screen_capture unless the user asks for a new capture.` 中文回复要求保持原句。
- **Pin**：`ScreenFrameStore` 增加 pin：`put` 在 pin 生效时拒绝覆盖（或 `ScreenCaptureTool` 在 `pinned()` 非空时直接返回）。任务结束（COMPLETED/FAILED）清 pin，不强制清 last。
- **失败**：附上走与 Tool 相同的前台/授权检查，UI 展示 `UserFacingErrors` 中文，不写假 `capture_id`。

## Compatibility

- 不改 Play/Sideload 通道边界。不改 MediaProjection FGS 线程约定。
- CLI 无附上按钮；无绑定字段时路径不变。

## Trade-offs

- Chat 在前台时冻帧是 Chat UI：已接受。真·他 App 冻帧需改前台门，明确延期。
- 不把 `capture_id` 拼进用户气泡，以免 History 把绑定当成用户原文；元数据走 task 字段 + system。
