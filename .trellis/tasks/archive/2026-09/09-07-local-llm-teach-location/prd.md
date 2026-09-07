# 本地小模型教定位

## Goal

侧载 0.6B 关出境时，「我在哪」走 `location` 真工具，且不回退已教会的时间/电量/读剪贴板。

## Requirements

- R1 本地 `localTeachable` 增加 `location`（无槽位 `{}`）。远程 `systemPrefix` 仍全表。
- R2 侧载 + 关出境 + 对话包 + 已授 `ACCESS_COARSE_LOCATION`：问「我在哪」，`LOCAL_LLM`，`location` SUCCESS（有粗略坐标 JSON，不是编造城市）。
- R3 时间/电量/读剪贴板仍在本地清单与协议示例中。
- R4 不 Logcat 提示；Play `checkChannelLeak` 过。未授权定位走现有 Policy 文案，不作为本刀必过。

## Out of scope

- `calendar_*`、`clipboard_write`、截屏、打开应用、语音、点击
- 精确定位、NPU、Play LiteRT、同轮云失败改跑本地

## Acceptance Criteria

- [ ] AC1 真机（定位已授权）「我在哪」→ `LOCAL_LLM` + `location` SUCCESS。
- [ ] AC2 JVM：传入全表时 `localPrompt` 含 `location` JSON 示例，仍无 `calendar_query`；`systemPrefix` 仍含日历等。
- [ ] AC3 `:core:llm:test` 与 `:app:checkChannelLeak` 过。
