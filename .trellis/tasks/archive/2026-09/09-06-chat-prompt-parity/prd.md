# 共用身份与任务上下文

## Goal

远程与本地从同一份拼装函数得到 Dougie 人设与任务上下文。人设为中文、不含工具名。本刀不接 LiteRT `ToolCall`。

## Background

远程 `systemPrompt` 现为英文 `SYSTEM_PROMPT`（含工具清单）+ 附件说明 + `retrievedMemories`。本地 `promptFor` 几乎只有用户句。Loop 两边都会检索记忆。有对话 LLM 时 MiniRBT 已跳过。

## Requirements

- R1 `:core:llm` 单一拼装；远程 `messages[system]` 与本地前缀都调用。禁止两处各写人设。
- R2 人设中文，唯一原文。不含 battery/time/clipboard_* 等工具名（归 `09-06-local-chat-tools`）。
- R3 记忆与附件说明规则与现在远程一致：SCREEN 只元数据、不夹像素；本地不发 `image_url`。
- R4 不 Logcat 拼好的提示。Play 不新增 LiteRT。

## Out of scope

- LiteRT 工具调用、本地视觉、采样调参、把 MiniRBT 挡回 LLM 前。

## Acceptance Criteria

- [ ] AC1 JVM：拼装含中文人设（无工具名）、记忆、附件说明；远程请求 body 的 system 来自该函数。
- [ ] AC2 本地用户串 = 拼装 + 用户句（及既有 toolTrace 拼接）；有 memories/attachments 时出现在串里。
- [ ] AC3 侧载关出境问「你是什么模型」能对上 Dougie / 本机优先，不出现剪贴板正文。
