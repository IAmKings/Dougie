# Design: 多源多图附件与预览

## UI

Chat 原相机按钮改为「附件」。底部菜单三项中文：截取屏幕、相册、拍照。横向芯片列表（最多 4）：`已附上 · 宽×高` + 来源字；× 删除；点击 → 全屏 `Image`（屏幕帧从灰度升 RGB 仅用于显示）。

`:feature:chat` 只拿 `List<ChatAttachmentUi>`（id、kind、width、height、**可选**本地 preview Bitmap 由 `:app` 注入或回调加载）。像素编解码、Picker、相机、MediaProjection 在 `:app`。

## Storage

`ScreenFrameStore` 扩为按 id 的 map，最多 4；`lastScreen()` = 最后放入的 SCREEN。pin 针对该帧。相册/拍照：进程内 JPEG/ARGB，不写入 `gray` match 路径。

`AgentTask.attachments: List<AttachmentMeta>`（id, kind, w, h）。`TaskSnapshotCodec` 只编这些字段。

## LLM

`OpenAICompatibleProvider` user 消息：

- 恒有 text part = `task.input`。
- 若 `allowCloud` 由调用方传入 **且** kind ∈ {GALLERY, CAMERA}：再加 `image_url` `data:image/jpeg;base64,...`（压缩最长边 ≤ 1280，忽略 SCREEN）。
- system 仍可列全部附件的 id/kind/尺寸；SCREEN 注明 do not expect pixels。

Egress：vision 请求仍走现有 `EgressGateway`；`allowCloud=false` 时 Provider 不得把 base64 放进 JSON。不要把 JPEG 打进 `AuditLog` / Debug。

注入 `fun attachmentJpeg(id: String): ByteArray?`（仅 gallery/camera）；SCREEN 返回 null。

## Capture

截取屏幕：现 `pinCurrentScreen`，改为 `addScreenFrame` 进列表。Overlay 同样；满 4 则 `overlayAttachError` 中文「最多附上 4 张」。

## Permissions

相册：`PickMultipleVisualMedia`（maxItems = 剩余槽）。拍照：`TakePicture` + `CAMERA`（拒绝则 `PERMISSION_DENIED`）。不把 content URI 写入 Logcat。
