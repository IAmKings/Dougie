package com.dougie.core.model

object UserFacingErrors {
    const val EGRESS_BLOCKED = "云端调用已被拦截。请先在设置中授权数据出境。"
    const val MISSING_API_KEY = "尚未配置 API 密钥。请在设置中填写密钥后再试。"
    const val LLM_TIMEOUT = "模型响应超时，请稍后重试。"
    const val TOOL_TIMEOUT = "工具执行超时，请稍后重试。"
    const val LLM_FAILED = "模型调用失败，请稍后重试。"
    const val NETWORK_FAILED = "网络请求失败，请检查连接后重试。"
    const val TOOL_FAILED = "工具执行失败，请稍后重试。"
    const val UNKNOWN_TOOL = "模型调用了未知工具，已拒绝执行。"
    const val INVALID_TOOL_ARGS = "工具参数无效，已拒绝执行。"
    const val CANCELLED = "任务已取消。"
}

open class AgentException(val userMessage: String) : Exception(userMessage)

class EgressBlockedException : AgentException(UserFacingErrors.EGRESS_BLOCKED)

class MissingApiKeyException : AgentException(UserFacingErrors.MISSING_API_KEY)
