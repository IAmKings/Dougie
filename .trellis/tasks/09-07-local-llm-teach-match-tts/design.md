# Design

沿用 L2 teach：只改 `ChatPromptAssembler`。

- `LOCAL_TEACH_NAMES` 追加 `screen_match`、`speech_output`。
- `exampleArgs`：`screen_match` → `{"template_id":"solid"}`；`speech_output` → `{"text":"要念的原文"}`（不要用「你好」，0.6B 会当聊天回复）。
- `localToolProtocol` 标签：模板匹配 / 念出来。日历 `startIso` 改写是中间句（仅当用户给了日期或钟点）；最后一句把念出来/读出来/播报映射到 `speech_output` 并写 **不要建日历**（避免 0.6B 把「把你好念出来」做成 `calendar_create`）。
- `LOCAL_AFTER_TOOL_RESULTS` 不动。Loop / ScreenMatchTool / SpeechOutputTool / 宿主 TTS 不动。

## Rollback

从名单去掉这两名并删对应 `exampleArgs` / 标签分支。
