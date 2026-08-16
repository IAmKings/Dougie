# 产品需求文档 (PRD): Waku-Android 架构本地优先智能体 (Local-First Mobile Agent)

---

| 文档版本 | 创建日期 | 状态 | 作者 |
| :--- | :--- | :--- | :--- |
| V1.0.0 | 2026-08-13 | 终稿 / 拟定 | System Architect |

---

## 1. 项目概述 (Executive Summary)

### 1.1 背景与痛点
当前大部分 Mobile Agent 依赖云端 SaaS 框架（如 LangChain/LlamaIndex 服务端），存在以下突出问题：
1. **隐私泄露风险**：用户敏感数据（如聊天记录、日程、通讯录、位置）需要频繁上传云端。
2. **上下文感知弱**：桌面或云端 Agent 难以实时感知用户的移动端设备状态（传感器、通知、应用互操作）。
3. **框架过于臃肿**：传统 Agent 框架封装层级过深，难以在 Android 端进行低延迟、高灵活度的定制开发。

### 1.2 解决方案
借鉴 **Waku-Agent** 的设计理念——**Local-first（本地优先）** 与 **极简推理循环（KISS Loop）**，构建运行于 Android 客户端的原生 Agent 引擎。将 Agent 的四大柱石（Harness/Gateway、Loop Engine、Memory Architecture、Tools）完整映射并落地到 Android 原生技术栈。

### 1.3 核心目标
* **隐私绝对安全**：所有记忆文件（SQLite）、配置与系统偏好（SOUL）均保存在手机本地。
* **低延迟与高响应**：控制流与状态机在本地运行，支持云端/端侧混合 LLM 推理。
* **极简易扩展**：控制 Loop 控制在百行 Kotlin 代码以内，保持极致轻量。

---

## 2. 整体系统架构 (System Architecture)

系统由 4 个核心层级构成，完全对齐 Waku-Agent 的模块设计：

```
+-----------------------------------------------------------------------+
|                         1. Gateway / Harness Layer                    |
|   (App UI / Floating Window / Quick Settings Tile / Notification Bot) |
+-----------------------------------------------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                         2. Core Loop Engine                           |
|      (Kotlin Coroutines State Machine / ReAct Loop / Event Bus)       |
+-----------------------------------------------------------------------+
          |                         |                         |
          v                         v                         v
+------------------+      +------------------+      +------------------+
| 3. Memory Module |      | 4. Tools Module  |      |  5. LLM Adapter  |
| - Episodic (Room)|      | - Android Native |      | - Cloud API      |
| - Semantic (Vector)|    | - Accessibility  |      | - Local ONNX/    |
| - Procedural     |      | - System Intent  |      |   llama.cpp      |
+------------------+      +------------------+      +------------------+
```

---

## 3. 核心功能模块详细设计 (Detailed Module Design)

### 3.1 Loop Engine（核心推理循环）
* **设计原则**：摒弃复杂的图/树结构，采用极简的 `ReAct (Reason + Act)` 状态循环。
* **核心流程**：
  1. **Assemble Prompt**：拼接 System Prompt (SOUL)、当前环境 Context、Retrieval Memory 与 History。
  2. **LLM Inference**：发起流式/非流式推理。
  3. **Parse Tool Call**：若包含工具调用请求，提取 Tool 名称与参数 JSON。
  4. **Execute Tool**：在 Android 本地异步执行 Native Tool。
  5. **Feedback & Iteration**：将执行结果喂回 LLM，直至输出 Final Answer 或达到最大 Loop 限制（默认 MAX_LOOPS = 10）。
* **状态定义 (State Machine)**：
  * `Idle` $ightarrow$ `Thinking` $ightarrow$ `ToolExecuting` $ightarrow$ `MemoryIngesting` $ightarrow$ `Completed` / `Error`

### 3.2 Memory Module（三层本地记忆体系）
基于 Android 本地 **Room Database (SQLite)** 与 **端侧向量索引** 构建：

