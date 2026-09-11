# Implement: MiniCPM5 真机 spike

## Checklist

1. 确认 sideload Debug 已装；打开过一次 app，使 `Android/data/com.dougie.app.sideload/files` 存在。
2. 若产品 Qwen 在探针目录，先移走或换目录备份（不要 git）。
3. 下载 `MiniCPM5-2B_int4.litertlm`，只放进外部 `models/chat/`。
4. `adb shell am start -n com.dougie.app.sideload/com.dougie.tool.chatllm.ChatLlmSpikeActivity` → CPU，再 GPU。屏幕抄数字；不 Logcat 全文。
5. 按 PRD R3 决定是否删除 2B、放入 `minicpm_wi4b32_wi8_afp32.litertlm` 重复步骤 4。
6. 写 `research/pjz110-minicpm5.md`（AC1/AC2）。
7. 恢复 Qwen 文件到产品所用路径。
8. 若改了探针超时：跑 `:app:checkChannelLeak`。未改代码则 leak 检查仍建议跑一次确认无权重打进 APK。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:checkChannelLeak
```

真机：CPU/GPU 各一句；失败记类名/超时，不把补全打进仓库 research 全文（可写「非空中文 / 空 / 超时」）。

## Risky

- 目录里多个 `.litertlm` 导致测错模型。
- 把 MiniCPM 改名为 `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` 污染产品路径。
- 权重进 git / APK。
- 开 `ThinkingConfig(true)`。
- 改 catalog SHA。
