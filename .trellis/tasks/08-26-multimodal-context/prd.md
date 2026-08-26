# 多模态上下文接到对话

## Goal

Chat 输入栏「附上屏幕」把当前前台画面冻成一帧，供**这一次发送**的本地 Tools（尤其 `screen_match`）使用。LLM 只看到 `capture_id` 与宽高；像素不出站、不进 Prompt、不进通知/Logcat。

## User value

用户指定「就用这一下」的画面，避免模型稍后自己再截到另一帧。不要求模型看懂图像内容。

## Background

- 根 `PRD.md` §6.7：截图仅本地；Tool JSON 无像素。`InMemoryScreenFrameStore` 只留 last 一帧。`screen_capture` / `screen_match` 已存在；OpenAI Provider 纯文本。
- 截屏必须前台 + MediaProjection（后台文案：`应用不在前台，无法截取屏幕。`）。Consent 一次性，`projection.stop()` 后需重新授权。
- `:feature:chat` 不得跑 Loop / 不得直接碰 MediaProjection；截屏端口在 `:tool:system`，接线在 `:app`。
- `TaskManager.submit` 拒绝空字符串。麦克风按钮保持禁用。

## Decisions

- Q1：仅本地 Tools，无云视觉 / 相册 / 拍照。
- Q2：输入栏「附上屏幕」立刻截；芯片「已附上 · 宽×高」。
- Q3：接受截到当前前台（Chat 在前台时即 Chat 自身）。芯片旁短说明「将截取当前屏幕」。不放松前台门；不做 overlay 冻帧。
- 芯片无缩略图（避免敏感画面进会话 UI）。可点 × 取消；发送成功后清除。无附件时发送仍须有正文。
- 本回合若已用户绑定，`screen_capture` 返回同一 `capture_id`/宽高、不再截，以免冲掉冻帧。

## Requirements

- R1 像素不出设备、不进 LLM Prompt / 用户气泡全文、不进通知/Logcat/`AuditLog`。
- R3 复用 `ScreenFrameStore` 与现有 `screen_capture` 端口/FGS，不新开截屏栈。
- R4 输入栏附上 + 芯片 + 前台门与既有错误文案；`:app` 捕获，`:feature:chat` 只收回调与芯片状态。
- R5 绑定元数据（id/宽高）进 `AgentTask` 快照（无灰度字节），供 system 侧文本告知模型；`TaskSnapshotCodec` 忽略未知键、缺省字段可解码旧行。
- R6 无绑定则行为与今日相同（模型仍可自行 `screen_capture`）。

## Out of scope

- 云端 vision、`image_url`、本地视觉 LLM、向量检索、桌面、相册/拍照。
- 每条消息自动截屏；倒计时切 App；悬浮球/气泡触发截屏；放松前台门。
- Play 包 Accessibility 点击。

## Acceptance Criteria

- [x] AC1 附上成功后芯片可见（宽高、无缩略图）；× 取消后发送不再带绑定。
- [x] AC2 发送后 Prompt / Tool 成功 JSON / 通知不含像素或长 base64；system 文本可含 `capture_id` 与整数宽高。
- [x] AC3 绑定回合内再调 `screen_capture` 不新开投屏，JSON 仍是同一 id/宽高。
- [x] AC4 无授权 / 非前台：既有中文错误，芯片不显示「已附上」。
- [x] AC5 `:feature:chat`、`:core:tool`、`:core:llm`、`:core:runtime` 相关 JVM 测试 + `:app:checkChannelLeak` 通过。
