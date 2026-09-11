# 本地对话稳身份且闲聊不乱调工具

## Goal

侧载本地 Chat 在身份问和闲聊时用中文自称 Dougie，不罗列工具、不执行工具 JSON；用户话命中已教能力的中文直球词时，仍走现有一行 JSON。三档 SKU 共用。Play 与云端工具表不变。

## Background

2026-09-11 真机（PJZ110，侧载 `com.dougie.app.sideload`）：MiniCPM5-2B 问「你是谁」立刻调 `time`；Qwen3-0.6B 把 `可用工具:` 清单当答案。

根因在共享本地 prompt，不在 SKU 切换。`ChatLlmProvider.promptFor`（`tool/chatllm/.../ChatLlmProvider.kt:184`）调用 `ChatPromptAssembler.localPrompt`（`core/llm/.../ChatPromptAssembler.kt:52`）。`IDENTITY`（同文件 L9–10）只有「你是 Dougie…用中文回答。」首轮只要 taught 非空，就会附 `可用工具:`（L93–98）和「整段回复必须是一行 JSON」（L120）且 `time` 排第一（L137–138）。`LocalToolCallParser`（`core/llm/.../LocalToolCallParser.kt:19`）对整段 `{"name","args"}` 一律变 ToolCall。有对话 LLM 时 `skipIntentShortcut` 为 true，时间/电量等 MiniRBT 短路径不跑，所以「现在几点了」今天能工作全靠每轮都教 JSON。

2026-09-11 真机复测：时间工具 0.6B/2B 均可；0.6B「你是谁」已是 Dougie；2B 仍自称开源 OpenBMB。身份约束改为用户话之后追加正向 Dougie 锁，prompt 里不出现 OpenBMB/MiniCPM/Qwen 字样以免 2B 复读。

云端 `systemPrefix` 仍教全表。不 Logcat prompt/completion。念出来 / 精确「打开」+allowlist / match-then-tap 仍在 LLM 前走 `LOCAL_INTENT`。

## Requirements

- R1 身份：本地对「你是谁」「你是什么模型」等，中文自称 Dougie、本地优先助手；不自称 Qwen/MiniCPM 等权重名；不把 `可用工具` 当回复。加词只放 local-only 闲聊后缀，不把工具名或权重名写进共享 `IDENTITY` 常量。身份问在用户话之后再追加正向 Dougie 锁；`IDENTITY` / 闲聊后缀 / 锁均不出现 OpenBMB / MiniCPM / Qwen。问候「你好」与工具问不加锁。
- R2 闲聊：问候与无闸门命中的闲聊，`localPrompt` 不附清单、不附 JSON 协议。模型若仍吐整段工具 JSON，本轮不得执行（`ChatLlmProvider` 与 prompt 用同一谓词）。
- R3 闸门：用户输入子串命中下列**直球词**才教工具（命中后仍教现有 **整份** `localTeachable`，不按词条拆教）。不跑 MiniRBT。不把「你在哪」「在哪」「时间」当命中。模糊说法（「帮我看下表」）本轮不做。
  - 必中：几点、电量、剪贴板、打开、念出来、日历、定位、截屏
  - 同义近邻（低误伤）：电池、读出来、播报、日程、截图
- R4 真要工具时 JSON 协议、taught 名单、calendar/speech 消歧与结果后丢掉协议，保持现有行为；不扩教 `tap_swipe` / `js_eval` 等。
- R5 三档共用同一套本地 prompt；不按 SKU 分 XML / 分教表。
- R6 Play 无对话模型、无 LiteRT 泄漏；不把 prompt 打进日志。`skipIntentShortcut` 语义不变。

## Acceptance Criteria

- [x] AC1 JVM：带 taught 工具时，「你是谁」「你是什么模型」「你好」的 `localPrompt` 含 Dougie 身份，不含 `可用工具`，不含 `{"name":"time"` 等 JSON 示例。
- [x] AC2 JVM：「现在几点了」「把你好写到剪贴板」的 `localPrompt` 仍含对应一行 JSON 协议与现有 taught 全表。
- [x] AC3 JVM：`IDENTITY` 常量本身仍不含工具名、不含 Qwen/MiniCPM。
- [x] AC4 JVM：直球词表有测；「你在哪」不激活协议；`localToolProtocolActive` 在无工具结果且命中直球时为 true，身份问为 false。
- [x] AC5 真机侧载：0.6B 与 2B 各问「你是谁」——中文 Dougie、不跑 `time`、不当工具列表、**不自称 OpenBMB / MiniCPM / Qwen**。
- [x] AC6 真机：同一激活档「现在几点了」仍走 `time` 并中文回答。

## Out of scope

- MiniCPM XML；按 SKU 分教表；改云端全表；改 catalog/下载/激活。
- 关本地工具、NPU、`/sdcard/Dougie` 当运行路径、Logcat prompt。
- 恢复 MiniRBT 短路径抢走有对话 LLM 的闲聊。
- 模糊能力说法、英文 what time、把「你在哪」当定位。
