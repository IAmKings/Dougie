# Tool-driven + Policy Engine 强制校验链

Agent 不直接修改系统状态,所有副作用必须经 Tool;且每次 Tool 调用强制走 Policy 链(`ToolCallSanitizer → Schema → Permission → Risk → Confirmation → Audit`),无例外路径。理由:Agent 能调用真实 Android API,而 LLM 输出不可信(存在 Prompt Injection 与 Tool Calling 幻觉),必须把权限与风险决策从 LLM 手中剥离,由 Policy Engine 统一裁决。

**Status**: accepted

**Considered Options**: LLM 直接执行(不可控、易被注入);仅 Schema 校验(无法阻止越权 Tool)。
