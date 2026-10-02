package com.dougie.app

import com.dougie.core.tool.ForegroundProbePort

/**
 * Fill point for the verification layer's foreground observer. Channels that can observe
 * the system (sideload accessibility) set `provider` at tool registration; Play builds
 * leave it unset, which keeps every verification at verified=null.
 */
object ForegroundProbeBridge {
    @Volatile
    var provider: ForegroundProbePort? = null
}
