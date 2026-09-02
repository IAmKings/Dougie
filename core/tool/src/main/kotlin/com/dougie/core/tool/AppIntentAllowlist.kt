package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import java.net.URI
import java.net.URISyntaxException

object AppIntentAllowlist {
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val ALLOWED_SCHEMES = setOf("https", "http", "geo", "package")

    fun validate(uri: String, packageName: String?, allowedPackages: Set<String> = emptySet()): String {
        val raw = uri.trim()
        if (raw.isEmpty() || raw.any { it.isISOControl() }) {
            throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        }
        val parsed = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        }
        val scheme = parsed.scheme?.lowercase()
            ?: throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        if (scheme !in ALLOWED_SCHEMES) {
            throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        }
        val extraPackage = packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (extraPackage != null && !isAndroidPackage(extraPackage)) {
            throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        }
        if (extraPackage != null && extraPackage !in allowedPackages) {
            throw AgentException(UserFacingErrors.APP_INTENT_NOT_ALLOWED)
        }
        return when (scheme) {
            "https", "http" -> {
                val host = parsed.host?.trim().orEmpty()
                if (host.isEmpty()) {
                    throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
                }
                raw
            }
            "geo" -> {
                if (parsed.schemeSpecificPart.isNullOrBlank()) {
                    throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
                }
                raw
            }
            "package" -> {
                val pkg = parsed.schemeSpecificPart?.trim().orEmpty()
                if (!isAndroidPackage(pkg)) {
                    throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
                }
                if (extraPackage != null && extraPackage != pkg) {
                    throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
                }
                if (pkg !in allowedPackages) {
                    throw AgentException(UserFacingErrors.APP_INTENT_NOT_ALLOWED)
                }
                "package:$pkg"
            }
            else -> throw AgentException(UserFacingErrors.APP_INTENT_DENIED)
        }
    }

    fun isAndroidPackage(value: String): Boolean = PACKAGE_NAME.matches(value)
}
