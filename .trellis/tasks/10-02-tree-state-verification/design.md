# 树状态提取器与短信编辑验证 — 技术设计

## Boundary（模块归属与依赖方向）

| 模块 | 新增/改动 | 约束 |
|---|---|---|
| `:core:model` | 无改动 | verified 契约已就位 |
| `:core:tool` | `TreeSnapshot`、`TreeSnapshotPort`、`TreeVerifier`、`FakeTreeSnapshotPort`、`SmsComposeTool` 验证接入 | 纯 JVM；快照类型只在工具层存活 |
| `:core:runtime` | 无改动 | verify 分支工具无关（10-02-verification-layer 已立） |
| `:tool:accessibility` | `AccessibilityTreeSnapshotPort`（BFS 遍历） | sideload-only；实现 core:tool 接口 |
| `:app` main | `TreeProbeBridge` + `DougieApplication` 接线 | 不 import sideload 类型 |
| `:app` sideload | `ChannelTools` 填充 bridge | Play 零改动 |

## 数据流（sms_compose）

```
executeToolPass
  → SmsComposeTool.execute
      1. 前置检查不变（isAppForeground / 幂等命中带 verifiedFromJson 重放）
      2. composeSms ok
      3. probe = treeProbe()；probe == null → verified=null，原 JSON 原样返回
      4. TreeVerifier.awaitContains(probe, needle=body, 5000, 250)
           true  → JSON 注入 "verified":true → 写 IdempotencyStore → 返回
           false → {"ok":false,"error":VERIFY_FAILED,"verified":false}，isFatal=false，不写幂等
```

LLM 路径拿到 verified=false 后由既有分支处理（回灌重规划 / 2 次预算后 FAILED）；sms_compose 无捷径路径。

## 匹配语义

- **needle = 正文**（用户内容，编辑器草稿字段原样展示）。收件人不设针：`ACTION_SENDTO smsto:<canonical>` 的地址即 URI 本身，正确性由构造保证；联系人匹配会把号码显示成姓名，设针必然制造假阴性（PRD R7）。
- **空白不敏感**：`compact(s) = s.filter { !it.isWhitespace() }`，needle 与快照文本都过 compact 再做 `contains`。覆盖号码分组显示（`138 0013 8000`）、多行正文、编辑器排版空格。
- 快照 `texts` = 活动窗口内全部可见 `text` + `contentDescription`（非空白者），BFS 上限 300 节点。

## 隐私设计（对齐 logging-guidelines 与「数据不出境」）

- 快照对象只存活于工具 execute 的调用栈；禁止进 ToolResult JSON、toolTrace、日志、审计、LLM 上下文——验证产物只有布尔 verified 与既定错误文案。
- 结果 JSON 失败形态 `{"ok":false,"error":"已执行操作，但未能确认是否成功。","verified":false}` 不含任何用户内容。
- 无新增 Logcat；`AuditLog` 口径沿用 10-02-verification-layer（verified=false 记 FAILED）。

## 接线

- `TreeProbeBridge`（app main）：`@Volatile var provider: TreeSnapshotPort?`，与 `ForegroundProbeBridge` 对称的持针模式；sideload `ChannelTools.register` 时填充，Play 恒 null。
- `AccessibilityTreeSnapshotPort { DougieAccessibilityService.instance }`：service 未连或 `rootInActiveWindow == null` → snapshot 返回 null → 验证按未命中处理（sideload 服务掉线时诚实报 VERIFY_FAILED，不假报成功）。

## Compatibility

- 无探针渠道 JSON 字节不变（`withVerified` 仅 verified 非 null 时调用；`{"ok":true}` 不加键）。
- 14 条冻结契约不涉 sms_compose，不受影响。
- TaskSnapshotCodec 无需改动（verified 已在上一任务入编解码）。

## Rollback

按 Stage 两段提交（tool / accessibility+app），任一失败独立 revert；revert app 接线即恢复今日行为。

## Do not

- 不得把快照内容写进任何日志、trace、JSON。
- 不得用 LLM 判断验证结果。
- 不得为过测试放宽匹配（如包含即真）或缩短超时路径断言。
- 探针必须保持 provider 形态（每次调用取用、可为 null），不得回到上一任务修掉的非空对象形态。
