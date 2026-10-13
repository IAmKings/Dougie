# 复制触发词与虚假完成声明门（E1/E2 六测）

## Goal

六测三现象：①0.6B 输出未教学工具名 JSON 被优雅拦截（防护按设计工作，0.6B 多步能力到顶，接受）；②「帮我复制"你好"」未进工具协议——`LOCAL_TOOL_ASK_NEEDLES` 有「剪贴板」无「复制」，措辞漏触发；③2B 回答时间后声称「提醒已设置」但未调用任何日历工具（假完成声明，指令封不住）。

## Confirmed facts

- 冻结 14 条契约文本不含「复制」（已核对 jsonl），加触发词不触碰冻结集。
- `LOCAL_TOOL_ASK_NEEDLES` 现有：几点/电量/剪贴板/打开/念出来/日历/定位/截屏/电池/读出来/播报/日程/截图。
- 声明门是验证层哲学的 UI 边界应用：不信任未验证的声明。检测是确定性的——输入含日历族意图词 + trace 无 calendar_create + 回复含完成声明词（已设置/已创建/已提醒/会提醒等）→ 在回答后追加诚实披露。时间缺失的「提醒我开会」本就无法直接建日程，模型本应追问；披露文案不假装它执行了。
- 0.6B 的 UNTAUGHT_TOOL_NOTICE 是既定终态（多步任务建议 2B+，记入验收清单）。

## Requirements

- R1 `LOCAL_TOOL_ASK_NEEDLES` 加「复制」（覆盖「帮我复制X」「刚才复制了什么」两个方向）。
- R2 `decideLocalReply` 文本出口加声明门：task.input 含日历族词（提醒/日程/日历/开会）且 toolTrace 无 `calendar_create` 且回复含完成声明词（已设置/已创建/已添加/已提醒/会提醒/我会提醒/帮你设置/帮你创建）→ 回答末尾追加「（注意：我还没有真正创建这个日程或提醒。）」。
- R3 已真正调用 calendar_create 的任务不追加（trace 检查）；查询类回答（calendar_query 后如实播报）无声明词不追加。
- R4 不动 14 条冻结契约、LoopEngine、云端路径；无新增日志。

## Acceptance Criteria

- [ ] AC1 JVM 测试：「帮我复制"你好"」→ localToolProtocolActive 为 true（clipboard_write 可被教学）。
- [ ] AC2 JVM 测试：日历族输入 + 无 calendar_create trace + 回复含「已设置」→ 追加披露；含「会提醒」→ 追加；有 calendar_create trace → 不追加；无声明词的正常回答 → 不追加。
- [ ] AC3 红态：声明门与触发词测试在未修复实现上失败。
- [ ] AC4 `:core:llm:test` + `:app:testSideloadDebugUnitTest` + `:app:checkChannelLeak` 绿；冻结契约测试不动全绿。
- [ ] AC5 真机七测：E2 全链路（复制→确认卡→追问读到真实剪贴板）；E1 2B 虚假声明带披露或真正走确认卡；记回 device-checklist。

## Out of scope

- 强制重规划轮（claim → force replan）——若披露仍不满足产品要求另立任务。
- 0.6B 多步能力（终态：不支持）；为 startIso 注入当前日期（若 2B 真正发起 calendar_create 但参数无效再立项）。
