# Dougie Android MVP

## Goal

按仓库根目录 `PRD.md`（V2.1.10 执行验收基线）交付可稳定运行的 Android Local-first Agent MVP，界面以 `design/` Stitch 资源为视觉参考。

用户价值：在设备上完成对话、Tool 调用、权限与记忆治理，且任务可恢复、可取消、可观察。

## Background

- 仓库已有 Gradle Android 工程：Phase 0 骨架 + Chat Fake Loop；Phase 1a 为云端 LLM + 真实电量 Tool。
- 根目录 `00-bootstrap-guidelines` 仍在填 spec，不作为本产品实现目标。
- MVP 总工期 8–12 周；本父任务只拥有需求全集与子任务地图，不直接写代码。

## Source requirements（父任务权威范围）

来源：`PRD.md` §3.1 / §15。子任务必须能独立验收，不得把整份 MVP 塞进一个子任务。

| 能力族 | 必须证明 |
|---|---|
| Agent Core | ReAct Loop、StateFlow、超时/取消/重试、Task 持久化与恢复 |
| LLM | OpenAI-compatible、默认 Cloud Provider、streaming / non-streaming、Tool Calling、Token budget |
| Memory | Conversation + FTS5 语义接口（关键词，非向量） |
| Tools（MVP） | 时间、电量、日历查询/创建、定位、App Intent、剪贴板（仅前台）、屏幕感知（Phase 3） |
| UI | Chat、Task 状态、Tool 状态、Permission Center、Memory Viewer、Provider Settings、Task History、Error/Retry、Debug |

## Task map

| 子任务 | 对应 PRD | 独立验收 |
|---|---|---|
| `08-17-phase-0-skeleton-chat` | Phase 0 + Chat 主界面 | Fake LLM + Fake Tool 稳定跑通 3 次 Loop；Chat 展示完整状态链（实现已通过） |
| `08-17-phase-1a-cloud-battery` | §15 Phase 1a | 真实 OpenAI-compatible 对话 + 真实电量 Tool + Egress 默认拦截（已归档） |
| `08-17-phase-1b-streaming-tools` | §15 Phase 1b | Streaming + ≥2 Tool（电量+时间）+ ToolCallSanitizer（已归档） |
| `08-17-phase-2-local-memory` | §15 Phase 2 | FTS5 关键词记忆 + Gate + Memory UI，能找回至少一个事实（已归档） |
| `08-17-phase-3a-tools-policy` | §15 Phase 3 切片 | 日历查询/创建、剪贴板、Policy+确认卡、权限中心（已归档） |
| `08-17-phase-3b-location-screen` | §15 Phase 3 余下 | Location + ScreenCapture/Match（不含 Accessibility 点击）（已归档） |
| `08-17-phase-4-task-recovery` | §15 Phase 4 | 任务落盘恢复、创建型幂等持久化、History、有限重试（已归档） |
| `08-17-mvp-app-intent` | §3.1 Tools #6 | 安全打开 URI/应用的 App Intent（L2 确认）（已归档） |
| `08-17-play-sideload-flavors` | §17.4 | Play / Sideload 构建期双渠道，play 不含 Accessibility（已归档） |
| `08-17-phase-5a-tap-swipe` | §15 Phase 5 / §10.2 | 侧载真实 tap/swipe；Play 仍零 Accessibility（已归档） |
| `08-17-phase-5b-speech-input` | §15 Phase 5 / §6.8 | `speech_input` 合同与权限门；不内置 230MB 模型（已归档） |
| `08-17-phase-5c-speech-engine` | §15 Phase 5 / §6.8 | 前台 PCM 采集 + 可注入本地引擎缝；不入库 ONNX/JNI（已归档） |
| `08-17-phase-5d-sherpa-jni` | §15 Phase 5 / §6.8 | sherpa-onnx JNI 可加载时本地 Paraformer 转写（已归档） |
| `08-17-phase-5e-speech-output` | §15 Phase 5 / §6.9 | `speech_output` 合同；系统短播报降级，禁联网合成（已归档） |
| `08-17-phase-5f-vits-tts` | §15 Phase 5 / §6.9 | sherpa VITS 离线合成；不入库 116MB 模型（已归档） |
| `08-17-phase-5g-intent-classifier` | §15 Phase 5 / §6.10 | `intent_classifier` 合同与模型门；不入库 GGUF（已归档） |
| `08-17-phase-5h-llama-engine` | §15 Phase 5 / §6.10 | llama 完成缝 + JSON 解析；不入库 GGUF/NDK（已归档） |
| `08-17-phase-5i-llama-ndk` | §15 Phase 5 / §6.10 | 可选 CMake 编译 `nativeComplete`；不入库 llama.cpp（已归档） |
| `08-17-phase-5j-model-download` | §15 Phase 5 / §6.8–6.10 | HTTPS+哈希按需安装到 filesDir；非 Agent Tool |
| （后续）Phase 5 余下 | §15 Phase 5 | 下载 UI / sideload 内置 / 评测集 |

后续子任务在本父任务下用 `task.py create --parent 08-17-dougie-android-mvp` 创建。

## Cross-child acceptance

- [ ] Phase 0 生死线通过后才允许启动 Phase 1 子任务。
- [ ] `:core:*` 保持 JVM 纯净（零 `android.*`），`:feature:*` 不直连系统 API。
- [x] Play / Sideload 双渠道差异不在 Phase 0 实现；构建 flavors 归后续子任务。
- [ ] 父任务本身不合并代码；最终集成审查在全部阻塞子任务归档后进行。

## Out of scope（父级，全 MVP 非目标）

`PRD.md` §3.2：无限制后台 Agent、第三方 App 自动化（Play）、短信/电话、向量检索、桌面端、端侧 LLM 产品化。`:cli` mosaic 为 Phase 0 加分项，不阻塞第一子任务。

## Constraints

- Android 10+，首期 Android 13–16。
- Local-first + Explicit Data Egress。
- 幂等键从 Phase 1 起实现；Phase 0 Fake Tool 仍使用 `taskId + toolCallId` 形状，避免后期重写。
