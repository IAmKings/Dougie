package com.dougie.core.runtime

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogTest {
    @Test
    fun noOpListRecentIsEmpty() = runTest {
        assertTrue(NoOpAuditLog.listRecent().isEmpty())
        assertTrue(NoOpAuditLog.listRecent(10).isEmpty())
    }

    @Test
    fun samLambdaKeepsDefaultEmptyListRecent() = runTest {
        val log = AuditLog { _, _, _ -> }
        assertTrue(log.listRecent().isEmpty())
    }
}
