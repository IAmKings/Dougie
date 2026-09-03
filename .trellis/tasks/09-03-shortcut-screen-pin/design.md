# Design: 短路径截屏钉附件

## Boundaries

| 件 | 位置 |
|---|---|
| 满 4 张时不截 | `:core:runtime` 短路径：`start.attachments.size >= AttachmentLimits.MAX` → Halt `ATTACHMENTS_FULL` |
| 捕获 + 预览字节 | `ScreenCaptureTool`：`store.put(frame)`；JPEG 进 store 的预览槽（不进 tool JSON） |
| 任务结束后钉芯片 | `:app` `onTaskFinished`：仅 `LOCAL_INTENT` 且最后一条成功 `screen_capture` |
| 刷新芯片 | `MainActivity` 在任务结束后 `syncChips()`（发送时作曲家已被 `onAttachmentsConsumed` 清空） |
| LLM 捕获 | 不走上述 adopt；`releaseAfterTask` 照常 |

`:core` 不依赖 `:app`。`:cli` 默认 `onTaskFinished` 空，无芯片。

## Flow

```
submit("截个屏") → 作曲家已 consumed
Loop 短路径 execute screen_capture → put gray + jpeg
COMPLETED 「已截取屏幕。」
finally clearPin
onTaskFinished:
  if shortcut screen success:
    addScreen(frame, jpeg)  // store 已有该 id 时 addScreen 须兼容（勿 clearPin 掉刚截的帧）
    不要 clearAll
  else:
    releaseAfterTask()
Main 同步 chips
```

`ChatAttachmentSession.addScreen` 今日会 `screens.clearPin()` 再 `put`。Adopt 时 pin 已 clear，同 id `put` 应覆盖成功。不要在 adopt 前 `clearAll`。

## JPEG

`ScreenFrame` 仍只灰阶。`InMemoryScreenFrameStore` 增加进程内 `jpeg(id)` / `putJpeg`（或 `CapturedScreen` 写入同一 store）。Chat 预览只读 session JPEG；adopt 时拷进 `jpegs`。禁止 JSON / Logcat / `image_url`。

## Compatibility

- 侧载短路径引导句可留。
- 有 SCREEN 附件仍跳过短路径。
- Overlay `pinCurrentScreen` 不经 Tool，路径不变。
