# 本地小模型稳调用常用工具

## Goal

侧载 0.6B 关出境时，`time` / `battery` / `clipboard_read` 稳定走 Loop，得到真工具结果。

## User value

关云端后问几点、电量、剪贴板，不是小模型猜数字或当闲聊。

## Background

本地已是整段 JSON → `ToolCall`，成功后丢掉协议。远程与本地仍注入全表 `toolDescriptors` 供 Loop 执行。上一刀只保 `time`；全表写进本地「可用工具」会稀释 0.6B。有对话 LLM 时 MiniRBT 跳过。Play 无 LiteRT。

## Requirements

- R1 侧载 + 关出境 + 对话包：`LOCAL_LLM`，下列句式对应工具 SUCCESS，不是 `LOCAL_INTENT`、不是编造。
  - 「现在几点了」→ `time`
  - 「电量多少」→ `battery`
  - 「剪贴板里是什么」→ `clipboard_read`（前台；空剪贴板走现有空文案，仍算打中工具）
- R2 `time` 成功后中文作答，不连调到 `MaxLoopExceeded`，不以问句当答案开头。
- R3 `localPrompt` 的「可用工具」与 JSON 示例 **只含** `time`、`battery`、`clipboard_read`（从已注入描述符里过滤）。远程 `system` 仍列全表。Loop 仍持有全表，模型若吐出其它合法 JSON 仍执行，但不教、不验收。
- R4 不 Logcat 提示/补全；Play `checkChannelLeak` 过。
- R5 不引入 Play LiteRT；不把 MiniRBT 挡回 LLM 前。

## Out of scope

- 真机必过：`calendar_*`、`location`、`screen_*`、`app_intent`、`speech_*`、`tap_swipe`、`clipboard_write`
- NPU、Play LiteRT、同轮云失败改跑本地、换更大聊天模型、向量记忆、预测性返回、LiteRT 官方 function-calling API

## Acceptance Criteria

- [ ] AC1 真机三句如上，各有对应成功 `toolTrace`，`completionPath` 为 `LOCAL_LLM`。
- [ ] AC2 JVM：`localPrompt` 在传入全表时清单与协议示例只有这三名；远程 `systemPrefix` 仍含其它工具名；三件 JSON 可解析；跟轮丢掉协议；`time` 回归不过期。
- [ ] AC3 `:app:checkChannelLeak` 过。
