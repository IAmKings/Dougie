# Dougie

Android 上的本地优先 Agent：对话走 ReAct Loop，Tool 受权限与确认卡约束，云端 LLM 默认拦截，须在设置里打开「允许显式数据出站」并保存后才会请求第三方接口。

产品需求见根目录 [`PRD.md`](PRD.md)（当前执行基线 **V2.1.11**）。界面为 Jetpack Compose；Agent 运行时在 `:core:*`（纯 JVM，禁止 `android.*`）。

## 进度（2026-08-24）

工程切片与 Trellis 父任务 **Dougie Android MVP** 已归档。真机 §16.1 十四 Case 已签字（机型一加 PJZ110 / Android 16；Case 07 / 09 / 14 为签名通过，现场未诱导出 Tool 15s 超时、也未在创建日程飞行中杀掉进程）。

意图主路径是 **ONNX 分类**（三件套 `model.onnx` + `tokenizer.json` + `labels.txt`），复用 sherpa 的 **一份** `libonnxruntime.so`。不再使用 Qwen3 GGUF / llama.cpp。

未纳入本仓库交付：向量检索、桌面端、Play 渠道点击第三方 App、端侧完整聊天 LLM。

## 渠道

| Flavor | applicationId | 说明 |
|--------|---------------|------|
| **play**（默认） | `com.dougie.app` | Play 合规路径。不含 Accessibility / `TapSwipeTool`。APK 内不得带 `*.onnx`。 |
| **sideload** | `com.dougie.app.sideload` | 可含无障碍点击滑动；可选内置 ASR/TTS 资源。与 play 可同机安装。 |

构建时 `checkChannelLeak` 会检查 play Debug APK 是否泄漏侧载能力或模型文件。

## 模块

```
:app                    组装、渠道、SAF 模型目录、探针
:core:model             错误文案、厂商预设等共享模型
:core:llm               OpenAI 兼容 Provider
:core:runtime           LoopEngine、任务、出境网关、策略、审计
:core:tool              Tool 合同、记忆无关的工具、模型安装/目录、ONNX 意图引擎（JVM）
:core:memory            MemoryGate / Store 接口
:data:preferences       设置与密钥
:data:memory            Room FTS 记忆
:data:tasks             任务落盘
:tool:system            电量、日历、定位、剪贴板、截屏、sherpa JNI、意图 ORT JNI
:tool:accessibility     仅 sideload：tap/swipe
:feature:chat           对话
:feature:settings       出境、密钥、离线模型
:feature:memory         记忆页
:feature:history        任务历史
:feature:permissions    权限中心
:feature:debug          开发者页（无 Prompt / 密钥 / 工具参数）
:cli                    本机 JVM 终端（Fake Loop + mosaic；不进 APK）
```

底栏：**对话** / **任务** / **记忆** / **设置**。开发者入口在设置页。

## 环境

- JDK **17**（本机常用 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`）
- Android SDK；NDK **27.2.12479018**（native：sherpa + `dougie_intent`）
- `minSdk` 26，`compileSdk` / `targetSdk` 35
- Gradle 通过 Wrapper；Kotlin 2.0.21，AGP 8.7.3

首次编 native 时 Gradle 会拉取 sherpa-onnx **v1.13.4 shared** Android 包到 `tool/system/build/`（不入库），得到 `libsherpa-onnx-jni.so` 与 `libonnxruntime.so`。

## 构建与安装

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug :app:checkChannelLeak

./gradlew :app:installPlayDebug
# 或
./gradlew :app:installSideloadDebug
```

常用测试：

```bash
./gradlew :core:tool:test :core:runtime:test :feature:settings:test
```

若 `extractDebugAnnotations` 失败，可加 `-x extractDebugAnnotations`。

## `:cli`（本机 Fake Loop 控制台）

JVM 模块，不依赖 Android，也不被 `:app` 引用。用 `FakeLlmProvider` + `FakeBatteryTool` 跑三次电量 Tool，再给出终答。有 TTY 时 mosaic 展示当前 `TaskStatus` / `loopCount` / 最近 tool 名与状态 / `finalAnswer`；无 TTY 或 mosaic 失败时用 stdout。`--log-only` 跳过 mosaic。输出不含 Prompt、密钥、工具参数、`resultJson`。

Mosaic 锁定 **0.14.0**（Kotlin 2.0.20 元数据，匹配本仓库 Kotlin 2.0.21）。**0.18.0** 以 Kotlin 2.2 编译，K2 2.0.21 无法读取其 metadata。0.14 没有 `NonInteractivePolicy`；非 TTY / mosaic 异常时降级 `--log-only` 同样字段。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :cli:test :cli:run --args='--log-only'
./gradlew :app:assemblePlayDebug
```

Gradle 有时需要 `-x extractDebugAnnotations`。Windows cmd 请用 `--log-only`。不接真实 Cloud LLM。

## 使用要点

1. **设置**：填 OpenAI 兼容 Base URL / 模型 / API 密钥；打开出站后必须点 **保存配置**。顶栏应显示 `出境策略: 仅本地` 或 `已授权云端`。
2. **权限中心**（对话顶栏盾牌）：日历、定位、截屏等按需授权。
3. **离线模型**：在设置里选一个 **模型目录**（SAF，可持久授权）。约定 `{目录}/models/{asr,tts,intent}/`。哈希与官方 catalog 一致则扫描安装、不走 HTTP；缺文件且 catalog 已配置 HTTPS 则可下载。引擎实际读 `filesDir` 里同步后的副本。
4. 意图默认包来自本仓库 testdata 的 GitHub raw（`IAmKings/Dougie` `master` 下 `core/tool/src/test/resources/intent-pack/`）。可用 `local.properties` 的 `dougie.model.*` 覆盖 URL/SHA-256。
5. 卸载后外部目录仍在；重装需再选一次同一文件夹。不要申请全盘存储权限。

## 文档

| 路径 | 内容 |
|------|------|
| [`PRD.md`](PRD.md) | 产品范围、Loop、Tool、§16.1 十四 Case |
| [`.trellis/spec/`](.trellis/spec/) | 给实现用的代码约定（backend / frontend） |
| [`.trellis/tasks/archive/2026-08/08-18-device-e2e-signoff/`](.trellis/tasks/archive/2026-08/08-18-device-e2e-signoff/) | 真机协议与签字记录 |

密钥与 `local.properties` 不要提交。`*.onnx` / `*.gguf` 默认 gitignore；意图 testdata 中的小 `model.onnx` 有例外规则。

## GitHub 发版

推送符合 `v主.次.补丁` 的标签会跑 Release workflow：渠道泄漏检查、打 Play / 侧载 Release APK，并创建 [GitHub Release](https://github.com/IAmKings/Dougie/releases)（附件 `Dougie-<版本>-play.apk` 与 `Dougie-<版本>-sideload.apk`）。`versionName` 来自标签，`versionCode` 为 `主*10000 + 次*100 + 补丁`。

```bash
git tag v0.1.0
git push origin v0.1.0
```

也可在 Actions 里手动跑 **Release**，输入已存在的标签。

上传密钥（可选，建议配置，否则 APK 用 debug 签名并标为预发布）在仓库 **Settings → Secrets and variables → Actions**：

| Secret | 说明 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | `.jks` / `.keystore` 的 base64（`base64 -i release.jks \| pbcopy`） |
| `ANDROID_KEYSTORE_PASSWORD` | 仓库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

不要把 keystore 提交进 git。Play Console 上架不在此 workflow 内。
