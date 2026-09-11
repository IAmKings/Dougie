# Dougie

[![CI](https://github.com/IAmKings/Dougie/actions/workflows/ci.yml/badge.svg)](https://github.com/IAmKings/Dougie/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/IAmKings/Dougie?include_prereleases)](https://github.com/IAmKings/Dougie/releases)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

手机上的本地优先助手。默认把对话、记忆和工具副作用留在设备上；只有你在设置里打开出境并点 **保存** 后，才会请求你配置的云端模型。

A local-first Android agent. Chat, memory, and tool side effects stay on device until you enable cloud egress and save.

当前 **v0.1.0** 是公开预览：GitHub Release 标为 pre-release，APK 为 debug 签名，**尚未上架任何应用商店**。

<p align="center">
  <img src="screens/dougie_chat.jpg" width="220" alt="对话首页">
  <img src="screens/dougie_chat_response.jpg" width="220" alt="自我介绍">
  <img src="screens/dougie_chat_tool_card.jpg" width="220" alt="时间工具卡片">
</p>
<p align="center">
  <img src="screens/dougie_permission.jpg" width="220" alt="权限中心">
  <img src="screens/dougie_setting.jpg" width="220" alt="设置">
  <img src="screens/dougie_task.jpg" width="220" alt="任务历史">
</p>

## 安装

系统要求 **Android 8.0+**（`minSdk` 26）。从 [GitHub Releases](https://github.com/IAmKings/Dougie/releases) 下载两个渠道包之一（当前 [v0.1.0](https://github.com/IAmKings/Dougie/releases/tag/v0.1.0)）：

| 文件 | 包名 | 给谁 |
|------|------|------|
| `Dougie-*-play.apk` | `com.dougie.app` | 只要云端对话与设备工具，不要无障碍 / 端侧大模型 |
| `Dougie-*-sideload.apk` | `com.dougie.app.sideload` | 要本机 LiteRT 对话、悬浮球或无障碍手势 |

两包可同机安装。侧载安装需在系统设置里允许该来源。当前 debug 签名**不能**被以后的上传密钥签名覆盖，换正式签名时请先卸载再装。

## 它做什么

Dougie 跑在 Android 上，用一条可取消、可恢复的 Agent 循环调用设备能力。查时间与电量、读写剪贴板（读剪贴板须 App 在前台）、粗略定位、日历、离线语音与意图分类、截屏匹配，以及按你的确认打开应用或起草短信。

- **本地优先**：云端默认关闭。顶栏能看到当前是「仅本地」还是「已授权云端」。
- **权限先行**：日历、定位、麦克风、截屏等按任务申请，可在权限中心逐项关闭。
- **工具可见**：每次调用在对话里显示工具卡，任务页可回看完成状态。
- **记忆可控**：对话里可开关记忆门控；记住的事实在记忆页管理。

侧载包还可以下载 LiteRT 对话权重（约 328MB / 756MB / 1.5GB 三档），点 **使用** 后才切换引擎；并在知情同意后使用悬浮球与无障碍手势。Play 渠道包不含这些能力，也不内置对话权重。

## 数据出境

本仓库不运营 Dougie 云。默认不上传对话。

打开出境并保存后，请求会发往你填写的 OpenAI 兼容 Base URL：用户输入、组装后的上下文（含检索到的记忆）、工具结果，以及非截屏图片附件。截屏像素留在本机，云端只看到附件元数据。API 密钥用 AndroidX Security（`EncryptedSharedPreferences` + `MasterKey`）存在本机，不进 Logcat。

卸载应用不会删除你选中的外部模型目录；重装后需再授权一次该文件夹。

## 工具卡与分级

对话里的工具卡对应一次真实调用：标题带风险等级（如「时间工具 (L0)」），卡片内是执行过程，下面是给用户看的中文结果。未授权的能力会被跳过，不会静默重试。

| 等级 | 策略 | 例子 |
|------|------|------|
| **L0** | 无需系统权限，直接执行 | 时间、电量、离线朗读、意图分类、模板匹配（对已有截屏） |
| **L1** | 需要对应权限，通过后执行 | 读日历、定位、麦克风、截屏、读剪贴板（须前台） |
| **L2** | 权限通过后仍弹出确认卡 | 写日历、写剪贴板、打开应用；侧载隔离 JS |
| **L3** | 确认卡 + 额外系统能力 | 起草短信 / 打开拨号盘；侧载无障碍手势 |
| **L4** | 确认卡；仅侧载脚本特权 | 隔离 Python；JS 程序模式 |

L2 及以上拒绝或超时都不会执行。权限中心按同一套等级展示，可随时在系统设置里收回。本地小模型不会被教会 `js_eval` / `py_eval` / 无障碍手势。

## 两个渠道

| | Play 渠道包 | 侧载 |
|---|---|---|
| 包名 | `com.dougie.app` | `com.dougie.app.sideload` |
| 分发 | GitHub Releases，尚未上架商店 | GitHub Releases，自行安装 |
| 无障碍点击 | 无 | 可选，需系统授权 |
| 端侧对话模型 | 无 | 设置中下载并激活 |
| 隔离脚本 | 无 | JS；打开脚本特权后还有 Python |

CI 会跑 `checkChannelLeak`，避免 Play 包带上侧载能力或模型文件。

## 使用

1. 打开 **设置**：填写 OpenAI 兼容的 Base URL、模型与密钥。若要走云端，打开出境开关后必须点 **保存**。
2. 对话顶栏的盾牌进入 **权限中心**，只开当前需要的能力。
3. 离线语音 / 意图：在设置里用系统文件选择器指定一个目录，约定 `{目录}/models/{asr,tts,intent}/`（识别约 230MB，合成约 116MB，意图约 12MB）。校验通过后会同步到应用私有目录再加载，不申请全盘存储。
4. 侧载对话模型：在同一设置页下载所需档位，点 **使用** 后才会作为当前引擎；下载完成不会自动切换。体积较大的档位需要更多内存，请按机型选择。

端侧权重由官方目录给出的 HTTPS 地址下载并校验 SHA-256；权重许可以模型发布方为准，与本仓库 Apache-2.0 不一定相同。

## 构建

需要 **JDK 17**（Temurin 等发行版）、Android SDK；native 部分使用 NDK **27.2.12479018**（`minSdk` 26，`compileSdk` / `targetSdk` 35）。Kotlin 2.0.21。请把 JDK 17 配到 `JAVA_HOME` 后再调用 Gradle。

```bash
./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug :app:checkChannelLeak
./gradlew :app:installPlayDebug
# 或
./gradlew :app:installSideloadDebug
```

首次编 native 时会拉取 sherpa-onnx 的 Android 共享库到 `tool/system/build/`（不入库）。

本机 JVM 控制台（不进 APK、不接真实云端）可用于看 Loop 状态：

```bash
./gradlew :cli:run --args='--log-only'
```

## 发版

推送 `v主.次.补丁` 标签会构建 Play / 侧载 Release APK，并挂到 [GitHub Releases](https://github.com/IAmKings/Dougie/releases)。`versionCode` 为 `主*10000 + 次*100 + 补丁`。

未配置仓库 Actions secrets（`ANDROID_KEYSTORE_BASE64` 等）时，产物为 debug 签名并标为预发布。不要把 keystore 提交进 git。Play Console 上架不在此流程内。

## 仓库结构

| 模块 | 职责 |
|------|------|
| `:app` | 组装、渠道差异、入口 |
| `:core:*` | 纯 JVM：Loop、LLM、工具合同、记忆门控 |
| `:data:*` | 设置（含加密密钥）、任务与记忆落盘 |
| `:tool:system` | 设备端口（日历、剪贴板、截屏、语音、定位） |
| `:tool:chatllm` / `:tool:accessibility` | 仅侧载：端侧对话引擎、手势 |
| `:tool:js` / `:tool:py` | 仅侧载：隔离 JS / Python |
| `:feature:*` | 对话、设置、记忆、任务、权限、调试页 |
| `:cli` | 开发用 JVM 控制台 |

界面是 Jetpack Compose。Agent 运行时不得依赖 `android.*`，以便 `:cli` 复用。

产品范围与验收基线见 [`PRD.md`](PRD.md)。实现约定见 [`.trellis/spec/`](.trellis/spec/)。

不要提交密钥、`local.properties`、对话权重（`*.litertlm`）或 keystore。

## 许可证

源码按 [Apache License 2.0](LICENSE) 发布。运行时下载的 ASR / TTS / 意图 / 对话权重遵循各自上游许可，不因放进本应用而变成 Apache-2.0。
