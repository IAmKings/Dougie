# Journal - kuluoluo (Part 1)

> AI development session journal
> Started: 2026-08-13

---



## Session 1: Phase 0–1a 落地

**Date**: 2026-08-17
**Task**: Phase 0–1a 落地
**Branch**: `main`

### Summary

提交 Android 骨架、Chat Fake Loop、Egress 默认拦截与真实电量 Tool；Phase 0/1a 验收通过并归档。

### Git Commits

| Hash | Message |
|------|---------|
| `1aebe5d` | (see git log) |

### Status

[OK] **Completed**


## Session 2: Phase 1b 流式双 Tool

**Date**: 2026-08-17
**Task**: Phase 1b 流式双 Tool
**Branch**: `main`

### Summary

提交 SSE 流式、时间 Tool、Sanitizer 与 Chat streamingText；归档 1b 后进入 Phase 2 Memory。

### Git Commits

| Hash | Message |
|------|---------|
| `f21cddf` | (see git log) |

### Status

[OK] **Completed**


## Session 3: Phase 2 本地记忆

**Date**: 2026-08-17
**Task**: Phase 2 本地记忆
**Branch**: `main`

### Summary

提交 FTS 记忆、Gate 与 Memory UI；归档后进入 Phase 3a 日历/剪贴板/权限确认。

### Git Commits

| Hash | Message |
|------|---------|
| `00585d9` | (see git log) |

### Status

[OK] **Completed**


## Session 4: Phase 3a 日历与确认卡

**Date**: 2026-08-17
**Task**: Phase 3a 日历与确认卡
**Branch**: `main`

### Summary

提交 Policy、L2 确认卡、日历与前台剪贴板、权限中心；进入 Phase 3b 定位与屏幕感知。

### Git Commits

| Hash | Message |
|------|---------|
| `eb0d72c` | (see git log) |

### Status

[OK] **Completed**


## Session 5: Phase 3b 定位与屏幕感知

**Date**: 2026-08-17
**Task**: Phase 3b 定位与屏幕感知
**Branch**: `main`

### Summary

提交粗定位、截屏元数据与本地模板匹配；进入 Phase 4 任务恢复与幂等落盘。

### Git Commits

| Hash | Message |
|------|---------|
| `55b75d5` | (see git log) |

### Status

[OK] **Completed**


## Session 6: Phase 4 任务恢复

**Date**: 2026-08-17
**Task**: Phase 4 任务恢复
**Branch**: `main`

### Summary

提交任务落盘、中断恢复、日历幂等、History 与重试；下一步补 MVP App Intent。

### Git Commits

| Hash | Message |
|------|---------|
| `88965cf` | (see git log) |

### Status

[OK] **Completed**


## Session 7: MVP App Intent

**Date**: 2026-08-17
**Task**: MVP App Intent
**Branch**: `main`

### Summary

提交可确认的 App Intent；下一步 Play/Sideload 双渠道 flavors。

### Git Commits

| Hash | Message |
|------|---------|
| `ad681bf` | (see git log) |

### Status

[OK] **Completed**


## Session 8: Play Sideload 双渠道

**Date**: 2026-08-17
**Task**: Play Sideload 双渠道
**Branch**: `main`

### Summary

提交 Play/Sideload flavors 与泄漏检查；下一步侧载真实 TapSwipe。

### Git Commits

| Hash | Message |
|------|---------|
| `2a54715` | (see git log) |

### Status

[OK] **Completed**


## Session 9: 侧载真实点击滑动

**Date**: 2026-08-17
**Task**: 侧载真实点击滑动
**Branch**: `main`

### Summary

提交 TapSwipe dispatchGesture；下一步离线 ASR 合同与权限门，不内置 230MB 模型。

### Git Commits

| Hash | Message |
|------|---------|
| `331595c` | (see git log) |

### Status

[OK] **Completed**


## Session 10: 语音输入合同

**Date**: 2026-08-17
**Task**: 语音输入合同
**Branch**: `main`

### Summary

提交 speech_input 权限与模型门；下一步接入 sherpa 离线引擎，仍不提交 230MB 模型。

### Git Commits

| Hash | Message |
|------|---------|
| `fd128b0` | (see git log) |

### Status

[OK] **Completed**


## Session 11: 前台录音与引擎缝

**Date**: 2026-08-17
**Task**: 前台录音与引擎缝
**Branch**: `main`

### Summary

提交 SpeechSession/AudioRecord；下一步接入 sherpa-onnx JNI 转写，仍不入库模型。

### Git Commits

| Hash | Message |
|------|---------|
| `bc4ba17` | (see git log) |

### Status

[OK] **Completed**


