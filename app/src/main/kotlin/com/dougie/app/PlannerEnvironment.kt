package com.dougie.app

import com.dougie.core.tool.OpenAppEntries

/**
 * Environment block injected into the planner prompt (cloud and local paths): the user's
 * open-app alias→package inventory and the screen resolution, so the model can act on
 * "打开X，点击Y" without asking back for package names or tap locations.
 */
object PlannerEnvironment {
    const val MAX_ENTRIES = 15
    const val MAX_LENGTH = 600

    fun build(openAppsJson: String, widthPixels: Int, heightPixels: Int): String {
        val apps = OpenAppEntries.parse(openAppsJson).take(MAX_ENTRIES)
        val parts = buildList {
            if (apps.isNotEmpty()) {
                add("可打开的应用（别名=包名）：" + apps.joinToString("、") { "${it.alias}=${it.packageName}" } + "。")
            }
            if (widthPixels > 0 && heightPixels > 0) {
                add(
                    "屏幕分辨率 ${widthPixels}x${heightPixels}；点击底部 Tab 时 y 约为高度减 110 像素附近，" +
                        "多个 Tab 横向均分宽度。",
                )
            }
        }
        if (parts.isEmpty()) return ""
        val body = parts.joinToString("") + "已知以上信息后直接执行，无需向用户询问包名或位置。"
        return body.take(MAX_LENGTH)
    }
}
