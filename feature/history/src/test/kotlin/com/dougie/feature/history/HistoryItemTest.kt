package com.dougie.feature.history

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals(UserFacingErrors.INTERRUPTED, item.error)
        assertEquals("thread-z", item.conversationId)
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
}
