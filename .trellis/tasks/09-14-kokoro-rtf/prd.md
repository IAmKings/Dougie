# Kokoro RTF 门槛评估

## Goal

在 PJZ110 上按规则 B 测 sherpa Kokoro：单线程 RTF ≤ 1.0 且音色过审才作为按需下载备选；否则维持 VITS，不内置、不替换主路径。

## Depends on

`09-14-intent-rule-e` **归档后**再 `task.py start`。

## Background

- 现网 TTS 主选 `vits-zh-hf-fanchen-C`。Kokoro 绑定在 sherpa Kotlin 配置里，catalog 无 Kokoro 行。
- 规则 A：Kokoro 不计入 sideload 内置 ≤400MB。规则 C 不因本刀改变。
- 父任务：`09-14-phase-5-calibration`。

## Notes

RTF 测量句、是否上架 catalog，规划本刀时再定。不达标 = 不做产品开关。
