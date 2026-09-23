# 端侧工具契约 — 执行

## Order

1. 写入 14 行冻结 jsonl，并实现 `LocalToolContractEval` + JVM 测试（罐头全对 / 一条错误 / 文件形状 / 闲聊不打开协议）。
2. 看 `AppIntentRuleEEval` 与开发者页如何展示计数和 `adb` 行，按同样方式加本契约的试跑与「上次结果」。不要复制规则 E 的准确率门槛常数。
3. 试跑限定 `chat-qwen06`。Play 构建下按钮不可用，不引用侧载引擎类型到 Play 源集。
4. 在已安装最小档的侧载包上跑一遍。不过则只改关键词或协议例句，并补上对应的 `localToolProtocolActive` 测试，然后再跑。仍然不过就停，在任务笔记里记下 `n` 与 `correct`。
5. `:app:checkChannelLeak`。

## Validation

```bash
./gradlew :core:llm:test --tests com.dougie.core.llm.LocalToolContractEvalTest --tests com.dougie.core.llm.ChatPromptAssemblerTest
./gradlew :app:checkChannelLeak
```

真机试跑不进 CI。

## Rollback

删除开发者页入口并停止读取预测文件即可。冻结 jsonl 与计分器留下也不改变对话行为，除非第 4 步改了关键词。

## Do not

- 不提交 `predictions.jsonl` 或权重。
- 不在报告或 Logcat 打印用例句子。
- 不新增工具。