## Session 12: sherpa 本地转写

**Date**: 2026-08-17
**Task**: sherpa 本地转写
**Branch**: `main`

### Summary

提交 Paraformer JNI 转写门；下一步 speech_output TTS 合同与系统短播报降级。

### Git Commits

| Hash | Message |
|------|---------|
| `f242924` | (see git log) |

### Status

[OK] **Completed**


## Session 13: speech_output 系统 TTS 降级

**Date**: 2026-08-17
**Task**: speech_output 系统 TTS 降级
**Branch**: `main`

### Summary

提交 speech_output：离线未就绪时系统短播报，拒绝联网音色。下一步接入 sherpa VITS 离线合成，仍不入库 116MB 模型。

### Git Commits

| Hash | Message |
|------|---------|
| `28f7f23` | (see git log) |

### Status

[OK] **Completed**


## Session 14: sherpa VITS 离线合成

**Date**: 2026-08-17
**Task**: sherpa VITS 离线合成
**Branch**: `main`

### Summary

提交 VITS 离线合成与 AudioTrack 播放；缺模型时仍降级系统短播报。下一步接入 IntentClassifier 合同，不入库 GGUF。

### Git Commits

| Hash | Message |
|------|---------|
| `e0f59b0` | (see git log) |

### Status

[OK] **Completed**


## Session 15: intent_classifier 合同

**Date**: 2026-08-17
**Task**: intent_classifier 合同
**Branch**: `main`

### Summary

提交 intent_classifier 模型/引擎/低置信度门。下一步接入可注入的 llama 完成缝，仍不入库 GGUF 与 NDK 源码。

### Git Commits

| Hash | Message |
|------|---------|
| `380953a` | (see git log) |

### Status

[OK] **Completed**


## Session 16: llama 意图完成缝

**Date**: 2026-08-17
**Task**: llama 意图完成缝
**Branch**: `main`

### Summary

提交 llama complete 缝与 JSON 解析。下一步可选编译 NDK JNI，仍不入库 GGUF 与 llama.cpp 源码。

### Git Commits

| Hash | Message |
|------|---------|
| `1ca77d3` | (see git log) |

### Status

[OK] **Completed**


## Session 17: llama NDK 可选编译

**Date**: 2026-08-17
**Task**: llama NDK 可选编译
**Branch**: `main`

### Summary

提交可选 llama.cpp JNI。下一步 Play 按需下载 HTTPS+哈希，不入库模型、不做评测集。

### Git Commits

| Hash | Message |
|------|---------|
| `ae59936` | (see git log) |

### Status

[OK] **Completed**


## Session 18: Phase 5j 模型按需安装器

**Date**: 2026-08-17
**Task**: Phase 5j 模型按需安装器
**Branch**: `main`

### Summary

HTTPS+SHA-256 ModelInstaller：须用户确认、非 Agent Tool；哈希失败删 .part。未做下载 UI。

### Git Commits

| Hash | Message |
|------|---------|
| `a4ddb9e` | (see git log) |

### Status

[OK] **Completed**


## Session 19: Phase 5k 设置页模型下载 UI

**Date**: 2026-08-17
**Task**: Phase 5k 设置页模型下载 UI
**Branch**: `main`

### Summary

设置页 ASR/TTS/意图三行确认体积后下载；可取消；URL 由 local.properties 注入。

### Git Commits

| Hash | Message |
|------|---------|
| `b538ed4` | (see git log) |

### Status

[OK] **Completed**


## Session 20: Phase 5l sideload 内置语音模型

**Date**: 2026-08-17
**Task**: Phase 5l sideload 内置语音模型
**Branch**: `main`

### Summary

sideload 从 assets 植入 ASR/TTS；play APK 不得含 onnx/意图 GGUF。权重不入库。

### Git Commits

| Hash | Message |
|------|---------|
| `b3b9d83` | (see git log) |

### Status

[OK] **Completed**


## Session 21: Phase 5m 评测夹具

**Date**: 2026-08-17
**Task**: Phase 5m 评测夹具
**Branch**: `main`

### Summary

JVM CER 与意图准确率小金标；全量 wav gitignore。Phase 5 地图切片收齐。

### Git Commits

| Hash | Message |
|------|---------|
| `4df8dde` | (see git log) |

### Status

[OK] **Completed**


## Session 22: Debug 开发者页面

**Date**: 2026-08-17
**Task**: Debug 开发者页面
**Branch**: `main`

### Summary

设置页开发者入口：当前任务字段 + 审计行，无敏感内容。父任务 UI 清单 Debug 已交付。

### Git Commits

| Hash | Message |
|------|---------|
| `5e012b1` | (see git log) |

### Status

[OK] **Completed**


