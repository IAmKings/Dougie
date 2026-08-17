# Phase 5a：侧载真实点击 / 滑动

## Goal

在 **sideload** 渠道把 `tap_swipe` 从占位错误升级为真实 Accessibility 手势：经知情同意、L3 每次确认、幂等键 `taskId+toolCallId` 后，对非银行/支付/密码管理器前台应用执行 tap 或 swipe。

Play 包仍必须零 Accessibility / 零 `TapSwipeTool`。本切片不含 ASR、TTS、本地 LLM。

## Background

- 双渠道与知情同意、L3 确认链、无障碍 Service 占位已在 `08-17-play-sideload-flavors` 落地。
- 根 `PRD.md` §6.7 / §10.2：Play 不得自动化点击第三方 App；侧载可提供 FGA 式点击，但禁止银行、支付、密码管理器。
- 匹配结果仍视为不可信；本任务只做手势，不把 `screen_match` 与点击绑死成一条流水线。

## Requirements

- Sideload 在同意且无障碍已开启时，`tap_swipe` 通过 `dispatchGesture` 执行 tap（`x`,`y`）或 swipe（`x`,`y` → `x2`,`y2`）。
- 无同意、无障碍未连接、前台包在拒绝名单、手势失败：返回中文错误且 **不得** 发出手势。
- 成功结果写入 `IdempotencyStore`；同一 `idempotencyKey` 不得第二次 dispatch。
- Play 构建：`checkChannelLeak` 仍绿；play `ChannelTools` 不引用 accessibility 类型。
- JVM 单测覆盖：同意/服务/拒绝名单/幂等/参数校验；不依赖真机手势。

## Constraints

- `:core:*` 禁止 `android.*`；手势与拒绝名单留在 `:tool:accessibility`。
- 不得把 `TapSwipeTool` 挪进 `:core:tool`。
- 不实现离线语音或本地 LLM。
- 不绕过系统无障碍授权对话框。

## Acceptance Criteria

- [ ] Sideload 同意 + 服务已连接 + 非拒绝名单：fake/port 记录到一次 tap 或 swipe，结果 `ok=true`。
- [ ] 拒绝名单包名：零 dispatch，中文错误说明不允许自动点击。
- [ ] 无同意 / 服务未连接：零 dispatch。
- [ ] 同一 `taskId+toolCallId` 第二次 execute 不第二次 dispatch。
- [ ] `./gradlew :tool:accessibility:test :app:checkChannelLeak` 通过。
- [ ] Play 合并清单与 classpath 仍无 Dougie Accessibility / TapSwipe。
