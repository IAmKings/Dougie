# Local-first + Explicit Data Egress(而非"数据绝不离开设备")

V1.0 宣称"隐私绝对安全 / 所有数据绝不离开设备",但该目标与"支持 Cloud LLM"天然冲突。我们决定采用 **Local-first + Explicit Data Egress**:默认本地保存,任何上云必须经过明确的 EgressPolicy(默认 `allowCloud = false`,用户主动配置 Cloud Provider 时明确提示)。理由:只要接入云端 LLM 就无法承诺数据永不离开设备,绝对化承诺反而不可信。

**Status**: accepted

**Considered Options**: 纯本地(放弃 Cloud LLM,损失能力);绝对隐私承诺(不可信,与产品形态矛盾)。
