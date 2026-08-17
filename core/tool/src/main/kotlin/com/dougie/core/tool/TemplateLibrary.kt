package com.dougie.core.tool

object TemplateLibrary {
    const val SOLID = "solid"

    fun frame(templateId: String): ScreenFrame? {
        return when (templateId) {
            SOLID -> ScreenFrame(
                id = SOLID,
                width = 8,
                height = 8,
                gray = ByteArray(64) { -1 },
            )
            else -> null
        }
    }
}
