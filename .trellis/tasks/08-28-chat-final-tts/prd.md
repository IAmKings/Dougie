# Chat Final Answer 自动播报

## Goal

语音发出去的 Chat 任务在 `COMPLETED` 后，用离线 TTS 念 `finalAnswer`；播放中可把发送钮当停止。不自动 `submit`，不改 `speech_output` 成功 JSON。纯打字发送不播。

## User value

按住说话组完 Prompt 后可以听回复。音频本机合成与播放。打字对话保持安静。

## Background

- 根 `PRD.md` UF-02 / §6.9 / 规则 C：正式回复不得静默用系统 TTS，应提示「语音回复暂不可用」。§11.6 的「停止并取消任务」在本切片不适用：播报发生在 `COMPLETED` 之后，停止只停音频。
- `speech_output` 已给 Agent；`LoopEngine` 完成后不播。`SherpaJni.speak` 阻塞写完 `AudioTrack`，现无 stop。`:feature:chat` 不得直连 sherpa / `AudioTrack`。
- 设计稿全屏播报层不做；录音 overlay 仍只服务按住说话。

## Decisions

- 仅当本回合发送前有过**成功**的按住转写进草稿时，该次 `submit` 带 `speakReply=true`。失败识别不计数。发送后清零标记。`ChatViewModel.retry` 沿用任务上的 `speakReply`。
- 停止：播放中发送钮改为停止（`contentDescription`：**停止播报**）；对话仍在 Chat。附件行状态句：**正在播报...**（非错误色）。失败/缺模型用同一行展示「语音回复暂不可用」。
- Overlay / Play 气泡打开的同一 `ChatRoute` 同一规则。
- 离开 Chat（Activity `onStop`）或新发送：先停播放。不后台续播。
- 最新一条已完成回复气泡下提供 **播报**；播报中改为 **停止播报**。卡片原文不变；离线 TTS 把 ASCII 数字扩成中文读音。

## Requirements

- R1 `COMPLETED` 且 `finalAnswer` 非空且 `speakReply`：前台离线 TTS 播全文。同一 `taskId` 只自动播一次。
- R2 离线未就绪或合成失败：不走系统 TTS 念回复；附件行「语音回复暂不可用」；气泡仍在；任务保持 `COMPLETED`。
- R3 点停止：声音立刻停，任务仍 `COMPLETED`，可继续打字/按住。气泡 **播报** 可再次播放同一条 `finalAnswer`。
- R4 PCM、回复全文不进 Logcat、通知 extra、`AuditLog`。
- R5 不改 `speech_output` 成功 JSON；Play 不内置 TTS 权重。宿主播报不是 Agent Tool 调用。
- R6 离线播报前把 ASCII 数字扩成中文（卡片仍显示阿拉伯数字）。

## Out of scope

- 流式 ASR、意图路由、Kokoro、系统 TTS 念正式回复。
- 全屏播报层、输入栏总开关、后台播放、媒体通知、耳机键、边生成边播。
- 禁止模型调用 `speech_output`（若模型已播，宿主仍可能再播一次）。
- Play 截屏补测、端侧对话 LLM。

## Acceptance Criteria

- [ ] AC1 本回合成功用过麦并发送：完成后播 `finalAnswer`。纯打字发送不播。
- [ ] AC2 点停止：声音停，任务仍完成；发送钮恢复。
- [ ] AC3 TTS 缺失或失败：附件行「语音回复暂不可用」，不走系统 TTS 念回复。
- [ ] AC4 重试带 `speakReply` 的失败任务：完成后仍播。
- [ ] AC5 `AgentTask.speakReply` 缺省 false；旧快照可解码。JVM：`speakReply` 传递/重试 + 停止不改 `COMPLETED`；`speech_output` 回归。Play `checkChannelLeak`。
- [ ] AC6 最新完成回复下有播报；停止后点播报再念同一条。失败气泡仍是重试。
- [ ] AC7 `2026年8月29日15点03分` 离线引擎收到中文数字，卡片仍是阿拉伯数字。
