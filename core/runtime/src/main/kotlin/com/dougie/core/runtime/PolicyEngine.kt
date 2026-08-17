package com.dougie.core.runtime

import com.dougie.core.model.RiskLevel
import com.dougie.core.model.ToolDescriptor

sealed class PolicyDecision {
    data object Allow : PolicyDecision()
    data class DeniedPermission(val permission: String) : PolicyDecision()
    data object NeedsConfirmation : PolicyDecision()
}

class PolicyEngine(
    private val isGranted: (String) -> Boolean = { true },
) {
    fun decide(descriptor: ToolDescriptor): PolicyDecision {
        val permission = descriptor.androidPermission
        if (permission != null && !isGranted(permission)) {
            return PolicyDecision.DeniedPermission(permission)
        }
        if (descriptor.riskLevel == RiskLevel.L2 || descriptor.riskLevel == RiskLevel.L3) {
            return PolicyDecision.NeedsConfirmation
        }
        return PolicyDecision.Allow
    }
}
