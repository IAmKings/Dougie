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
