# 多源多图附件与预览

## Goal

Chat 用一个附件按钮打开「截取屏幕 / 相册 / 拍照」；最多 4 张；芯片显示宽×高，可点开本地预览。截屏永不上传；相册与拍照仅在 `allowCloud=true` 时进入 vision。

## User value

多张图自己能核对；截屏只给本地 Tools；开了出境后模型能看相册/拍照。

## Background

- 现单芯片、不可预览；store 一帧。Chat 按钮与侧载悬浮球都是截屏。
- §6.7；Provider 纯文本；`allowCloud` + EgressGateway 已有。
- `TaskManager.submit` 拒绝空正文。Photo Picker / CAMERA 尚未接 Chat。

## Decisions

- Q1 混合出站。
- Q2 最多 4 张。
- Q3 一个附件按钮 → 三项菜单。悬浮球仍直接加截屏。
- 发送仍须非空正文（不改空 submit）。
- 相册：系统 Photo Picker 多选，不申请整库读取。拍照：相机应用 + 应用缓存文件。
- 多张截屏时，`screen_match` / pin 用列表里**最后一张截屏**。
- 杀进程后附件丢（与现 pin 一致）；旋转尽量保住元数据与进程内字节。
- 预览在 App 内全屏，不走系统分享。

## Requirements

- R1 菜单三项；满 4 张禁用并中文提示。
- R2 每张：来源标记（屏幕/相册/拍照）、宽×高、删、点开预览。
- R3 截屏像素永不进 Prompt/请求体/通知/Logcat。
- R4 `allowCloud=false`：相册/拍照也不进请求体，仅 id/尺寸可进 system 文本。
- R5 `allowCloud=true`：仅相册/拍照 JPEG（须压缩封顶）进 user 多部分；截屏仍只有 id/尺寸。
- R6 `TaskSnapshotCodec` 只存附件元数据，不含像素。
- R7 Play/侧载 Chat 同样入口；overlay 只加截屏；不 submit。

## Out of scope

- 本地视觉 LLM、向量检索、桌面、Play Accessibility、截屏出云、只附图不打字。

## Acceptance Criteria

- [ ] AC1 菜单三入口可加入列表。
- [ ] AC2 最多 4 张，可删；第 5 张（含悬浮球）进不去。
- [ ] AC3 芯片分辨率；点击全屏预览；预览不上传。
- [ ] AC4 `allowCloud=false` 或截屏：请求体无 image/长 base64/gray。
- [ ] AC5 `allowCloud=true`：仅非截屏进 vision 部分。
- [ ] AC6 空正文仍发不出去。
- [ ] AC7 JVM 测试（含 Provider 体）+ `checkChannelLeak`。
