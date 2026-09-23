# V3 日常助手

## Goal

在 v0.1.5 已发布的本地优先运行时上，让同一个人、同一套规则、同一批常用设备动作每天能重复成功。本父任务只保存这组需求、子任务地图和集成验收，不直接改产品代码。

## User value

打开应用就能按自己写的规则说话；侧载最小对话模型按一份冻结用例完成日常设备动作；早上有一条要点一次才会执行的简报。

## Background

- 现网 **v0.1.5**（`versionCode` 105），GitHub Release 已挂 `Dougie-0.1.5-play.apk` 与 `Dougie-0.1.5-sideload.apk`。`master` 在开本任务时是干净的。
- 根 `PRD.md` 是 V2.1.11 执行基线。Phase 0–4 已落地。Phase 5 里语音、意图、端侧对话、向量记忆、悬浮球、磁贴、定时草稿已交付。规则 E 真机过门。Kokoro 真机 `p95Rtf=2.2515` 未过门，产品朗读仍是 VITS。规则 D 的 500 条真机集未闭合。自动读通知、端侧视觉、商店上架未做。
- 身份是 `ChatPromptAssembler.IDENTITY` 的一句话。设置存在 `EncryptedSharedPreferences`（`PreferenceStore`）。云端系统前缀与端侧 `localPrompt` 都从 `systemPrefix` 起头。
- 端侧工具协议由 `LOCAL_TEACH_NAMES` 与关键词表打开，最小档 SKU 是 `chat-qwen06`。
- 定时已有：`ScheduleStore` 到点只发「定时提醒 · HH:mm」，通知点击把草稿填进输入框，不 `submit`。

## Child map

| 目录 | 交付 | 依赖 |
|---|---|---|
| `09-23-v3-seal` | 根 `PRD.md` 标明 V2.1 基线已随 v0.1.5 关闭，并指向本文件 | 无 |
| `09-23-soul-rules` | 设置里一段常驻规则，云端与端侧同一段注入 | 无 |
| `09-23-local-tool-contract` | 冻结中文用例 + 计分器 + 开发者页最小档试跑 | 无。与常驻规则可并行 |
| `09-23-morning-brief` | 设置里一键加入每天晨间简报草稿 | 常驻规则的注入位已在树上，简报发送时才会遵守用户规则 |

父任务不 `task.py start`。每个子任务单独规划、实现、检查、归档。

## Requirements

- R1 默认安装行为保持 v0.1.5：常驻规则关闭，没有晨间简报，工具表不增加条目。
- R2 四个子任务的验收都过，才算本父任务完成。
- R3 Play 包仍然没有无障碍、端侧对话权重、JS、Python。
- R4 本阶段不改工具注册表，不读通知，不上端侧视觉模型，不启用 Kokoro，不采规则 D 500 条，不上架，不做桌面端，定时任务不到点自己 `submit`。

## Cross-child acceptance

- [ ] AC1 四个子任务均已归档，各自验收勾选完成。
- [ ] AC2 未打开常驻规则、未添加晨间简报时，对话身份与定时行为与 v0.1.5 一致。
- [ ] AC3 `:app:checkChannelLeak` 仍通过。
- [ ] AC4 根 `PRD.md` 能看出 V2.1 已关闭，且没有把规则 D、Kokoro、读通知、端侧视觉、上架写成已完成。

## Out of scope

- 父任务目录内写 Kotlin。
- 技能市场、多文件插件、桌面端、NotificationListener、端侧视觉、自动提交、新工具、商店表格。
