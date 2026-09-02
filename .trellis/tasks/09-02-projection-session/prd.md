# 一次授权、多次截屏

## Goal

系统投屏同意一次后，同进程内聊天、悬浮球、短路径可反复截屏，不再每次弹系统框。像素仍只留本机、不上云。

## Background

现网每帧后 `MediaProjection.stop()` 并 `ScreenCaptureConsentStore.clear()`（`ScreenCaptureService`；spec 写明 one-shot）。原因是 ColorOS 上错误线程停 FGS 会杀进程，不是产品要每次授权。Android 14+ 须先 `startForeground(mediaProjection)` 再 `getMediaProjection`；活投影通常要挂着同类型 FGS。token 只在内存；进程死即丢。权限中心现为「去授权 / 重新授权」，无结束会话。聊天截屏仍要前台；悬浮球 `pinCurrentScreen(requireForeground=false)`。Play/侧载共用 `:tool:system`。

## Requirements

- R1 权限中心同意一次后，同进程内连续 `capture()` 不再弹系统投屏框（投影未 `onStop`、token 仍在）。
- R2 聊天按钮、LLM/短路径 `screen_capture`、侧载悬浮球共用会话。Play 与侧载同一规则。
- R3 会话结束：权限中心「结束截屏授权」、或进程被杀。之后 `hasProjectionConsent()` 为 false；再截须重新授权；无 token 仍 `PERMISSION_DENIED`。系统 `Callback.onStop` 只拆 VirtualDisplay/FGS，**不** `clear()` token（本机释放 display 会误触发 onStop，权限中心会变成未授权、无法连截）。
- R4 首次真正截屏才 `getMediaProjection` 并挂 FGS；仅在权限中心授权、尚未截屏时不挂投屏服务。两次截屏之间 FGS 与 **同一个** 全尺寸 VirtualDisplay 保持，通知文案「Dougie 可以截取屏幕」（低优先级、ongoing）。**不要**每帧 release display（会 onStop 并曾 `clear()`）。结束会话才 `stop()` + 主线程 `stopForeground`/`stopSelf` + `clear()`。
- R5 不把像素写入 Tool JSON / `AgentTask` / 日志 / 云端。宽边 ≤720。聊天前台门不变。
- R6 权限中心：未授权「去授权屏幕截取」；已授权「结束截屏授权」（可另保留重新授权：先结束再弹系统框，或只保留结束）。副标题说明同进程可多次截屏、通知栏可停、图不上云。

## Acceptance Criteria

- [x] AC1 授权后连截两次：第二次无系统投屏弹窗，两次都有彩色预览。真机第三方 App 连续 3 次悬浮球截屏均进附件且预览正确。
- [ ] AC2 「结束截屏授权」或系统停投屏后再截：须重新系统授权；未授权走既有失败文案。
- [ ] AC3 不在工作线程 `stopForeground`；宽边 ≤720。
- [ ] AC4 Play `checkChannelLeak` 过；SCREEN 不进 `image_url`。

## Out of scope

- token 落盘、开机记住投屏、空闲超时停会话。
- 改 MiniRBT、`speech_input` 短路径、OpenCV、后台 LLM 截屏。
