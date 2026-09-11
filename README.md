# Dougie

手机上的本地优先助手。默认把对话、记忆和工具副作用留在设备上；只有你在设置里明确打开出境并保存后，才会请求云端模型。

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

## 它做什么

Dougie 跑在 Android 上，用一条可取消、可恢复的 Agent 循环调用设备能力。查时间与电量、读写剪贴板、粗略定位、日历、离线语音、截屏匹配，以及按你的确认打开应用或起草短信。

- **本地优先**：云端默认关闭。顶栏能看到当前是「仅本地」还是「已授权云端」。
- **权限先行**：日历、定位、麦克风、截屏等按任务申请，可在权限中心逐项关闭。
- **工具可见**：每次调用在对话里显示工具卡，任务页可回看完成状态。
- **记忆可控**：对话里可开关记忆门控；记住的事实在记忆页管理。

侧载包还可以在本机跑 LiteRT 对话模型（需自行下载权重），并在知情同意后使用悬浮球与无障碍手势。Play 包不含无障碍点击，也不内置对话权重。

## 工具卡与分级

对话里的工具卡对应一次真实调用：标题带风险等级（如「时间工具 (L0)」），卡片内是执行过程，下面是给用户看的中文结果。未授权的能力会被跳过，不会静默重试。

| 等级 | 策略 | 例子 |
|------|------|------|
| **L0** | 无需系统权限，直接执行 | 时间、电量、模板匹配 |
| **L1** | 需要对应权限，通过后执行 | 读日历、定位、麦克风、截屏 |
| **L2** | 权限通过后仍弹出确认卡 | 写日历、写剪贴板、打开应用 |
| **L3** | 确认卡 + 额外系统能力 | 起草短信 / 打开拨号盘；侧载无障碍手势 |
| **L4** | 确认卡；仅侧载脚本特权 | 隔离 Python |

L2 及以上拒绝或超时都不会执行。权限中心按同一套等级展示，可随时在系统设置里收回。

## 两个渠道

| | Play | 侧载 |
|---|---|---|
| 包名 | `com.dougie.app` | `com.dougie.app.sideload` |
| 上架 | 应用市场路径 | 自行安装 |
| 无障碍点击 | 无 | 可选，需系统授权 |
| 端侧对话模型 | 无 | 设置中下载并激活 |

两包可同机安装。CI 会跑 `checkChannelLeak`，避免 Play 包带上侧载能力或模型文件。

## 使用

1. 打开 **设置**：填写 OpenAI 兼容的 Base URL、模型与密钥。若要走云端，打开出境开关后必须点 **保存**。
2. 对话顶栏的盾牌进入 **权限中心**，只开当前需要的能力。
3. 离线语音 / 意图等模型：在设置里用系统文件选择器指定一个目录，约定 `{目录}/models/{asr,tts,intent}/`。校验通过后会同步到应用私有目录再加载，不申请全盘存储。
4. 侧载对话模型：在同一设置页下载所需档位，点 **使用** 后才会作为当前引擎；下载完成不会自动切换。

卸载应用不会删除你选中的外部模型目录；重装后需再授权一次该文件夹。

## 构建

需要 **JDK 17**、Android SDK；native 部分使用 NDK **27.2.12479018**（`minSdk` 26，`compileSdk` / `targetSdk` 35）。Kotlin 2.0.21。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

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

可选在仓库 Actions secrets 配置上传密钥（`ANDROID_KEYSTORE_BASE64` 等），否则产物为 debug 签名。不要把 keystore 提交进 git。Play Console 上架不在此流程内。

## 仓库结构

| 模块 | 职责 |
|------|------|
| `:app` | 组装、渠道差异、入口 |
| `:core:*` | 纯 JVM：Loop、LLM、工具合同、记忆门控 |
| `:data:*` | 设置（含加密密钥）、任务与记忆落盘 |
| `:tool:system` | 设备端口（日历、剪贴板、截屏、语音、定位） |
| `:tool:chatllm` / `:tool:accessibility` | 仅侧载：端侧对话引擎、手势 |
| `:feature:*` | 对话、设置、记忆、任务、权限、调试页 |
| `:cli` | 开发用 JVM 控制台 |

界面是 Jetpack Compose。Agent 运行时不得依赖 `android.*`，以便 `:cli` 复用。

产品范围与验收基线见 [`PRD.md`](PRD.md)。实现约定见 [`.trellis/spec/`](.trellis/spec/)。

不要提交密钥、`local.properties`、对话权重（`*.litertlm`）或 keystore。
