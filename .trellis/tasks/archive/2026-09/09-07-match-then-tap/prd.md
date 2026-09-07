# 匹配成功后确认点击

## Goal

侧载用户说「点 / 点击 / 按一下」且已有截屏时，Loop 短路径：`screen_match`（默认 `solid`）→ `found=true` 才用返回的 `x,y` 走现有 `tap_swipe` 与 L3 确认。不教 0.6B 填坐标。Play 不发手势。

## Background

本地 LLM 工具成功后禁止再出 JSON，0.6B 也填不好坐标。念出来已用短语短路径。`tap_swipe` 仅 sideload 注册；L3 每次确认；银行/支付/密码管理器拒绝。匹配结果仍视为不可信，只把坐标交给用户确认后的 tap。

## Requirements

- R1 用户话经 normalize 后含「点击」「按一下」或单独「点」（避免「几点」误伤）才走本短路径。纯「匹配一下」仍只走 LLM/`screen_match`，不出点击确认卡。
- R2 短路径：`screen_match` `template_id=solid` → `found=true` 才 `tap_swipe` `{"action":"tap","x","y"}`。两次 `executeToolPass`；第二次走现有 L3 门。不经 0.6B JSON。
- R3 无帧 / `found=false`：现有 `SCREEN_MATCH_FAILED`，零手势。无 `tap_swipe`（Play 或未同意）：中文失败、零手势、不 crash。
- R4 不把 `tap_swipe` 写入 `LOCAL_TEACH_NAMES`。`:core:runtime` 只按工具名调用，不 import `:tool:accessibility`。
- R5 不把 SCREEN 像素写入 prompt/日志。`checkChannelLeak` 过。

## Out of scope

- 在第三方 App 前台完成点击（Chat 确认卡会抢前台；`solid` 模板不是产品级定位）。后期用**目标仍可见的脚本/目标循环**执行 match/tap，确认不能走 Chat。
- 改「工具结果后禁止再出 JSON」做任意连招；教 0.6B `tap_swipe`。
- 滑动、多模板、OpenCV、每次匹配都出确认卡；悬浮球加「点击」并 `submit`。

## Acceptance Criteria

- [x] AC1 真机 sideload：无障碍已开、非拒绝名单、已截屏、说「点一下」→ 确认卡 → 确认后手势打在**当时前台**（Chat 在前则点的是 Dougie，不作为第三方点击验收）。说「匹配一下」不出现点击确认。拒绝确认零手势。
- [x] AC2 JVM：短语命中 + found → tap 参数等于 match 的 x,y 且经确认；「现在几点了」不走本路径；found=false / 无 tap 工具 → 零 tap；本地 prompt 不含 `tap_swipe`。
- [x] AC3 `:core:runtime:test` `:core:llm:test` `:tool:accessibility:test` `:app:checkChannelLeak`。
