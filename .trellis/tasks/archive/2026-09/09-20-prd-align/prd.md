# PRD 与现状对齐

## Goal

把根 `PRD.md` 和 `README.md` 改成与 v0.1.4 现网一致：已提前做完的 Beta 标成已交付，过时句子改掉；不宣称没做的能力已经完成。

## User value

以后按文档排期时，不会把已经上线的无障碍 / 语音 / 意图 / 端侧对话 / 向量记忆再当成「还没做」。

## Background

- 现网 **v0.1.4**，`minSdk` 26（Android 8.0+）。README 已写 8.0+ 与双渠道；根 PRD 文头仍写 Android 10+。
- §15 Phase 5 仍是无状态清单。现网已有：侧载 `TapSwipeTool`、离线 ASR/TTS、意图分类（规则 E 真机过门）、侧载 Local LLM、快捷设置磁贴、悬浮球、定时提醒（到点通知打开对话、不自动 submit）、向量记忆（FTS 降级仍在）。
- 尚未当产品做完：自动读通知、完整多模态上下文、打字机/M3 双主题、规则 D 500 条真机 CER、Compose UI 五态测试、商店上架。
- §3.2 仍写「向量语义检索 MVP 不做」。工具卡 README 仍像常驻 dump；对话已默认收起可展开。用户气泡已有「语音转写」。

## Requirements

- R1 §15 Phase 5：每条标明 **已交付**（及渠道/边界）或 **未做**。已交付不得删掉历史「曾标 Beta」的演进事实；在清单上写现状。
- R2 纠正直接矛盾：文头平台与 `minSdk` 26 对齐；§3.2 / US-001 向量句改为「已交付，FTS 为未就绪降级」，不再写「MVP 阶段不做」。
- R3 §16.2 Phase 5 完成标准：规则 E 真机已过门；规则 D runner 在、500 条真机集未闭合；TTS 现网 VITS 非 Kokoro。不把未闭合的门标成达标。
- R4 README：工具卡改为默认收起、可展开；可补一句语音气泡「语音转写」。版本号仍 0.1.4，不发版。
- R5 不改 Kotlin / 资源 / 工作流以外的产品行为。不把打字机、暗色主题、Compose UI 测试、商店上架勾成已完成。

## Out of scope

- 改代码、动效、规则 D 真机采集、发版。
- 重写 `source/` 历史 PRD。
- 把 §16.3 DoD 空复选框全部打勾。

## Technical notes

- 文档刀：改 `PRD.md`、`README.md`；必要时加一条 V2.1 演进记录说明「现状对齐」。不 bump 无意义的功能版本号。

## Acceptance Criteria

- [x] AC1 Phase 5 十条都能看出已交付或未做；已交付条与现网渠道一致（play 无无障碍/端侧对话权重）。
- [x] AC2 文头不再写死 Android 10+ 而与 README 8.0+ 冲突；向量不再出现在「MVP 不做」清单里当未做项。
- [x] AC3 README 工具卡 / 语音标注与现网一致；仍写尚未上架商店、v0.1.4。
- [x] AC4 git diff 不含 `.kt` / Gradle 功能改动。
