# 侧载对话模型随 App 发版引导更新

## Goal

发新版 App（catalog 里官方文件名或 SHA 变了）后，侧载设置对过期的 0.6B / 1B / 2B 给出「更新」，用户确认下载新包；未改动的档继续用。过期档在新包就绪前仍可闲聊。

## Background

- 三档 SKU id 固定：`chat-qwen06` / `chat-minicpm1b` / `chat-minicpm2b`。运行时按文件名（及体积启发式）认已安装，不校验 SHA。已安装行没有「下载」；刷新跳过已有同名非空文件。因此升版改 catalog 后旧文件仍显示已安装，无法从 UI 拉新权重。
- 新文件名与 SHA 随官方发布变化，由 **App 发版写入 catalog**，不在运行时拉远程清单。Play 无对话行。下载完成不自动改 `activeChatSku`（未在用该档时仍须点「使用」）。

## Requirements

- R1 发版期望 = 该 SKU 在 catalog 的官方文件名 + SHA-256。本地有该档旧文件（启发式或旧文件名），但官方名缺失，或官方名在场而 SHA 不符 → **可更新**。完全没有该档文件 → 仍为「下载」。
- R2 仅设置该行：可更新时「更新」与「使用 / 使用中」并存。Chat 无文案、不弹窗。确认下载走现有 HTTPS → 模型目录 → 应用缓存；成功写入官方新文件名。同名换 SHA 时覆盖该文件。
- R3 新官方文件校验通过后，删除 **应用私有目录**（`filesDir` 与 `getExternalFilesDir` 的 `models/chat/`）里该 SKU 的旧文件名权重。用户 SAF 模型目录中的旧文件不删。其它 SKU 不删。
- R4 新包未就绪时，已激活的过期档仍使 `localLlmReady` 为真（旧文件）。官方新文件就位后，同 SKU 官方名优先；若该档已是激活档，下一句走新权重。未改动的 SKU 行为不变。
- R5 不新增 4B。Play 无对话更新 UI；`checkChannelLeak` 过。不把权重打进 APK。不把路径/哈希打进 Logcat。

## Out of scope

- 不发 App 的远程 catalog / OTA 清单。
- 自动后台下载、Chat 过期提示、按电量换模、NPU、4B。
- 从 `/sdcard/Dougie` POSIX 直读推理；清理用户模型目录里的旧文件。

## Acceptance Criteria

- [ ] AC1 改某一档 catalog 文件名或 SHA 后，该行显示「更新」且仍可「使用」旧文件；确认下载装上新官方文件。
- [ ] AC2 只改一档时，另外两档仍已安装且可激活。
- [ ] AC3 新文件校验通过后，应用私有目录中该 SKU 旧文件名消失；SAF 树中旧文件可仍在。
- [ ] AC4 Play 无对话更新 UI；Chat 无过期提示；`checkChannelLeak` 过。
- [ ] AC5 JVM：过期（文件名/SHA）、已安装且未过期不可再下、更新后删旧名、未改档不受影响。
