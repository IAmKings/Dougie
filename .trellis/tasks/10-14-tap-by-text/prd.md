# 按文本点击控件（tap_by_text）

## Goal

用户提出：点击「文字描述的按钮」（如「底部的记录 tab」）应直接用无障碍服务定位控件并点击，而非让模型猜坐标。落地新 sideload 工具 `tap_by_text`：无障碍树按文本定位可点控件 → 边界中心派发真实手势 → 单边状态指纹验证。这消除坐标猜测这一最大能力短板，让「打开X，点击Y」类复合指令真正可用。

## Confirmed facts

- 树读取能力已具备：`canRetrieveWindowContent=true`、`AccessibilityTreeSnapshotPort` BFS（300 节点上限）已在产；点击用 `GesturePort.tap`（真实触摸语义，与 tap_swipe/高危拦截/确认门同构）。
- 真机证据（tap-evidence）：文本+contentDescription 在导航/Tab/对话框场景覆盖良好；匹配策略须做空白不敏感（TreeVerifier.compact 同规则）。
- 模型只需要说 needle（「记录」），定位全在本机；不匹配时非致命观察让模型换词重试（replan），无隐私外泄（树内容不出调用栈，只有命中与否+坐标进结果）。
- tap_swipe 不在 14 条冻结契约/ALLOWED_TOOLS 内；新增 sideload 工具不影响冻结集，`checkChannelLeak` 把关。

## Requirements

- R1 core/tool 新增纯 JVM 选择器：`TapCandidate(text, desc, clickable, centerX, centerY)` + `TapTargetSelector.select(candidates, needle)`——needle 空白不敏感包含匹配 text/desc；命中中优先 clickable；同分取文档序最后者（≈屏幕下方，天然契合「底部 tab」）；无命中返回 null。
- R2 core/tool 新增 `TapSourcePort`（`suspend fun candidates(): List<TapCandidate>`）+ `FakeTapSourcePort`。
- R3 tool/accessibility 新增 `AccessibilityTapSource`：BFS 收集全部节点的 text/desc/clickable/边界中心（上限 300 节点）。
- R4 tool/accessibility 新增 `TapByTextTool`（name=`tap_by_text`，L3，args `{text}` 非空 ≤50）：consent→connected→前台→HighRisk 拦截→选择器→未命中返回**非致命** `{"ok":false,"found":false,"error":TAP_TEXT_NOT_FOUND}`（模型可换词重试）；命中→手势点击边界中心→单边 awaitStateChange（before 快照在手势前取）→true 则注入 `"verified":true`；无变化/null → verified=null。幂等重放保留标志。新错误文案 `UserFacingErrors.TAP_TEXT_NOT_FOUND = "屏幕上没有找到匹配的文字控件，可以换个说法再试。"`。
- R5 接线：sideload `ChannelTools` 注册（consent 门内，与 TapSwipeTool 并列，传 TreeProbeBridge/新 tap source）；Play 零改动。
- R6 不教本地最小档（不进 LOCAL_TEACH_NAMES）；不动 14 条冻结契约、tap_swipe 保留（坐标模式仍有场景）。

## Acceptance Criteria

- [x] AC1 JVM 测试（选择器）：精确/包含匹配、clickable 优先、文档序最后（底部优先）、无命中 null、needle 空白不敏感。
- [x] AC2 JVM 测试（TapByTextTool）：命中点击成功+边界正确+verified 注入；未命中非致命 found:false 不点不缓存；幂等重放保留标志；确认/高危/未连服务各门保持。
- [x] AC3 红态：选择器规则测试在空实现上失败。
- [x] AC4 全量 `:core:tool:test :tool:accessibility:test :app:testSideloadDebugUnitTest :app:checkChannelLeak` 绿；冻结契约不动。
- [x] AC5 真机复测：「打开Anchor，点击底部的记录tab」→ app_intent 后模型调用 tap_by_text(text=记录) → 确认卡 → 真实点击并 verified=true（若 Anchor tab 文本可及）。

## Out of scope

- 非文本控件（纯图标无 desc）的定位；ACTION_CLICK 语义；滚动查找（屏幕外控件）；本地最小档教学。
