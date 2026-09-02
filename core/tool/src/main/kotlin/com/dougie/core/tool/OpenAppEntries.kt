package com.dougie.core.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class OpenAppEntry(
    val alias: String,
    val packageName: String,
)

object OpenAppEntries {
    const val ALIAS_MAX = 32

    fun parse(raw: String): List<OpenAppEntry> {
        if (raw.isBlank()) return emptyList()
        val arr = try {
            Json.parseToJsonElement(raw).jsonArray
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<OpenAppEntry>(arr.size)
        val seen = HashSet<String>()
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            val alias = obj["alias"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val pkg = obj["package"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (alias.isEmpty() || alias.length > ALIAS_MAX) continue
            if (!AppIntentAllowlist.isAndroidPackage(pkg)) continue
            if (!seen.add(pkg)) continue
            out += OpenAppEntry(alias = alias, packageName = pkg)
        }
        return out
    }

    fun encode(entries: List<OpenAppEntry>): String {
        return JsonArray(
            entries.map { entry ->
                buildJsonObject {
                    put("alias", entry.alias)
                    put("package", entry.packageName)
                }
            },
        ).toString()
    }

    fun upsert(entries: List<OpenAppEntry>, alias: String, packageName: String): List<OpenAppEntry>? {
        val name = alias.trim()
        val pkg = packageName.trim()
        if (name.isEmpty() || name.length > ALIAS_MAX) return null
        if (!AppIntentAllowlist.isAndroidPackage(pkg)) return null
        val without = entries.filterNot { it.packageName == pkg }
        return without + OpenAppEntry(alias = name, packageName = pkg)
    }

    fun remove(entries: List<OpenAppEntry>, packageName: String): List<OpenAppEntry> {
        return entries.filterNot { it.packageName == packageName }
    }

    fun packages(entries: List<OpenAppEntry>): Set<String> =
        entries.map { it.packageName }.toSet()

    fun match(input: String, entries: List<OpenAppEntry>): OpenAppEntry? {
        val key = stripOpenPrefix(input)
        if (key.isEmpty()) return null
        return entries.firstOrNull { it.alias == key }
    }

    fun stripOpenPrefix(input: String): String {
        val s = input.trim()
        for (prefix in OPEN_PREFIXES) {
            if (s.startsWith(prefix) && s.length > prefix.length) {
                return s.removePrefix(prefix).trim()
            }
        }
        return s
    }

    private val OPEN_PREFIXES = listOf("请打开", "帮我打开", "打开")
}
