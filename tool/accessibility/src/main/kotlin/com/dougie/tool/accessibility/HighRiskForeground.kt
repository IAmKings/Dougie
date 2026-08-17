package com.dougie.tool.accessibility

object HighRiskForeground {
    private val exact = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "com.chinamworld.main",
        "com.icbc",
    )

    private val tokens = listOf(
        "alipay",
        "unionpay",
        "bank",
        "wallet",
        "password",
        "keepass",
        "bitwarden",
        "lastpass",
        "1password",
        "authenticator",
        "pay.",
        ".pay",
    )

    fun isBlocked(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) return true
        if (pkg in exact) return true
        val lower = pkg.lowercase()
        return tokens.any { lower.contains(it) }
    }
}