| 记忆类型 | 存储内容 | 实现方案 (Android) | 检索机制 |
| :--- | :--- | :--- | :--- |
| **Episodic (情境记忆)** | 用户与 Agent 的历史对话、事件日志 | Room DB (`conversations` 表) | 基于时间戳与 FTS5 全文检索 |
| **Semantic (语义记忆)** | 从对话中提取的实体、偏好、用户事实 | Room DB + `sqlite-vec` / ObjectBox Vector | 余弦相似度向量检索 (Vector Embedding) |
| **Procedural (程序/SOUL)** | Agent 的性格设定、SOP 技能库、执行规则 | 本地 JSON / Markdown 配置文件 | 启动时加载至内存并注入 System Prompt |

* **Memory Gate 机制**：
  * 每次 Loop 结束时触发后台协程，判断当前对话是否包含新增长期事实（Fact）。
  * 若包含，使用端侧 Embedding 模型生成向量，存入 Semantic Memory。

### 3.3 Tools Module（端侧工具能力）
充分利用 Android 系统的原生能力扩展 Agent 的行为空间：

1. **System Interaction Tool**：
   * 日历与提醒：创建/查询 Calendar 事件。
   * 通讯：发送 SMS、发起 Calls（需用户授权）。
   * 设备控制：开关 WiFi/蓝牙、获取当前 GPS 坐标与电量。
2. **UI Automation Tool (Accessibility Service)**：
   * 基于无障碍服务实现屏幕元素识别与自动点击/滑动，支持无 API 的第三方 App 交互。
3. **App Intent Tool**：
   * 通过 `Intent` 跳转指定 App 界面或传递数据。

### 3.4 Gateway / Harness Layer（宿主层）
* **UI 交互**：Jetpack Compose 构建的 Chat 界面与悬浮窗（Floating Widget）。
* **后台保活与断点续传**：利用 `WorkManager` 处理延时定时任务；使用 SQLite 保存状态机，确保被系统杀死后能从断点继续恢复 Loop。

---

## 4. 技术栈与选型 (Tech Stack)

| 模块 | 推荐技术/库 | 说明 |
| :--- | :--- | :--- |
| **开发语言** | Kotlin 2.0+ | 原生性能与高效异步协程 |
| **异步流控** | Kotlin Coroutines + Flow | `StateFlow` 驱动响应式 UI |
| **本地数据库** | Room DB + SQLite FTS5 | 存储结构化数据与全文检索 |
| **端侧向量引擎** | ObjectBox Vector Search / ONNX Runtime | 端侧文本 Embedding 计算与检索 |
| **网络请求** | Ktor Client / Retrofit | 与云端 LLM API 通信 |
| **依赖注入** | Hilt / Koin | 模块解耦与依赖管理 |

---

## 5. 跨端与安全性设计 (Security & Performance)

1. **沙盒隔离**：所有 SQLite 数据库文件均存在应用私有目录 (`/data/data/<package_name>/databases`)，禁止外部应用读取。
2. **API Key 加密**：云端 LLM API Key 使用 Android `Keystore System` 加密存储。
3. **敏感权限动态申请**：无障碍服务、定位、通讯录等敏感工具的调用必须经过前台显示授权。
4. **资源占用控制**：
   * 端侧 Embedding 向量计算放在 Low-priority Background Thread（低优先级后台线程）。
   * 限制单次 Context 长度，动态裁剪历史对话（Sliding Window）。

---

## 6. 开发里程碑与 Roadmap

```
Phase 1: 原型验证 (PoC)
├── 构建极简 ReAct Loop (Kotlin Coroutines)
├── 实现基本 Cloud LLM API Adapter
└── 实现基础 Room DB 历史记录存储

Phase 2: 本地记忆与工具库
├── 集成 FTS5 与端侧向量检索 (Memory Gate)
├── 实现基础 Android Native Tools (日历、定位、SMS)
└── 完成基于 StateFlow 的 Chat UI

Phase 3: 高级特性与自动化
├── 集成 AccessibilityService 无障碍自动化工具
├── 实现 WorkManager 断点续传与后台定时触发
└── 端侧小模型 (llama.cpp/ONNX) 混合推理适配
```
