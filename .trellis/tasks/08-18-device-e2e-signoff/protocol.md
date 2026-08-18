# Dougie 真机 E2E 协议（PRD §16.1 Case 01–14）

本文件供另一人在 **Android 10+ 真机**（优先 13–16）上复做签字。只记现场现象，禁止编造通过。本轮不填写 `findings.md`；跑完后再写 14 行结论。

产品名始终是 **Dougie**。对话类 Case 默认 **play debug**。不为签字加超时调试开关，也不写 Espresso。

---

## 结论怎么记（跑完后写入 findings.md）

每条只能是 **通过** / **失败** / **受阻**，禁止「未跑」。

| 结论 | 必须写清 |
|---|---|
| 通过 | 日期、机型与 Android API、渠道（play / sideload）、可观察现象 |
| 失败 | 复现步骤、与「通过标准」的差、截图或原话 |
| 受阻 | 缺条件（无日历账号、拒截屏、无法让 Tool 超过 15s 等）+ 仓库里已有的单测证据路径 |

Chat 失败气泡带前缀 **`任务失败：`**，后面才是 `UserFacingErrors` 原文。对照时以完整气泡为准。

---

## 全局前置

### 环境

- JDK **17**。本机示例：`export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- 仓库根目录执行 Gradle。
- 真机 USB 调试；`adb devices` 可见一台设备。
- 需要 **真实 OpenAI 兼容 API Key** 的 Case：01–04、06、08–11、14，以及 Case 13 的「授权后」半段。没有 Key：这些对话 Case 标 **受阻**（缺密钥），不要假装通过。

### 构建产物

```bash
# 在仓库根目录
./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug :app:checkChannelLeak
```

| 渠道 | applicationId | APK |
|---|---|---|
| play | `com.dougie.app` | `app/build/outputs/apk/play/debug/app-play-debug.apk` |
| sideload | `com.dougie.app.sideload` | `app/build/outputs/apk/sideload/debug/app-sideload-debug.apk` |

```bash
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
# Case 12 再装 sideload；此前对话 Case 只装 play
```

两包可同机共存，互不覆盖。

### 设置（每次改完必须点「保存配置」）

1. 底栏 **设置**。
2. 开关 **允许显式数据出站**（默认关）。说明文案：`本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。`
3. 选厂商预设或填写 Base URL / 模型 / `maxTokens`（16–8192）。
4. 填 API 密钥。
5. 点 **保存配置**。未保存则下一轮 `submit` 仍用旧策略。
6. 回到 **对话**：顶栏应为 `出境策略: 仅本地` 或 `出境策略: 已授权云端`。
7. **开发者**入口在设置页（「开发者」）。任务进行中可看到 `taskId`、`status`、`loopCount`、`lastError`。

### 权限中心（对话顶栏盾牌 / 「权限中心」）

按 Case 需要授权：**读取日历**、**写入日历**、**粗略位置**、**屏幕截取**。日历相关 Case 需要系统里至少有一个日历账号；没有则 Case 03/04/09 **受阻**。

### 推荐执行顺序

1. 只装 play → **Case 13**（先拦截，再授权并保存）→ 保持云端已授权。
2. Case 01 → 02 → 03 → 04 → 05 → 10 → 11。
3. Case 06（超时）。
4. Case 08 → 09 → 14（杀进程；09 不要用「重试」验证幂等）。
5. Case 07（多半受阻）。
6. Case 12（再装 sideload、核对 play 无 Accessibility）。

### 与签字无关（不要当通过条件）

Phase 5 全量 ASR、第三方 UI 模板包、OpenCV AAR、本地 8K tokenizer。`screen_match` 模板目录只有 JVM 内置 `solid` / `logo`。

---

## Case 01 — 普通聊天 → Final Answer

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | **允许显式数据出站 = 开**，已 **保存配置**；对话显示 `出境策略: 已授权云端` |
| 权限 | 无 |
| 真实 API Key | **要** |

**步骤**

1. 打开对话，输入一句不需要 Tool 的话，例如：`用一句话介绍你自己。`
2. 发送，等到状态结束（输入框重新可点）。

**通过**

- 出现 **思考中… [循环 n]**（进行中），最终一条 Agent 气泡终答（无 `来源：`，因未检索记忆亦可）。
- 状态完成，不是 `任务失败：`。
- 设置 → 开发者：该次 `status` 为 `COMPLETED`，`lastError` 显示 **无**。

**失败 / 受阻**

- 失败：终答缺失、一直转圈、或出现出境/密钥错误。
- 受阻：无 Key、上游不可达且无法换供应商。

---

## Case 02 — 询问电量 → Battery Tool → Answer

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 无（电量为 L0） |
| 真实 API Key | **要** |

**步骤**

1. 可用空态示例芯片 **`我现在手机还有多少电？`**，或手输同句。
2. 发送，等到完成。

**通过**

- 出现工具卡，名称 **电池工具**（内部 id `battery`）。
- 卡上可见电量摘要（如 `63%, charging: true` 形式）或含 `battery_percent` 的 JSON。
- 终答用自然语言提到电量，且与工具结果大致一致。

**失败 / 受阻**

- 失败：无电池工具卡、终答与工具数字明显矛盾、任务失败。
- 受阻：模型始终不调 `battery`（可换更直白的「读电量工具」再试一次；仍不调则失败，不是受阻）。

---

## Case 03 — 查询日历 → Calendar Tool → Answer

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | **读取日历** 已授予；设备有日历数据或至少有账号 |
| 真实 API Key | **要** |

**步骤**

1. 权限中心确认「读取日历」已授权。
2. 对话：`查一下我接下来几天的日程。`
3. 等到完成。

**通过**

- 出现 **日历查询** 工具卡（`calendar_query`）。
- 终答基于工具结果（有日程则提到标题/时间；无日程则明确说没有/未找到，而不是编造）。

**失败 / 受阻**

- 失败：有读权限仍 `任务失败：未授权，已为你跳过该操作`；或未调日历却编造日程。
- 受阻：系统无日历账号 / OEM 日历权限弹窗无法完成。写明机型。

---

## Case 04 — 创建日历 → Confirmation → Calendar Tool

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | **写入日历**（及查询所需读权限）已授予 |
| 真实 API Key | **要** |

**步骤**

1. 使用 **唯一标题**，避免和已有日程混淆，例如：`DougieE2E-04-<日期时刻>`。
2. 发送：`请在日历创建一个事件，标题是「DougieE2E-04-…」，时间用明确的明天某个整点。`
3. 出现确认卡后核对：标题 **确认 创建日程**、风险 **L2**、文案 **该操作会写入设备数据。确认后才会执行；拒绝则跳过。**、参数 JSON 含 `title` 与 `startIso`。
4. 点 **确认**（不要点拒绝）。
5. 打开系统日历，核对该标题事件 **恰好一条**。

**通过**

- 确认前 **没有** 新事件。
- 确认后出现 **创建日程** 成功工具卡，任务 COMPLETED。
- 系统日历中该唯一标题 **一条**。

**失败 / 受阻**

- 失败：未出确认卡就写入；确认后未写入；或一次确认写出两条。
- 受阻：无写日历权限/账号。拒绝路径记在 Case 05，不要和本 Case 混为失败。

---

## Case 05 — 拒绝权限 → Tool 不执行

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | **刻意不授予** 目标权限（见步骤） |
| 真实 API Key | **要** |

做 **路径 A** 即可签字；路径 B 可选（确认拒绝）。

**步骤 — 路径 A（运行时权限，主路径）**

1. 系统设置中 **关闭** Dougie 的日历或定位权限（或首次对「粗略位置」点拒绝）。
2. 对话触发对应工具：无定位时问 `我现在大概在哪？`（`location`）；无读日历时问 `查一下我的日程`。
3. 若系统再弹权限框，点 **拒绝**。

**步骤 — 路径 B（L2 确认拒绝，可选）**

1. 写入日历权限保持授予。
2. 再走一次创建日程直到确认卡，点 **拒绝**。

**通过**

- 路径 A：气泡含 **`任务失败：未授权，已为你跳过该操作`**（`PERMISSION_DENIED`）。权限中心该项仍为未授权。系统日历 **没有** 因本轮产生的新事件。开发者页不应出现该 Tool 的成功执行痕迹。
- 路径 B：气泡含 **`任务失败：该操作需你确认后才执行`**（`CONFIRM_REJECTED`），日历无新事件。

**失败 / 受阻**

- 失败：拒绝后仍执行 Tool / 仍写入日历。
- 受阻：OEM 无法关掉权限或模型不调该 Tool。换更直白的指令再试；仍不调则失败。

---

## Case 06 — LLM Timeout → 错误 + 重试

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 无 |
| 真实 API Key | **要**（或故意无效的上游，见步骤） |

生产默认 LLM 超时 **60s**，**没有** 强制超时开关，不要改代码。

**步骤（任选能观察到超时的一种）**

1. **等待约 60s**：提交一句简单问题后立刻断网（飞行模式），保持约 60s 以上；或把 Base URL 指到会挂起、不返回 SSE 的地址后 **保存配置** 再发送。
2. 出现失败后，点对话 **重试**（同一句用户输入）。
3. 恢复可用上游与网络后再观察重试。

**通过**

- 失败气泡含 **`模型响应超时，请稍后重试。`**（`LLM_TIMEOUT`）。允许同时看到 `任务失败：` 前缀。
- 出现 **重试**。点重试后开发者页 **taskId 与失败那次不同**（新任务，不续传旧 LLM 流）。
- 若断网极快变成 **`网络请求失败，请检查连接后重试。`**（`NETWORK_FAILED`）而从未等到 60s：本 Case **受阻**（未能诱导 LLM 超时），不要标通过。可在 findings 注明网络错误路径是通的。仓库旁证：`core/runtime/src/test/kotlin/com/dougie/core/runtime/LoopEngineTest.kt` 的 `llmTimeoutFailsTaskWithReadableError`。

**失败 / 受阻**

- 失败：明确卡死超过 60s 仍无失败态、或重试复用同一 taskId / 试图续传半截流。
- 受阻：只能稳定打出 `NETWORK_FAILED` / `LLM_FAILED`，无法打出 `LLM_TIMEOUT`。

---

## Case 07 — Tool Timeout → 错误

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 视所调 Tool 而定 |
| 真实 API Key | 若走真机诱导则 **要** |

生产默认 Tool 超时 **15s**。**禁止** 为签字加调试开关或慢工具。

**步骤**

1. 尝试让某个已注册 Tool 执行超过 15s（真实设备上电量/时间/日历通常远小于 15s，**预期做不到**）。
2. 若 15s 内所有 Tool 都返回：停止尝试，按 **受阻** 记录。

**通过（仅当真机真的超过 15s）**

- 气泡含 **`工具执行超时，请稍后重试。`**（`TOOL_TIMEOUT`）。
- 可点 **重试**，且为新 `taskId`。

**受阻（预期默认）**

- 条件：无法让任何 Tool 执行超过 15s。
- 单测证据（必须抄进 findings）：
  - 文件：`core/runtime/src/test/kotlin/com/dougie/core/runtime/LoopEngineTest.kt`
  - 方法：`toolTimeoutFailsTaskWithReadableError`
  - 命令：`./gradlew :core:runtime:test --tests com.dougie.core.runtime.LoopEngineTest.toolTimeoutFailsTaskWithReadableError`
- 正常电量/时间路径若 Case 02 已通过，**不要**把 Case 07 受阻升级成产品缺口。

**失败**

- 某一 Tool 明显卡住远超 15s 且任务不失败（ANR 或无限转圈）。这与「诱导不了超时」不同。

---

## Case 08 — Process Death → Task 可恢复为失败并可重新提交（不续传 LLM）

对应 PRD §16.5：恢复的是 Task 状态，**不是** LLM 流。实现：启动时 `recoverInterrupted` → `FAILED` + `INTERRUPTED`，再 `taskManager.seed`。

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 无 |
| 真实 API Key | **要** |

**步骤**

1. 发送一句需要模型思考较久的话（可复用 Case 01）。
2. 在 **思考中 / 流式未完成**（输入框仍禁用）时：系统「强制停止」Dougie，或 `adb shell am force-stop com.dougie.app`。
3. 重新打开 Dougie，进入对话。
4. 看开发者页 `taskId` / `status` / `lastError`。
5. 点 **重试**。

**通过**

- 重开后气泡含 **`任务失败：任务已中断，请重新提交。`**（`INTERRUPTED`）。开发者页 `lastError` 为 **`任务已中断，请重新提交。`**（无 `任务失败：` 前缀），`status` 为 `FAILED`。
- **没有** 从杀进程前的半截 token 接着打字。
- 底栏 **任务** 打开 **任务历史**：该条状态 **失败**，错误行为 `INTERRUPTED` 原文（同样无 `任务失败：` 前缀）。
- **重试** 使用 **相同用户原文**、**新 taskId**，并可完成或再次失败（完成即可；再次因网络失败不否定本 Case，只要没有续传旧流）。

**失败 / 受阻**

- 失败：重开后仍显示进行中并续写旧流；或中断后无法重试。
- 受阻：OEM 杀得太慢，任务已 COMPLETED 才停掉。再试一次；两次都杀不在飞行中则受阻并写 OEM 行为。

---

## Case 09 — 重复恢复 → 不重复创建事件（幂等）

与 Case 04 配合：`idempotencyKey = taskId + toolCallId`。恢复路径 **不得再次执行** 已成功的创建。

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 写入日历已授予 |
| 真实 API Key | **要** |

**步骤**

1. 使用 **新的唯一标题** `DougieE2E-09-…`（不要复用 Case 04 标题）。
2. 走创建日程 → 确认卡 → **确认**，等到 **创建日程** 工具卡已成功。
3. 若此时任务仍在 THINKING：立即 `force-stop`，再打开 App（同 Case 08）。
4. **不要点重试**（重试会换新 `taskId`，幂等键不同，可能再写一条，那不是本 Case）。
5. 在系统日历数该唯一标题的条数。

**通过**

- 杀进程并重开后：中断提示（若仍在飞行中）或已完成；日历中该标题 **仍为一条**。
- 恢复 **没有** 自动再弹确认卡、没有自动再调一次创建。

**失败 / 受阻**

- 失败：仅重开（未点重试）就出现第二条同标题事件。
- 受阻：来不及在 COMPLETED 前杀掉；或无日历。可再试一次。不要用「点重试后出现第二条」当本 Case 失败（那是新任务）。

---

## Case 10 — Memory Retrieval → 终答展示来源

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 无 |
| 真实 API Key | **要** |
| 记忆 | 底栏 **记忆** 中 **启用记忆** 为开（「新对话会检索并写入本地事实」）。之后若改设置须再 **保存配置**，以免冲掉该开关 |

**步骤**

1. 先发送一条能被记忆门控收下的话，须含标记之一：**我叫 / 我是 / 我住 / 我喜欢**。建议：`我叫小明，我住上海。`（勿含密码、`sk-`、卡号。）
2. 等 COMPLETED。打开 **记忆**，应能看到该事实；来源形如 `taskId · 我叫小明…`。
3. **再开一轮新对话**（再提交一句新问题，不要只看同一条终答）：`我住在哪？` 或 `我叫什么？`

**通过**

- 第二轮 **COMPLETED 终答** 下方出现 **`来源：`** 行，内容来自记忆条目的 `source`（可含 taskId 与摘录），**不是** 把事实正文整段当来源标签。
- 流式中的半截气泡 **没有** `来源：`；失败气泡也没有。
- 终答能用到该事实（例如提到上海 / 小明）。

**失败 / 受阻**

- 失败：记忆页有事实但 COMPLETED 终答完全没有 `来源：`；或把 `content` 冒充来源。
- 受阻：门控未写入（句子不含「我叫」等）。按建议句重试后再判。

---

## Case 11 — 屏幕感知：截图 → 匹配 → 拒绝授权

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 系统 **MediaProjection** 投屏授权；Dougie 须在 **前台** |
| 真实 API Key | **要** |

内置模板 id：`solid`（8×8 白块）、`logo`（24×24 高对比 D）。匹配结果视为不可信数据。截图不应进 Prompt 全文；工具 JSON 仅为结构化字段。

**步骤 — 授权成功**

1. 权限中心 **屏幕截取**；按系统对话框允许投屏。
2. 保持 Dougie 在前台。发送例如：`截取当前屏幕，再用模板匹配看看界面状态。` 可点名 `screen_capture` / `screen_match` 与 `solid` 或 `logo`。
3. 观察工具卡与终答。

**步骤 — 拒绝授权**

1. 撤销投屏 / 下次弹窗点拒绝。
2. 再请 Agent 截屏。

**通过（捕获链路）**

- 授权后：出现 `screen_capture` 工具卡；结果 JSON 含 `capture_id`、`width`、`height`，**不含** 像素/截图内容。
- 随后可出现 `screen_match`：JSON 含 `template_id`、`found`、`x`、`y`、`confidence`。
- 拒绝后：失败含 **`未授权，已为你跳过该操作`**（`PERMISSION_DENIED`），或明确截屏失败；**不得**在无授权时仍返回 capture_id。

**受阻（匹配，允许）**

- 截屏已成功，但匹配失败、终答或错误含 **`未能匹配屏幕内容，已停止以免误操作。`**（`SCREEN_MATCH_FAILED`）：真实主屏对不上 `solid`/`logo` 时 **可以标受阻**，不算产品失败。前提是捕获本身已通过。
- 用户拒绝系统截屏弹窗、无法完成 MediaProjection：捕获半段标受阻，不要标通过。

**失败**

- 无授权仍截到屏；或日志/聊天气泡里出现截图像素/base64。

---

## Case 12 — 双渠道包隔离

**前置**

| 项 | 值 |
|---|---|
| 渠道 | **必须** 构建并安装 play **和** sideload |
| 出境 | 本 Case 不依赖对话 |
| 权限 | sideload 可出现知情同意，不必为签字打开无障碍 |
| 真实 API Key | 否 |

**步骤**

1. JDK 17 执行：

   ```bash
   ./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug :app:checkChannelLeak
   ```

   `checkChannelLeak` 必须成功（已依赖两次 assemble）。

2. 安装两包：

   ```bash
   adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
   adb install -r app/build/outputs/apk/sideload/debug/app-sideload-debug.apk
   ```

3. 启动器中应有两个 Dougie；包名 `com.dougie.app` 与 `com.dougie.app.sideload`。分别打开，互不覆盖数据。

4. **不要写新扫描器。** 在现有检查之外，可用合并 Manifest 复核 play：

   ```bash
   # 路径以本地 intermediates 为准；checkChannelLeak 失败时会打印 play merged manifest 路径
   rg -n "AccessibilityService|BIND_ACCESSIBILITY_SERVICE|TapSwipeTool" \
     app/build/intermediates/merged_manifest/playDebug
   ```

   play 侧 **不得** 出现上述字符串。sideload 合并 Manifest **应** 含 `AccessibilityService` 与 `BIND_ACCESSIBILITY_SERVICE`。

**通过**

- `checkChannelLeak` 退出码 0。
- 两 APK 均已安装，可同时打开。
- play 不含 sideload 专用 Accessibility / `TapSwipeTool`；play classpath 不含 `:tool:accessibility`（由现有任务断言）。

**失败 / 受阻**

- 失败：`checkChannelLeak` 失败、安装时互相覆盖、play Manifest 泄漏无障碍。
- 受阻：无第二台设备要求；同机装不上 sideload 时写 adb 报错原文。

---

## Case 13 — 出境拦截 → 授权后可对话（UF-01）

建议在 **首次配置云端之前** 跑，或先关掉出站并保存。

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 先：**允许显式数据出站 = 关** 且已 **保存配置**；顶栏 `出境策略: 仅本地` |
| 权限 | 无 |
| 真实 API Key | **半段 A 也要填 Key**（证明有 Key 仍拦截）；半段 B 同一把 Key |

**步骤**

1. 设置：出站 **关**，**仍填写有效 Key**，点 **保存配置**。确认顶栏 **仅本地**。
2. 对话发送任意一句。
3. 再打开设置：出站 **开**，**保存配置**。顶栏变为 **已授权云端**。
4. 再发送一句（或点重试）。

**通过**

- 半段 A：气泡含 **`任务失败：云端调用已被拦截。请先在设置中授权数据出境。`**（`EGRESS_BLOCKED`）。开发者页无成功 LLM 完成。未保存就拨开关则行为仍属旧策略——必须强调已点保存。
- 半段 B：不再出现该拦截文案，能像 Case 01 一样出终答（或至少进入 THINKING/流式而非立刻 EGRESS_BLOCKED）。

**失败 / 受阻**

- 失败：仅本地仍打到云端；或已授权并保存后仍 EGRESS_BLOCKED。
- 受阻：无 Key。半段 A 若 Key 为空可能先出现 **`尚未配置 API 密钥。请在设置中填写密钥后再试。`**——请填入 Key 后重跑半段 A，以便证明「有 Key 仍拦截」。

---

## Case 14 — 杀进程后重开 + 幂等边界（UF-05）

覆盖 Case 08 的提示与 Case 09 的「继续不得重复副作用」。产品 **没有**「是否继续未完成任务」对话框：中断任务被标失败，用户用 **重试** 重新提交。

**前置**

| 项 | 值 |
|---|---|
| 渠道 | play debug |
| 出境 | 已授权云端并保存 |
| 权限 | 若用创建日程验证副作用：写入日历 |
| 真实 API Key | **要** |

**步骤**

1. 提交进行中任务（对话或创建日程均可）。创建日程时用 **新的唯一标题** `DougieE2E-14-…`，确认执行后、在 COMPLETED 前 force-stop（同 Case 09）。
2. 重开：应见 **`任务失败：任务已中断，请重新提交。`**
3. 日历：未点重试前，该标题仍为 **至多一条**（已执行成功则一条；尚未执行则零条）。
4. 点 **重试**：新 `taskId`。若重试再次创建日程并确认，允许出现 **第二条**（新幂等键）——在 findings 写明，这 **不是** 恢复路径重复副作用。
5. 开发者：重试前后 `taskId` 不同。

**通过**

- 重开提示为 `INTERRUPTED` 原文（带 `任务失败：` 前缀）。
- 恢复逻辑本身不续传 LLM、不自动再执行 L2 创建。
- 未点重试时日历无「凭空多一条」。

**失败 / 受阻**

- 失败：重开无提示却自动把旧任务跑完并重复写入；或同一 taskId 被再次拿去执行 Tool。
- 受阻：与 Case 08/09 相同的 OEM 杀后台时机问题。

---

## 文案速查（UserFacingErrors）

对照时允许前面有 `任务失败：`。

| 常量 | 界面原文 |
|---|---|
| EGRESS_BLOCKED | 云端调用已被拦截。请先在设置中授权数据出境。 |
| MISSING_API_KEY | 尚未配置 API 密钥。请在设置中填写密钥后再试。 |
| LLM_TIMEOUT | 模型响应超时，请稍后重试。 |
| TOOL_TIMEOUT | 工具执行超时，请稍后重试。 |
| INTERRUPTED | 任务已中断，请重新提交。 |
| PERMISSION_DENIED | 未授权，已为你跳过该操作 |
| CONFIRM_REJECTED | 该操作需你确认后才执行 |
| NETWORK_FAILED | 网络请求失败，请检查连接后重试。 |
| SCREEN_MATCH_FAILED | 未能匹配屏幕内容，已停止以免误操作。 |

设置：开关 **允许显式数据出站** → 按钮 **保存配置**。对话顶栏：**出境策略: 仅本地** / **出境策略: 已授权云端**。COMPLETED 记忆引用行：**来源：**。确认卡按钮：**确认** / **拒绝**。失败后按钮：**重试**。

---

## 工具显示名

| toolName | 对话卡片 |
|---|---|
| battery | 电池工具 |
| time | 时间工具 |
| calendar_query | 日历查询 |
| calendar_create | 创建日程 |
| clipboard_read / clipboard_write | 读取剪贴板 / 写入剪贴板 |
| location / screen_capture / screen_match | 无中文映射时显示原始 id |

---

## 本任务不做什么

- 不提交 git、不归档父任务、不在本文件填写假的 14/14 通过。
- 现场修得完的小缺陷可顺手修；修不完记缺口并另开子任务。
- 父任务最后一格 **仅当 14 条均为通过** 才勾；任一失败或受阻则保持打开。
