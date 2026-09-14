# Chat 按住说话流式回显

## Goal

按住麦克风时，现有 overlay 能看到正在识别的字；松手后仍把最终文本接到草稿，人再点发送。不自动跑任务。

## User value

现在要松手才出字，长句中间不知道识别对不对。UF-02 要「实时回显部分识别文本」，`08-27-voice-composer` 当时没做。

## Background

- 按住最长 15s，松手一次性 `transcribe`，`appendVoiceTranscript` 追加草稿，不 `submit`。
- Overlay：波形 + 「正在录音」；松手后 「正在进行本地识别...」。PCM 不进 `:feature:chat`。
- `SherpaJni.decode` 用 **OfflineRecognizer**，catalog 钉 `sherpa-onnx-paraformer-zh-2023-09-14`。无 `OnlineRecognizer`。
- `speech_input` 仍是约 3 秒 `listen()`，与 Composer 分立。Play ASR 按需下载。
- 禁止 PCM / 转写进 Logcat、`AuditLog`、通知。

## Decisions

- 部分字只在 overlay **临时行**：有字则替换「正在录音」，无字保持「正在录音」。草稿框在按住期间不变。
- 松手或满 15s 才 `appendVoiceTranscript`；失败 / 空音频草稿不变，临时行消失。
- **用现有离线 Paraformer**，边录边对已收 PCM 重解。不新增流式模型、不改 catalog。

## Requirements

- R1 按住时 overlay 显示部分识别文本（尚无字时「正在录音」）。草稿框不变。
- R2 松手（或满 15s）后与现网一致：最终文本追加草稿（已有字则前加空格）；不 `submit`；临时行清空。
- R3 失败用现有中文常量，草稿不变；不造假文本。部分转写失败不弹错，保持上一行或「正在录音」。
- R4 PCM、部分/最终转写不进 Logcat、通知 extra、`AuditLog`。
- R5 不改 `speech_input` 成功 JSON；不把权重打进 APK；不新下载 ASR 档。

## Out of scope

- 松手自动 `submit` / 意图短路径 / TTS 念回复。
- 新增 sherpa Online 流式包、SenseVoice、规则 D、Kokoro。
- 云端 `SpeechRecognizer`、麦克风 FGS、改 15s 上限。

## Acceptance Criteria

- [x] AC1 按住说话时 overlay 可见部分字（或仍「正在录音」直到有字）；草稿框不被半句改写；松手后追加最终文本，任务未自动跑。
- [x] AC2 未授权 / 无模型 / 不在前台 / 空音频：既有中文错误，草稿不变。
- [x] AC3 PCM 与转写不进 Logcat / `AuditLog`；`checkChannelLeak` 通过；Play/sideload 仍无第二套 ASR 权重。
