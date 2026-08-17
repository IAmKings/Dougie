package com.dougie.core.tool

interface LocationPort {
    suspend fun lastKnownCoarse(): String
}

class FakeLocationPort(
    private val json: String =
        """{"ok":true,"latitude":31.23,"longitude":121.47,"accuracy_m":500.0,"provider":"network"}""",
) : LocationPort {
    var queryCount: Int = 0
        private set

    override suspend fun lastKnownCoarse(): String {
        queryCount += 1
        return json
    }
}
