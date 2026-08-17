package com.dougie.core.runtime

import com.dougie.core.model.AndroidPermissions
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {

    @Test
    fun defaultGrantAllowsL0AndL1() {
        val engine = PolicyEngine()
        assertEquals(
            PolicyDecision.Allow,
            engine.decide(ToolDescriptor("battery", riskLevel = RiskLevel.L0)),
        )
        assertEquals(
            PolicyDecision.Allow,
            engine.decide(
                ToolDescriptor(
                    name = "calendar_query",
                    riskLevel = RiskLevel.L1,
                    androidPermission = AndroidPermissions.READ_CALENDAR,
                ),
            ),
        )
    }

    @Test
    fun missingPermissionDeniesBeforeConfirm() {
        val engine = PolicyEngine { false }
        val decision = engine.decide(
            ToolDescriptor(
                name = "calendar_create",
                riskLevel = RiskLevel.L2,
                androidPermission = AndroidPermissions.WRITE_CALENDAR,
            ),
        )
        assertTrue(decision is PolicyDecision.DeniedPermission)
    }

    @Test
    fun grantedL2NeedsConfirmation() {
        val engine = PolicyEngine { true }
        assertEquals(
            PolicyDecision.NeedsConfirmation,
            engine.decide(
                ToolDescriptor(
                    name = "calendar_create",
                    riskLevel = RiskLevel.L2,
                    androidPermission = AndroidPermissions.WRITE_CALENDAR,
                ),
            ),
        )
    }
}
