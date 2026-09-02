# 截屏预览默认彩色

## Goal

截屏在聊天里预览为彩色。`screen_match` 仍用派生灰度。屏幕帧不上云。不增加黑白开关。

## Background

- 用户已定：不要设置项；默认彩色预览。
- 现网 `ScreenCaptureService.toGrayFrame` 只留 `gray`；`ChatImageCodec.grayPreview` 把灰升 RGB。相册/拍照走 JPEG，云端 vision 只发 GALLERY/CAMERA。
- `GrayscaleNccMatcher` / `ScreenFrame` 仍要灰度。可在截屏时同时产出 JPEG（预览）和 gray（匹配）。
- 悬浮球可发现性（`overlay-capture-hint`）未归档；本切片改截帧格式，两条截屏入口共用。

## Requirements

- R1 聊天/悬浮球截屏全屏预览为彩色。
- R2 `screen_match` 行为与阈值不变，继续灰度 NCC；gray 在捕获时从彩色派生，不改模板库。
- R3 SCREEN 仍不进入 OpenAI `image_url`；`AgentTask` / 日志 / 通知仍无像素。
- R4 无「黑白截图」设置。Play/侧载同一预览规则。
- R5 相册/拍照路径不变。

## Acceptance Criteria

- [ ] AC1 截屏预览肉眼为彩色（非 gray 升 RGB）。
- [ ] AC2 `screen_match` 现有 JVM 用例仍过。
- [ ] AC3 带 SCREEN 附件的云端请求体无 image/gray/长 base64。
- [ ] AC4 无新设置开关。

## Out of scope

- 黑白开关；把 SCREEN 送上云；改 NCC 算法；OpenCV。
