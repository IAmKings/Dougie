# AgentTask 作为一级执行单位 + 显式状态机

把"对话"建模为可持久化、可恢复的 **AgentTask**(taskId/conversationId/status/currentLoop/maxLoops/timeoutAt 等),并配合显式状态机(`IDLE → PREPARING → THINKING → TOOL_* → COMPLETED/FAILED`)。理由:移动端进程死亡、网络中断、Tool 失败频繁,只有把任务状态落库才能可靠地支持"恢复、取消、失败态"三类语义(见 PRD §5)。

**Status**: accepted

**Considered Options**: 无状态对话流(无法可靠恢复);隐藏状态机(状态跃迁不可审计)。
