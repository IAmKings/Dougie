package com.dougie.app

import java.io.File
import java.util.Base64
import java.util.UUID

object ScheduleCodec {
    private const val SEP = '\u001f'

    fun encode(items: List<ScheduleItem>): String =
        items.joinToString("\n") { item ->
            listOf(
                item.id,
                item.hour.toString(),
                item.minute.toString(),
                if (item.daily) "1" else "0",
                item.oneShotEpochMillis?.toString().orEmpty(),
                Base64.getEncoder().encodeToString(item.draft.toByteArray(Charsets.UTF_8)),
            ).joinToString(SEP.toString())
        }

    fun decode(raw: String): List<ScheduleItem> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split(SEP)
            if (p.size < 6) return@mapNotNull null
            val draft = runCatching {
                String(Base64.getDecoder().decode(p[5]), Charsets.UTF_8)
            }.getOrDefault("")
            ScheduleItem(
                id = p[0],
                hour = p[1].toIntOrNull() ?: return@mapNotNull null,
                minute = p[2].toIntOrNull() ?: return@mapNotNull null,
                daily = p[3] == "1",
                draft = draft,
                oneShotEpochMillis = p[4].toLongOrNull(),
            )
        }.toList()
    }
}

class ScheduleStore(private val dir: File) {
    private val file = File(dir, FILE_NAME)

    fun list(): List<ScheduleItem> {
        if (!file.isFile) return emptyList()
        return runCatching { ScheduleCodec.decode(file.readText()) }.getOrDefault(emptyList())
    }

    fun find(id: String): ScheduleItem? = list().find { it.id == id }

    fun putPendingDraft(id: String, draft: String) {
        val next = readPending().toMutableMap()
        next[id] = draft
        writePending(next)
    }

    fun takePendingDraft(id: String): String? {
        val next = readPending().toMutableMap()
        val value = next.remove(id)
        writePending(next)
        return value
    }

    fun hasPendingDraft(id: String): Boolean = readPending().containsKey(id)

    fun draftForNotificationTap(id: String): String? {
        if (hasPendingDraft(id)) return takePendingDraft(id).orEmpty()
        return find(id)?.draft
    }

    private fun pendingFile() = File(dir, PENDING_FILE)

    private fun readPending(): Map<String, String> {
        val file = pendingFile()
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.readText().lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                val p = line.split(PENDING_SEP)
                if (p.size < 2) return@mapNotNull null
                val draft = runCatching {
                    String(Base64.getDecoder().decode(p[1]), Charsets.UTF_8)
                }.getOrDefault("")
                p[0] to draft
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writePending(map: Map<String, String>) {
        val file = pendingFile()
        if (map.isEmpty()) {
            file.delete()
            return
        }
        file.writeText(
            map.entries.joinToString("\n") { (id, draft) ->
                id + PENDING_SEP + Base64.getEncoder().encodeToString(draft.toByteArray(Charsets.UTF_8))
            },
        )
    }

    fun save(items: List<ScheduleItem>) {
        file.writeText(ScheduleCodec.encode(items.take(MAX)))
    }

    fun add(item: ScheduleItem): Boolean {
        val cur = list()
        if (cur.size >= MAX) return false
        save(cur + item)
        return true
    }

    fun remove(id: String) {
        save(list().filter { it.id != id })
    }

    companion object {
        const val MAX = 8
        const val FILE_NAME = "dougie_schedules.txt"
        const val PENDING_FILE = "dougie_schedule_pending.txt"
        private const val PENDING_SEP = '\u001f'

        fun newId(): String = UUID.randomUUID().toString()
    }
}
