package com.dougie.core.tool

import java.io.File

object ChatModelLayout {
    const val ID = "chat-qwen06"
    const val MINICPM1B_ID = "chat-minicpm1b"
    const val MINICPM2B_ID = "chat-minicpm2b"
    const val DIR = "models/chat"
    const val MODEL_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
    const val MINICPM1B_FILE = "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"
    const val MINICPM2B_FILE = "MiniCPM5-2B_int4.litertlm"
    val SKU_IDS = listOf(ID, MINICPM1B_ID, MINICPM2B_ID)

    fun isChatSku(id: String): Boolean = normalizeSku(id) in SKU_IDS

    fun normalizeSku(id: String): String = if (id == "chat") ID else id

    fun fileName(skuId: String): String? = when (normalizeSku(skuId)) {
        ID -> MODEL_FILE
        MINICPM1B_ID -> MINICPM1B_FILE
        MINICPM2B_ID -> MINICPM2B_FILE
        else -> null
    }

    fun isPresent(modelDir: File, skuId: String = ID): Boolean = locateInDir(modelDir, skuId) != null

    fun officialFile(modelDir: File, skuId: String): File? {
        val name = fileName(skuId) ?: return null
        val exact = File(modelDir, name)
        return exact.takeIf { it.isFile && it.length() > 0L }
    }

    fun officialFile(chatDirs: List<File>, skuId: String): File? {
        chatDirs.forEach { dir -> officialFile(dir, skuId)?.let { return it } }
        return null
    }

    fun locateInDir(modelDir: File, skuId: String): File? {
        officialFile(modelDir, skuId)?.let { return it }
        return modelDir.listFiles()?.firstOrNull { file ->
            file.isFile && file.length() > 0L && belongsToSku(file, skuId)
        }
    }

    fun legacyNames(skuId: String): Set<String> {
        val extra = when (normalizeSku(skuId)) {
            ID -> setOf("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm")
            MINICPM1B_ID -> setOf(
                "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm",
                "MiniCPM5-1B_int4.litertlm",
                "minicpm_wi4b32_wi8_afp32.litertlm",
            )
            MINICPM2B_ID -> setOf("MiniCPM5-2B_int4.litertlm")
            else -> emptySet()
        }
        return extra + setOfNotNull(fileName(skuId))
    }

    fun belongsToSku(file: File, skuId: String): Boolean {
        val sku = normalizeSku(skuId)
        if (legacyNames(sku).any { it.equals(file.name, ignoreCase = true) }) return true
        return guessSku(file.name, file.length()) == sku
    }

    fun removePrivateStale(skuId: String, chatDirs: List<File>) {
        val official = fileName(skuId) ?: return
        chatDirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (file.name.equals(official, ignoreCase = true)) return@forEach
                if (belongsToSku(file, skuId)) file.delete()
            }
        }
    }

    fun locate(skuId: String, chatDirs: List<File>): File? {
        chatDirs.forEach { dir -> locateInDir(dir, skuId)?.let { return it } }
        return null
    }

    fun chatDirs(filesDir: File, extraRoot: File? = null): List<File> =
        listOfNotNull(File(filesDir, DIR), extraRoot?.let { File(it, DIR) })

    fun guessSku(name: String, length: Long): String? {
        val byName = SKU_IDS.firstOrNull { matchesFileName(name, it) }
        if (byName != null) return byName
        val byLegacy = SKU_IDS.firstOrNull { sku ->
            legacyNames(sku).any { it.equals(name, ignoreCase = true) }
        }
        if (byLegacy != null) return byLegacy
        if (!name.endsWith(".litertlm", ignoreCase = true)) return null
        return when {
            length in 200L * 1024 * 1024..450L * 1024 * 1024 -> ID
            length in 500L * 1024 * 1024..1_100L * 1024 * 1024 -> MINICPM1B_ID
            length in 1_200L * 1024 * 1024..2_400L * 1024 * 1024 -> MINICPM2B_ID
            else -> null
        }
    }

    fun matchesStoredName(actual: String, wanted: String): Boolean {
        if (actual.equals(wanted, ignoreCase = true)) return true
        val sku = SKU_IDS.firstOrNull { fileName(it) == wanted } ?: return false
        return matchesFileName(actual, sku) || guessSku(actual, -1L) == sku
    }

    fun matchesFileName(actual: String, skuId: String): Boolean {
        val want = fileName(skuId) ?: return false
        if (actual.equals(want, ignoreCase = true)) return true
        if (!actual.endsWith(".litertlm", ignoreCase = true)) return false
        val lower = actual.lowercase()
        return when (normalizeSku(skuId)) {
            ID -> lower.contains("qwen3-0.6b")
            MINICPM1B_ID ->
                lower.contains("gpu_opt") ||
                    lower.contains("minicpm5-1b") ||
                    (lower.contains("minicpm") && lower.contains("1b") && !lower.contains("2b"))
            MINICPM2B_ID ->
                lower.contains("minicpm5-2b") ||
                    (lower.contains("minicpm") && lower.contains("2b"))
            else -> false
        }
    }

    fun installedSkus(modelDir: File): List<String> = SKU_IDS.filter { isPresent(modelDir, it) }

    fun resolveActiveSku(modelDir: File, stored: String?): String? =
        resolveActiveSku(stored, listOf(modelDir))

    fun resolveActiveSku(stored: String?, chatDirs: List<File>): String? {
        val installed = chatDirs.flatMap { installedSkus(it) }.distinct()
        val want = stored?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeSku(it) }
        if (want != null && want in installed) return want
        return installed.singleOrNull()
    }
}
