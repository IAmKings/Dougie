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
    const val INTERRUPTED = "任务已中断，请重新提交。"
    const val PERMISSION_DENIED = "未授权，已为你跳过该操作"
    const val CONFIRM_REJECTED = "该操作需你确认后才执行"
    const val CLIPBOARD_NOT_FOREGROUND = "应用不在前台，无法读取剪贴板。"
    const val SCREEN_NOT_FOREGROUND = "应用不在前台，无法截取屏幕。"
    const val SCREEN_MATCH_FAILED = "未能匹配屏幕内容，已停止以免误操作。"
    const val APP_INTENT_DENIED = "该链接不被允许打开。"
    const val APP_INTENT_NOT_FOREGROUND = "应用不在前台，无法打开应用或链接。"
    const val APP_INTENT_LAUNCH_FAILED = "无法打开该应用或链接。"
    const val TAP_SWIPE_CONSENT = "未完成侧载知情同意，无法执行屏幕操作"
    const val TAP_SWIPE_SERVICE = "未开启无障碍服务，无法执行屏幕操作"
    const val TAP_SWIPE_BLOCKED = "该应用不允许自动点击或滑动。"
    const val SPEECH_NOT_FOREGROUND = "应用不在前台，无法使用语音输入。"
    const val SPEECH_MODEL_MISSING = "离线语音模型尚未就绪，无法识别。"
    const val SPEECH_ENGINE_NOT_READY = "离线语音引擎尚未接入，无法识别。"
    const val SPEECH_EMPTY = "没有听到有效语音，请靠近麦克风后重试。"
}

open class AgentException(val userMessage: String) : Exception(userMessage)

class EgressBlockedException : AgentException(UserFacingErrors.EGRESS_BLOCKED)

class MissingApiKeyException : AgentException(UserFacingErrors.MISSING_API_KEY)
