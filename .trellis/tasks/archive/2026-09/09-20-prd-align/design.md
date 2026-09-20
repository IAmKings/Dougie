# Design: PRD 与现状对齐

## Boundaries

只改 `PRD.md`、`README.md`。不改 `:app` / `:feature:*` / `:core:*`。

## Phase 5 stamp map

| 条目 | 现状 |
|---|---|
| Accessibility `TapSwipeTool` | 已交付，仅 sideload |
| 离线 ASR | 已交付，可选下载 |
| 离线 TTS | 已交付，VITS；非 Kokoro |
| 本地意图 | 已交付；规则 E 真机过门 |
| Local LLM | 已交付，仅 sideload 下载后启用 |
| Notification | 未做自动读通知；任务进度/定时提醒 shade 已有，不标成 Notification Listener |
| Quick Settings | 已交付，打开 Chat、不 submit |
| Floating Widget | 已交付，仅 sideload 悬浮球 |
| Scheduled Agent | 已交付：到点通知 + 预填草稿，不自动 submit |
| Multi-modal Context | 未做完整多模态；截屏/相册附件已有，保持「未做」或写「仅附件，非端侧视觉 LLM」 |
| 向量语义检索 | 已交付；embedding 未就绪走 FTS |

## Compatibility

演进记录保留「MVP 曾降级向量」。§3.2 改成现状，避免执行清单撒谎。

## Risks

文档写「已交付」不等于规则 D/Kokoro 过门。Phase 5 完成标准段必须分开写测量门。
