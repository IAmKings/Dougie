# Chat 麦克风识别进草稿

## Goal

Chat 输入栏可按住说话：离线 ASR 把识别结果接到草稿后面，人再点发送。不自动 `submit`。

## User value

打字不便时也能分段组 Prompt；音频不出设备、不进 Prompt/Logcat。

## Background

`speech_input` 已给 Agent（前台、`RECORD_AUDIO`、模型/引擎门、只返回文本）。Chat 麦克风目前禁用。`AudioRecordSpeechRecorder.capture()` 固定 3 秒，供 Tool 使用。人声入口单独起停，不改 Tool JSON。Play ASR 靠设置下载；侧载可 seed。禁止 PCM/转写进 Logcat 与 `AuditLog`。本切片不新增麦克风 FGS。

## Decisions

- 按住说话，松手后一次性转写；无流式回显。
- 成功接到现有草稿后；非空则先加一个空格。可多次按住分段输入。失败不改草稿。
- 单次最长 15 秒：到点自动停并转写；手指仍按住则忽略，直到松手后再录下一段。
- 未授权时在 Chat 弹出系统 `RECORD_AUDIO`（同拍照）。拒绝：`PERMISSION_DENIED`。授权后须再按住才录音。权限中心麦克风项保留。
- Composer 与 `speech_input` 分立：Agent 仍走 3 秒 `listen()`。

## Requirements

- R1 输入栏启用时可按住麦克风；松手后（或满 15 秒）在前台 + 已授权 + 模型/引擎就绪时转写并追加草稿。
- R2 失败用既有中文：`SPEECH_NOT_FOREGROUND` / `PERMISSION_DENIED` / `SPEECH_MODEL_MISSING` / `SPEECH_ENGINE_NOT_READY` / `SPEECH_EMPTY`；不造假文本、不覆盖草稿。
- R3 不 `TaskManager.submit`；空草稿仍发不出去。
- R4 PCM、转写不进 Logcat、通知 extra、`AuditLog`。
- R5 Play/侧载同一 Chat 入口；Play 不内置 ASR 权重。
- R6 错误展示在输入栏附件错误行（与附件满额同一位置）。

## Out of scope

- 意图分类、TTS、云端 SpeechRecognizer、UF-02 实时部分识别。
- 后台录音、麦克风 FGS、改 `speech_input` 成功 JSON。
- 只语音不打字发送、Play 截屏补测、端侧对话 LLM。

## Acceptance Criteria

- [ ] AC1 按住→说话→松手：草稿追加文本（已有字则前加空格），任务未自动跑；可再按住追加。
- [ ] AC2 未授权 / 无模型 / 不在前台 / 空音频：既有中文错误，草稿不变。
- [ ] AC3 发送仍须非空正文。
- [ ] AC4 满 15 秒自动停并转写，不丢已有草稿。
- [ ] AC5 系统授权：首次按住弹出；拒绝后 `未授权，已为你跳过该操作`。
- [ ] AC6 JVM 测追加规则 + `speech_input` 回归；`checkChannelLeak`。
