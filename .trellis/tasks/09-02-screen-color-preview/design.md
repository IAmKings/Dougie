# Design: 截屏预览默认彩色

## Data

捕获一次 Image：

1. 压 JPEG（沿用 `ChatImageCodec.MAX_EDGE` / quality 75）进 `ChatAttachmentSession.jpegs[id]`（SCREEN 也可走 jpeg map）。
2. 同帧算 `gray` 写入 `ScreenFrame` / `ScreenFrameStore`（匹配与 pin 不变）。

预览：`kind==SCREEN` 优先 `jpegPreview(session.jpeg(id))`，无 JPEG 时再 `grayPreview`（旧进程内帧）。

## Boundaries

- `:core:tool` `ScreenFrame` 仍只持 gray（JVM 匹配、无 Android）。
- `:tool:system` `ScreenCaptureService` 产出 gray；JPEG 在 `:app` 从同一 Bitmap 压出（避免 core 依赖 Bitmap）。
- 更干净：Service 返回 Bitmap 或 RGBA 给 port，Application/`ChatImageCodec` 同时 `jpegFromBitmap` + `toGray`。Port 今日返回 `ScreenFrame`。可扩 port 为 color+gray，或在 `AndroidScreenCapturePort` 回调里带 Bitmap。优先少改：Service 转 Bitmap → gray Frame + 把 JPEG 交给 session。

建议：`ScreenCapturePort.capture()` 仍返回 `ScreenFrame`（gray）；新增 `capturePreviewJpeg(): ByteArray?` 或 Frame 旁路 `lastJpeg`。最简单是 Application 层：port 增加 `suspend fun captureColor(): Pair<ScreenFrame, ByteArray>`（jpeg）。Fake port 合成彩条 JPEG。

## Cloud / logging

`OpenAICompatibleProvider.userContent` 继续跳过 SCREEN。测试保持无 gray/image。

## Compatibility

旧只灰的 in-memory 帧：预览回退灰度。不迁移磁盘（截屏本就不落盘）。
