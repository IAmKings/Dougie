package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.CompletionPath
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryItemTest {
    @Test
    fun mapsPersistedTaskFields() {
        val item = AgentTask(
            taskId = "t1",
            input = "帮我约明天下午开会并且再确认一下地点和时间安排",
            status = TaskStatus.FAILED,
            loopCount = 2,
            toolTrace = listOf(
                ToolTraceEntry(toolCallId = "c1", toolName = "calendar_query", argsSummary = "{}"),
                ToolTraceEntry(toolCallId = "c2", toolName = "calendar_create", argsSummary = "{}"),
            ),
            lastError = UserFacingErrors.INTERRUPTED,
            conversationId = "thread-z",
        ).toHistoryItem(maxInputChars = 8)
        assertEquals("帮我约明天下午开…", item.inputSummary)
        assertEquals("失败", item.statusLabel)
        assertEquals(2, item.loopCount)
        assertEquals("calendar_query → calendar_create", item.toolChain)
        assertEquals(
            listOf(
                HistoryToolStep("c1", "calendar_query", "进行中"),
                HistoryToolStep("c2", "calendar_create", "进行中"),
            ),
            item.steps,
        )
        assertEquals(UserFacingErrors.INTERRUPTED, item.error)
        assertEquals("thread-z", item.conversationId)
        assertNull(item.durationLabel)
        assertNull(item.providerLabel)
        assertNull(item.completedAtLabel)
    }

    @Test
    fun mapsToolTraceStepsAndEmptyTrace() {
        val mixed = AgentTask(
            taskId = "t-steps",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "ok",
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "time",
                    argsSummary = """{"secret":true}""",
                    resultJson = """{"iso":"x"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
                ToolTraceEntry(
                    toolCallId = "c2",
                    toolName = "battery",
                    argsSummary = "{}",
                    status = ToolTraceStatus.FAILED,
                ),
                ToolTraceEntry(
                    toolCallId = "c3",
                    toolName = "calendar_query",
                    argsSummary = """{"title":"开会"}""",
                    status = ToolTraceStatus.PENDING,
                ),
                ToolTraceEntry(
                    toolCallId = "c4",
                    toolName = "clipboard_read",
                    argsSummary = "{}",
                    resultJson = """{"text":"剪贴板秘密"}""",
                    status = ToolTraceStatus.EXECUTING,
                ),
                ToolTraceEntry(
                    toolCallId = "c5",
                    toolName = "sms_compose",
                    argsSummary = """{"to":"120","body":"短信正文"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        ).toHistoryItem()
        assertEquals("time → battery → calendar_query → clipboard_read → sms_compose", mixed.toolChain)
        assertEquals(
            listOf(
                HistoryToolStep("c1", "time", "成功"),
                HistoryToolStep("c2", "battery", "失败"),
                HistoryToolStep("c3", "calendar_query", "进行中"),
                HistoryToolStep("c4", "clipboard_read", "进行中"),
                HistoryToolStep("c5", "sms_compose", "成功"),
            ),
            mixed.steps,
        )
        val chrome = mixed.steps.joinToString("\n") { "${it.toolName}  ·  ${it.statusLabel}" }
        val dumped = mixed.toString() + chrome
        listOf("secret", "iso", "开会", "剪贴板秘密", "短信正文", "argsSummary", "resultJson", "{").forEach { leak ->
            assertEquals(false, dumped.contains(leak))
        }
        val names = listOf(HistoryItem::class.java, HistoryToolStep::class.java)
            .flatMap { type -> type.declaredFields.map { it.name } }
        assertEquals(false, names.any { it.contains("resultJson", ignoreCase = true) })
        assertEquals(false, names.any { it.contains("args", ignoreCase = true) })
        assertEquals(false, names.any { it.contains("risk", ignoreCase = true) })
        val empty = AgentTask(
            taskId = "t-empty",
            input = "你好",
            status = TaskStatus.COMPLETED,
            finalAnswer = "hi",
        ).toHistoryItem()
        assertEquals("", empty.toolChain)
        assertEquals(emptyList<HistoryToolStep>(), empty.steps)
    }

    @Test
    fun mapsDurationAndProviderLabels() {
        val nowMs = shanghaiMs(2026, 9, 16, 15, 0)
        val endedAt = shanghaiMs(2026, 9, 16, 14, 32, 59)
        val item = AgentTask(
            taskId = "t-meta",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
            completionPath = CompletionPath.LOCAL_LLM,
            startedAt = endedAt - 3_000L,
            endedAt = endedAt,
        ).toHistoryItem(nowMs = nowMs, zone = shanghai)
        assertEquals("3秒", item.durationLabel)
        assertEquals("本地 LLM", item.providerLabel)
        assertEquals("今天 14:32", item.completedAtLabel)
        assertEquals(
            "本地意图",
            AgentTask(
                taskId = "t-intent",
                input = "现在几点",
                status = TaskStatus.COMPLETED,
                completionPath = CompletionPath.LOCAL_INTENT,
            ).toHistoryItem().providerLabel,
        )
        assertEquals(
            "远程 LLM",
            AgentTask(
                taskId = "t-remote",
                input = "你好",
                status = TaskStatus.COMPLETED,
                completionPath = CompletionPath.REMOTE_LLM,
            ).toHistoryItem().providerLabel,
        )
        assertEquals(
            "3秒 · 本地 LLM · 今天 14:32",
            listOfNotNull(item.durationLabel, item.providerLabel, item.completedAtLabel)
                .joinToString(" · "),
        )
        val none = AgentTask(
            taskId = "t-none",
            input = "查电量",
            status = TaskStatus.COMPLETED,
        ).toHistoryItem()
        assertNull(none.providerLabel)
        assertNull(none.completedAtLabel)
        assertEquals(
            "",
            listOfNotNull(none.durationLabel, none.providerLabel, none.completedAtLabel)
                .joinToString(" · "),
        )
        assertEquals(
            "2秒",
            AgentTask(
                taskId = "t-fail",
                input = "查电量",
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.CANCELLED,
                startedAt = 1_000L,
                endedAt = 3_000L,
            ).toHistoryItem().durationLabel,
        )
        val onlyCompletedAt = AgentTask(
            taskId = "t-only-at",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            endedAt = endedAt,
        ).toHistoryItem(nowMs = nowMs, zone = shanghai)
        assertNull(onlyCompletedAt.durationLabel)
        assertNull(onlyCompletedAt.providerLabel)
        assertEquals("今天 14:32", onlyCompletedAt.completedAtLabel)
        assertEquals(
            "今天 14:32",
            listOfNotNull(
                onlyCompletedAt.durationLabel,
                onlyCompletedAt.providerLabel,
                onlyCompletedAt.completedAtLabel,
            ).joinToString(" · "),
        )
    }

    @Test
    fun durationMissingEitherTimestampStaysNull() {
        val startedOnly = AgentTask(
            taskId = "t-start",
            input = "查电量",
            status = TaskStatus.THINKING,
            completionPath = CompletionPath.REMOTE_LLM,
            startedAt = 1_000L,
        ).toHistoryItem()
        assertNull(startedOnly.durationLabel)
        assertNull(startedOnly.completedAtLabel)
        assertEquals("远程 LLM", startedOnly.providerLabel)
        val nowMs = shanghaiMs(2026, 9, 16, 15, 0)
        val endedOnly = AgentTask(
            taskId = "t-end",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            endedAt = 2_000L,
        ).toHistoryItem(nowMs = nowMs, zone = shanghai)
        assertNull(endedOnly.durationLabel)
        assertEquals(formatCompletedAt(2_000L, nowMs, shanghai), endedOnly.completedAtLabel)
    }

    @Test
    fun formatTaskDurationCoversBucketsAndNegative() {
        assertNull(formatTaskDuration(null, 1_000L))
        assertNull(formatTaskDuration(1_000L, null))
        assertEquals("不足1秒", formatTaskDuration(1_000L, 1_500L))
        assertEquals("3秒", formatTaskDuration(0L, 3_000L))
        assertEquals("59秒", formatTaskDuration(0L, 59_000L))
        assertEquals("1分", formatTaskDuration(0L, 60_000L))
        assertEquals("1分12秒", formatTaskDuration(0L, 72_000L))
        assertEquals("不足1秒", formatTaskDuration(5_000L, 1_000L))
    }

    @Test
    fun formatCompletedAtCoversBucketsAndTruncatesSeconds() {
        val nowMs = shanghaiMs(2026, 9, 16, 15, 0)
        assertNull(formatCompletedAt(null, nowMs, shanghai))
        assertEquals(
            "今天 14:32",
            formatCompletedAt(shanghaiMs(2026, 9, 16, 14, 32, 59), nowMs, shanghai),
        )
        assertEquals(
            "昨天 14:32",
            formatCompletedAt(shanghaiMs(2026, 9, 15, 14, 32), nowMs, shanghai),
        )
        assertEquals(
            "9月5日 09:05",
            formatCompletedAt(shanghaiMs(2026, 9, 5, 9, 5), nowMs, shanghai),
        )
        assertEquals(
            "2025年9月15日 14:32",
            formatCompletedAt(shanghaiMs(2025, 9, 15, 14, 32), nowMs, shanghai),
        )
        val mapped = AgentTask(
            taskId = "t-completed-at",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            endedAt = shanghaiMs(2026, 9, 16, 14, 32, 59),
        ).toHistoryItem(nowMs = nowMs, zone = shanghai)
        assertEquals("今天 14:32", mapped.completedAtLabel)
        assertNull(
            AgentTask(
                taskId = "t-no-end",
                input = "查电量",
                status = TaskStatus.COMPLETED,
            ).toHistoryItem(nowMs = nowMs, zone = shanghai).completedAtLabel,
        )
    }

    @Test
    fun mapsMissingConversationIdToDefault() {
        val item = AgentTask(
            taskId = "t2",
            input = "查电量",
            status = TaskStatus.COMPLETED,
            finalAnswer = "63%",
        ).toHistoryItem()
        assertEquals(ConversationIds.DEFAULT, item.conversationId)
    }

    @Test
    fun groupsTwoWindowsMostRecentlyActiveFirst() {
        val extra = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val sections = toHistorySections(
            listOf(
                historyTurn("e2", extra, "extra newer"),
                historyTurn("e1", extra, "extra older"),
                historyTurn("d2", ConversationIds.DEFAULT, "default newer"),
                historyTurn("d1", ConversationIds.DEFAULT, "default older"),
            ),
        )
        assertEquals(2, sections.size)
        assertEquals(extra, sections[0].conversationId)
        assertEquals("对话 2", sections[0].title)
        assertEquals(listOf("e2", "e1"), sections[0].items.map { it.taskId })
        assertEquals(ConversationIds.DEFAULT, sections[1].conversationId)
        assertEquals("默认会话", sections[1].title)
        assertEquals(listOf("d2", "d1"), sections[1].items.map { it.taskId })
        sections.forEach { section ->
            assertEquals(false, section.title.contains(extra))
            assertEquals(false, section.title.contains("aaaa"))
        }
    }

    @Test
    fun droppingSiblingExtraRenumbersCurrentConversationTitle() {
        val older = "window-older"
        val newer = "window-newer"
        assertEquals(
            "对话 3",
            currentConversationTitle(
                conversationId = newer,
                recentItems = listOf(historyTurn("n1", newer), historyTurn("o1", older)),
                windowEmpty = false,
            ),
        )
        assertEquals(
            "对话 2",
            currentConversationTitle(
                conversationId = newer,
                recentItems = listOf(historyTurn("n1", newer)),
                windowEmpty = false,
            ),
        )
    }

    @Test
    fun extraSectionCanDeleteAndDefaultCannot() {
        val extra = toHistorySections(listOf(historyTurn("e1", "window-extra"))).single()
        val default = toHistorySections(
            listOf(historyTurn("d1", ConversationIds.DEFAULT)),
        ).single()
        assertEquals(true, extra.canDelete())
        assertEquals(false, default.canDelete())
        assertEquals(false, HistorySection(ConversationIds.DEFAULT, "家里", emptyList()).canDelete())
    }

    @Test
    fun emptyHistoryHasNoSections() {
        assertEquals(emptyList<HistorySection>(), toHistorySections(emptyList()))
    }

    @Test
    fun groupsInterleavedTurnsByWindowKeepingNewestFirst() {
        val extra = "window-extra"
        val sections = toHistorySections(
            listOf(
                historyTurn("d3", ConversationIds.DEFAULT),
                historyTurn("e2", extra),
                historyTurn("d2", ConversationIds.DEFAULT),
                historyTurn("e1", extra),
                historyTurn("d1", ConversationIds.DEFAULT),
            ),
        )
        assertEquals(listOf(ConversationIds.DEFAULT, extra), sections.map { it.conversationId })
        assertEquals(listOf("d3", "d2", "d1"), sections[0].items.map { it.taskId })
        assertEquals(listOf("e2", "e1"), sections[1].items.map { it.taskId })
        assertEquals("默认会话", sections[0].title)
        assertEquals("对话 2", sections[1].title)
    }

    @Test
    fun extraWindowIsDialogueTwoEvenWhenAlone() {
        val extra = "11111111-2222-3333-4444-555555555555"
        val sections = toHistorySections(
            listOf(
                historyTurn("e2", extra),
                historyTurn("e1", extra),
            ),
        )
        assertEquals(1, sections.size)
        assertEquals("对话 2", sections[0].title)
        assertEquals(false, sections[0].title.contains(extra))
    }

    @Test
    fun customTitleOverridesNumberedDialogue() {
        val extra = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val sections = toHistorySections(
            listOf(historyTurn("e1", extra)),
            titles = mapOf(extra to "工作"),
        )
        assertEquals(1, sections.size)
        assertEquals("工作", sections[0].title)
        assertEquals(false, sections[0].title.contains(extra))
        assertEquals(false, sections[0].title.contains("aaaa"))
    }

    @Test
    fun blankCustomTitleFallsBackToNumberedOrDefault() {
        val extra = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val extraSections = toHistorySections(
            listOf(historyTurn("e1", extra)),
            titles = mapOf(extra to "  \n "),
        )
        assertEquals("对话 2", extraSections[0].title)
        assertEquals(false, extraSections[0].title.contains(extra))
        val defaultSections = toHistorySections(
            listOf(historyTurn("d1", ConversationIds.DEFAULT)),
            titles = mapOf(ConversationIds.DEFAULT to "   "),
        )
        assertEquals("默认会话", defaultSections[0].title)
    }

    @Test
    fun customDefaultTitleOverridesFallback() {
        val sections = toHistorySections(
            listOf(historyTurn("d1", ConversationIds.DEFAULT)),
            titles = mapOf(ConversationIds.DEFAULT to "家里"),
        )
        assertEquals("家里", sections[0].title)
    }

    @Test
    fun unlistedEmptyWindowUsesNewConversationLabel() {
        val extra = "11111111-2222-3333-4444-555555555555"
        assertEquals(
            "新对话",
            currentConversationTitle(
                conversationId = extra,
                recentItems = emptyList(),
                windowEmpty = true,
            ),
        )
        assertEquals(
            "默认会话",
            currentConversationTitle(
                conversationId = ConversationIds.DEFAULT,
                recentItems = emptyList(),
                windowEmpty = true,
            ),
        )
        assertEquals(
            "家里",
            currentConversationTitle(
                conversationId = ConversationIds.DEFAULT,
                titles = mapOf(ConversationIds.DEFAULT to "家里"),
                recentItems = emptyList(),
                windowEmpty = true,
            ),
        )
        val listed = listOf(historyTurn("e1", extra))
        assertEquals(
            "对话 2",
            currentConversationTitle(
                conversationId = extra,
                recentItems = listed,
                windowEmpty = false,
            ),
        )
        assertEquals(
            false,
            currentConversationTitle(
                conversationId = extra,
                recentItems = listed,
                windowEmpty = false,
            ).contains(extra),
        )
    }

    @Test
    fun numbersNonDefaultByOldestFirstAppearance() {
        val newerExtra = "window-newer-uuid"
        val olderExtra = "window-older-uuid"
        val sections = toHistorySections(
            listOf(
                historyTurn("n2", newerExtra),
                historyTurn("n1", newerExtra),
                historyTurn("o2", olderExtra),
                historyTurn("o1", olderExtra),
            ),
        )
        assertEquals(newerExtra, sections[0].conversationId)
        assertEquals("对话 3", sections[0].title)
        assertEquals(olderExtra, sections[1].conversationId)
        assertEquals("对话 2", sections[1].title)
    }

    private fun historyTurn(
        taskId: String,
        conversationId: String,
        input: String = "查电量",
    ) = AgentTask(
        taskId = taskId,
        input = input,
        status = TaskStatus.COMPLETED,
        finalAnswer = "ok",
        conversationId = conversationId,
        loopCount = 1,
    ).toHistoryItem()

    private companion object {
        val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")

        fun shanghaiMs(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int = 0,
        ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, shanghai)
            .toInstant()
            .toEpochMilli()
    }
}
