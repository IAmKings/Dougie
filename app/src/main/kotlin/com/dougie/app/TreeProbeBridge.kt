package com.dougie.app

import com.dougie.core.tool.TreeSnapshotPort

/**
 * Fill point for the verification layer's tree observer. Channels that can read window
 * content (sideload accessibility) set `provider` at tool registration; Play builds leave
 * it unset, which keeps every verification at verified=null.
 */
object TreeProbeBridge {
    @Volatile
    var provider: TreeSnapshotPort? = null
}
