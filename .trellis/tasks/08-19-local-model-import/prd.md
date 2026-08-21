# 本地模型外部目录与烟测

## Goal

用户选定一个**外部模型根目录**。目录里已有与官方 catalog 哈希一致的 layout 则自动识别为已安装；缺的那一行才显示下载，且下载写入该目录。play 与 sideload 只要选同一个文件夹即可共享。卸载后文件仍在，再授权一次该文件夹即可。设置行保留**测试**烟测。

## Background

当前下载/逐文件导入都写 `filesDir`：卸载丢失；`com.dougie.app` 与 `com.dougie.app.sideload` 不能互读。JNI 需要 POSIX 路径，不能直接读 `content://`。用户接受：重装后用 SAF 再选一次同一文件夹（`takePersistableUriPermission` 随卸载失效）。不做全盘路径、不申请 `MANAGE_EXTERNAL_STORAGE`。

## Requirements

- **R1** 设置提供「模型目录」：`OpenDocumentTree` + 持久授权；URI 写入 prefs。无目录或授权失效时，各行不下载，提示先选目录。
- **R2** 约定子目录：`models/asr`、`models/tts`、`models/intent`（文件写成 layout 名），三者共用一个 `models`，不得为每次下载新建 `models (1)`。**刷新**或**重新选择目录**才全量扫描外部树（按 **SHA-256** 匹配 catalog）。进设置页不自动扫描；未刷新时用 `filesDir` 缓存显示已安装。齐则已安装；不齐则该行「下载」。扫描须覆盖 SAF 已 uniquify 的同名目录。
- **R3** 识别成功后把通过哈希的文件同步到 `filesDir` 供 JNI（`.part` + 校验）。引擎仍读 `filesDir`。
- **R4** 下载在用户确认后：HTTPS 拉到临时文件 → 哈希 → 写入**外部目录**对应 layout 名 → 再同步 `filesDir`。不把外部目录当 Agent 可写路径暴露给 LLM。
- **R5** 无下载 URL 时：目录里哈希齐全仍可识别；不齐则不能下载（沿用「尚未配置下载地址」）。
- **R6** 意图为**单一 ONNX 分类包**（`models/intent/` 下 catalog 锁定的 layout 名：`model.onnx` + tokenizer + 标签表）。**不再**提供 Q4/Q8 GGUF 互斥行；目录里若仍有历史 `model.gguf` 不得标为已安装。
- **R7** 已同步到 `filesDir` 的行提供**测试**（ASR 短静音不抛；TTS generate 不外放；意图「现在几点」不抛）。
- **R8** JVM：扫描匹配、错哈希不标已安装、无树不可下载。真机 JNI 不挡合并。

## Out of Scope

- 逐文件多选导入作为主路径（可留 `ModelImporter` 给同步复用）。
- 固定 `/sdcard/...`、全盘权限、任意第三方架构、Agent 触发安装、TTS 烟测外放。
- 改 sideload APK 内置 seed 策略（仍可写入 `filesDir`；与外部目录对齐留后续）。

## Acceptance Criteria

- [ ] 选中目录且 ASR/TTS/意图文件哈希正确 → 对应行已安装，无 HTTP。
- [ ] 目录缺文件 → 该行显示下载（URL 已配置时）；下载后外部目录与 `filesDir` 均有 layout 文件。
- [ ] 未选目录 / 授权失效 → 不能下载，中文提示重新选择。
- [ ] 测试按钮行为同前：成功/失败中文；TTS 不外放。
- [ ] play 与 sideload 只要用户选同一文件夹即可各自识别（各包各自授权）。

## Key Decisions

- **D1** SHA-256 必须过。
- **D2** 测试为烟测。
- **D3** 重装后再授权同一文件夹（接受）。
- **D4** 真实来源是外部目录；`filesDir` 只是 JNI 缓存同步。
- **D5** 不用全盘访问。