## Session 23: MVP 集成审查归档

**Date**: 2026-08-18
**Task**: MVP 集成审查归档
**Branch**: `main`

### Summary

§16.1 十四 Case 代码覆盖结论已写入 findings；补厂商预设、max_tokens、记忆来源、ScreenMatch catalog、意图 Q4/Q8 二选一后提交并归档审查任务。未做真机 14/14 签字。

### Git Commits

| Hash | Message |
|------|---------|
| `54e10c2` | (see git log) |

### Status

[OK] **Completed**


## Session 24: Case11 截屏崩溃修复

**Date**: 2026-08-18
**Task**: Case11 截屏崩溃修复
**Branch**: `main`

### Summary

主线程停 MediaProjection FGS，HandlerThread 上 stop 后 quitSafely；宽边 720。PJZ110 捕获/拒绝/后台门闩通过。问句不进 MemoryGate，记忆页进入 refresh。未归档签字父任务（07/09/14 受阻）。

### Git Commits

| Hash | Message |
|------|---------|
| `eedf1e7` | (see git log) |

### Status

[OK] **Completed**


## Session 25: OpenCode Go DeepSeek Flash 预设

**Date**: 2026-08-19
**Task**: OpenCode Go DeepSeek Flash 预设
**Branch**: `main`

### Summary

可选预设 opencode-go + 官方 DeepSeek 默认 Flash；PJZ110 人工验收。下周独立任务：本地模型 SAF 导入、官方 SHA-256 校验、设置页测试按钮验证模型可加载。

### Git Commits

| Hash | Message |
|------|---------|
| `4266207` | (see git log) |

### Status

[OK] **Completed**


## Session 26: 本地模型目录与意图 ONNX

**Date**: 2026-08-24
**Task**: 本地模型目录与意图 ONNX
**Branch**: `master`

### Summary

外部模型目录 + 单一 ONNX 意图分类；GitHub HTTPS 下载；sherpa shared ORT 修复真机推理。真机测试已通过。

### Git Commits

| Hash | Message |
|------|---------|
| `40f79fc` | (see git log) |
| `3d0e7b9` | (see git log) |
| `d4276ea` | (see git log) |

### Status

[OK] **Completed**


## Session 27: 归档 MVP 签字与 Bootstrap

**Date**: 2026-08-24
**Task**: 归档 MVP 签字与 Bootstrap
**Branch**: `master`

### Summary

§16.1 十四 Case 签名通过；补全并收紧 Trellis spec；归档签字、MVP 父任务与 Bootstrap。

### Git Commits

| Hash | Message |
|------|---------|
| `5c730e7` | (see git log) |
| `b574f17` | (see git log) |
| `9513043` | (see git log) |

### Status

[OK] **Completed**


## Session 28: JVM :cli Fake Loop 控制台

**Date**: 2026-08-25
**Task**: JVM :cli Fake Loop 控制台
**Branch**: `master`

### Summary

新增 Gradle :cli：FakeLlm + FakeBattery 三次循环，mosaic 0.14.0 / --log-only，不进 APK；spec 记录目录与测试命令。Check 通过后提交并归档 08-24-cli-agent-console。

### Git Commits

| Hash | Message |
|------|---------|
| `51665e4` | (see git log) |

### Status

[OK] **Completed**


## Session 29: 快捷设置 Tile 打开对话

**Date**: 2026-08-25
**Task**: 快捷设置 Tile 打开对话
**Branch**: `master`

### Summary

Quick Settings Tile 打开 Chat，不 submit；checkChannelLeak 校验 Tile 与禁止 NotificationListener。父任务 08-25-phase-5-system-surfaces 保留。

### Git Commits

| Hash | Message |
|------|---------|
| `9240df9` | (see git log) |

### Status

[OK] **Completed**


## Session 30: 任务进度通知

**Date**: 2026-08-25
**Task**: 任务进度通知
**Branch**: `master`

### Summary

状态机通知渠道、点开 Chat、无 Listener；权限中心 API 33+ 通知行。归档 08-25-notification-agent，父任务保留。

### Git Commits

| Hash | Message |
|------|---------|
| `84eb666` | (see git log) |

### Status

[OK] **Completed**


## Session 31: Play 气泡与 sideload 悬浮球

**Date**: 2026-08-25
**Task**: Play 气泡与 sideload 悬浮球
**Branch**: `master`

### Summary

Play 任务通知气泡 + sideload overlay 打开 Chat；气泡 PendingIntent 改为 mutable，避免发任务时主线程崩溃。

### Git Commits

| Hash | Message |
|------|---------|
| `8feb45d` | (see git log) |

### Status

[OK] **Completed**
