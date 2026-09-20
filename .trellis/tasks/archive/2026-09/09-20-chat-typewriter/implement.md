# Implement: Chat 终答打字机

## Order

1. `nextTypewriterShown` + JVM 测试：firstFrame/reduceMotion 全文；小前缀延长立即跟上；大跳跃按 elapsed 显露；非前缀跳到 target；800ms 封顶步长。
2. `AgentBubble`（或 feed）`remember(listKey)` 驱动显示文本；完整 `item.text` 仍给播报。
3. 读 `ANIMATOR_DURATION_SCALE`，与气泡进入同一套 0 → 无打字。
4. `./gradlew :feature:chat:testDebugUnitTest`

## Do not

- 改 LoopEngine、SSE、TTS 语义、`listKey`、进入动效。
- 失败气泡 / 用户气泡打字。
- Compose UI 测试。
- `task.py start` 前未获规划批准。
