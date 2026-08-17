# Phase 5k：离线模型下载 UI

## Goal

用户在设置页看到 **语音识别 / 语音合成 / 意图理解** 三行，确认大约流量与占用后，用现有 `ModelInstaller` 装到 `filesDir`。下载中能看进度、能取消。成功后对应 `*ModelLayout.isPresent` 为真。

## Background

- 根 `PRD.md` §6.8–6.10、§12：play 按需下载须进度、可取消、存储/流量提示。
- 5j 已有 `ModelInstaller.install(pack, destRoot, userConfirmed, onProgress)`；设置页目前只有云端 Provider。
- 三行共用同一安装器（已拍板）。目录由应用持有，不经 Loop / LLM。
- 仓库没有已托管生产 URL；本片不阻塞于真实 CDN 上线。

## Requirements

- 设置页「离线模型」三行：ASR（`models/asr`）、TTS（`models/tts`）、意图（`models/intent`）。
- 确认文案含大约体积：ASR ~230MB、TTS ~116MB、意图 ~470MB；未确认不调用 `install`，或 `userConfirmed=false`。
- 已安装按 layout 判定；下载中显示进度；取消后 `.part` 不得被当成已安装。
- HTTPS URL 与 SHA-256 写在应用 catalog（可由 `local.properties` / Gradle 注入）。URL 未配置则该行不可下载，并中文说明。
- 文案产品名 Dougie；失败用已有 `MODEL_*` 用户可见错误。
- 不入库 `.onnx` / `.gguf`。

## Out of scope

- sideload APK 内置模型
- CER / 意图评测集
- 远程动态目录或用户粘贴任意 URL
- 聊天页内嵌下载、后台无确认下载

## Acceptance Criteria

- [x] 三行均可独立确认下载；未确认零网络。
- [x] 取消后对应 layout 仍为未安装，无残留 `.part` 冒充成功。
- [x] URL 未配置时不能开始下载。
- [x] `./gradlew :core:tool:test :feature:settings:testDebugUnitTest :app:checkChannelLeak` 通过（JDK 17）。
