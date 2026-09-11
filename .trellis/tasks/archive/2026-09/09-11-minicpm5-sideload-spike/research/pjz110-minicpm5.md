# PJZ110 MiniCPM5 INT4 spike

- **Date**: 2026-09-11
- **Device**: OnePlus 13 / PJZ110 (Snapdragon 8 Elite, Adreno 830)
- **Runtime**: sideload LiteRT-LM 0.16.1，`ChatLlmSpikeActivity` / `ChatLlmProbe`，`ThinkingConfig(false)`，prompt「用一句话介绍你自己」，max 64 tokens
- **Note**: `/sdcard/Dougie/models/chat/` 对应用 uid 是 `Permission denied`（`770`）。探针只读 `filesDir` 然后 external files。每档测时把内部 Qwen 暂移出 `filesDir/models/chat/`，测完已搬回。2B 测完后用户加测 1B GPU 优化包。

未改 catalog / `ChatModelLayout` / Loop。

## 2B — `MiniCPM5-2B_int4.litertlm`（1553670064 bytes）

| 后端 | 加载 | 首 token | 生成 | 片段 | 字符 | 字符/秒 | 状态 |
|---|---|---|---|---|---|---|---|
| CPU | 5885 ms | 2342 ms | 3404 ms | 22 | 48 | 14.1 | 成功 |
| GPU (OpenCL) | 15301 ms | 496 ms | 1456 ms | 22 | 48 | 33.0 | 成功 |

## 1B — `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm`（756MB）

同一探针，CPU 与 GPU 均非空中文。gpu_opt 在 CPU 上也能出字（降级可用）。

| 后端 | 加载 | 首 token | 生成 | 片段 | 字符 | 字符/秒 | 状态 |
|---|---|---|---|---|---|---|---|
| CPU | 2003 ms | 1502 ms | 1909 ms | 23 | 50 | 26.2 | 成功 |
| GPU (OpenCL) | 6898 ms | 709 ms | 1065 ms | 23 | 50 | 46.9 | 成功 |

## 同机对照（含 2026-09-05 Qwen3-0.6B）

| 模型 | GPU 加载 | GPU 首 token | GPU 字符/秒 | 包体积 |
|---|---|---|---|---|
| Qwen3-0.6B INT4 | 4109 ms | 799 ms | 25.9 | ~328MB |
| MiniCPM5-1B gpu_opt | 6898 ms | 709 ms | **46.9** | ~756MB |
| MiniCPM5-2B INT4 | 15301 ms | **496 ms** | 33.0 | ~1.55GB |

## Conclusion

两档 **都能**在本机 LiteRT-LM 上闲聊。体感上 GPU 才达标。

- **1B gpu_opt**：加载明显短于 2B（~7s vs ~15s），解码最快；体积约一半。更像「替换 0.6B 还不把冷启动拖到 15s」的候选。
- **2B INT4**：首 token 最快，质量档更高（未做工具/长对话评测），冷启动和 1.55GB 下载是代价。

本切片仍不切设置。下一步 catalog 若以速度/体积为先选 1B gpu_opt；若以能力为先选 2B。工具 XML 仍未测。
