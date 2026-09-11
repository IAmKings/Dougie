# 侧载本地 Chat GPU 引擎启动预热

## Goal

侧载在「下一句会走本地 Chat」时，进程启动后后台 initialize 当前激活档（GPU 失败则 CPU）。用户打完字再发，只付剩余加载 + 首 token。云端已配置则不预热。Play 无对话引擎。

## Background

`ChatLlmProvider.get` 在 `onCreate` 只建单例。`Engine.initialize()` 发生在第一次 `stream()` → `ensureEngine()`，锁内 `ChatLlmEngines.tryCreate` GPU 再 CPU。PJZ110：0.6B ~4s / 1B ~7s / 2B ~15s。热引擎进程内常驻。`SelectingLlmProvider` 云端已配置则不走本地。`localChatReady` 只表示激活档文件存在。Loop 超时 60s。用户要求一启动就异步加载。

## Requirements

- R1 侧载且下一句走本地（未开云端或无 key，且激活档可定位）：`Application.onCreate` 接好 provider 后，在 `Dispatchers.Default` 对激活档调用与发送相同的 GPU→CPU `ensureEngine()`。不阻塞 Main。
- R2 `stream()` 与预热共用同一引擎槽：已热只建 Conversation；仍在 initialize 则等剩余时间；禁止并行第二次 `initialize()`。
- R3 切换激活档并释放旧 Engine 后，若仍满足 R1，对新路径再预热。云端从关到开且无进行中生成时释放引擎；从开到关再预热。
- R4 Play 不预热、无 LiteRT。不改 JSON/身份 prompt/catalog。不 Logcat 路径或 prompt。预热失败不改 `localChatReady`，不崩进程；发送仍可再试 GPU→CPU。
- R5 进行中生成不得换引擎（现有 `inFlight`）。不预热未激活的兄弟档。

## Acceptance Criteria

- [x] AC1 冷启动、未开云端、激活档已装：打完字再发，等待明显短于该档完整 GPU load（2B 不应再接近整段 ~15s）。
- [x] AC2 已热后再发：不再付 initialize。
- [x] AC3 切换 SKU 后第一句走新档。
- [x] AC4 云端已配置（allowCloud + 非空 key）时启动不预热；关掉云端后才预热。
- [x] AC5 Play `checkChannelLeak` 过；无对话权重进 APK。
- [x] AC6 谓词 JVM 可测：`shouldWarmLocalEngine(cloudConfigured, localReady)` 与 `SelectingLlmProvider.isLocal` 同义。

## Out of scope

- 三档同时加载、NPU、改 60s 超时、Chat「模型准备中」文案。
- 改身份/工具 prompt、Play 对话下载、低内存跳过 2B。
