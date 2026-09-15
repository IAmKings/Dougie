# 会话可重命名

## Goal

用户给会话窗口起名。任务页节标题显示该名；对话页顶栏在 Dougie 下方显示当前窗口名。只在任务页改名。

## User value

多窗口时靠名字找回；人在对话页也知道自己在哪个窗口。

## Background

已归档窗口三刀。`conversationId` 默认 `"default"`，新对话为 UUID。指针在 prefs `current_conversation_id`。无 `conversations` 表。任务页节标题现算「默认会话 / 对话 n」。Chat 顶栏是 Dougie + 灵魂 + 出境。`PreferenceStore.save()` 只写 Provider 字段，不会清掉同文件里的其它键。

## Requirements

- R1 任务页吸顶标题右侧「改名」打开对话框。有自定义名则显示该名，否则回退「默认会话 / 对话 n」（编号规则与上一刀相同，仍不展示 UUID）。默认会话可改名。
- R2 空名字或只空格：去掉自定义名，默认窗口回「默认会话」，其它回「对话 n」。允许重名。最长 20 字，去掉首尾空白，换行当空格。
- R3 Chat 保留大字 Dougie；其下一行是当前窗口显示名；灵魂、出境不动。Chat 不能改名。尚未出现在任务页的空新窗口显示「新对话」。
- R4 标题存在独立 prefs 键（id → 名），杀进程仍在。**保存配置**不丢。不进 `snapshot_json`、不进 LLM、不加表、不 bump `dougie_tasks.db`。
- R5 改名不切会话、不 `openConversation`；忙时也可改。不 log 自定义名。

## Out of scope

Chat 顶栏改名、侧栏、删除会话、搜索、LLM、`listRecent(50)` 扩扫、§11.3、规则 E 续作。

## Key Decisions

- 改名只在任务页「改名」；Chat 只显示。
- 默认会话可改；空名回退。
- Dougie 下加一行窗口名。

## Acceptance Criteria

- [ ] AC1 给「对话 2」起名「工作」→ 任务页该节与 Chat 该窗口下一行都显示「工作」，不含 UUID。
- [ ] AC2 默认会话改成「家里」后 Chat / 任务页都显示「家里」；清空后两边回到「默认会话」。
- [ ] AC3 新对话（还没有任务卡）Chat 下一行是「新对话」；发完一轮未改名前，任务页为「对话 n」。
- [ ] AC4 杀进程、以及设置里点一次**保存配置**后，自定义名仍在。
- [ ] AC5 `toHistorySections` / 显示名纯函数单测（JDK 17）：自定义覆盖、空名回退、默认会话。
