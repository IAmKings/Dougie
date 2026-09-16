package com.dougie.core.runtime

import com.dougie.core.llm.FakeLlmProvider
import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationIds
import com.dougie.core.model.TaskStatus
import com.dougie.core.tool.FakeBatteryTool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTaskManagerTest {

    @Test
    fun secondSubmitMovesFirstTurnIntoTranscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val first = manager.task.value
        requireNotNull(first)
        assertEquals(ConversationIds.DEFAULT, first.conversationId)
        assertTrue(manager.transcript.value.isEmpty())
        manager.submit("现在几点了？")
        advanceUntilIdle()
        val second = manager.task.value
        requireNotNull(second)
        assertNotEquals(first.taskId, second.taskId)
        assertEquals(listOf(first.taskId), manager.transcript.value.map { it.taskId })
        assertEquals(ConversationIds.DEFAULT, second.conversationId)
    }

    @Test
    fun newConversationClearsWindowAndKeepsOldTasks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val firstId = manager.task.value!!.conversationId
        manager.newConversation()
        assertNull(manager.task.value)
        assertTrue(manager.transcript.value.isEmpty())
        assertNotEquals(firstId, pointer.currentId())
        assertEquals(1, store.listByConversation(firstId).size)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val second = manager.task.value
        requireNotNull(second)
        assertNotEquals(firstId, second.conversationId)
        assertTrue(manager.transcript.value.isEmpty())
    }

    @Test
    fun newConversationNoopsWhenWindowEmpty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, InMemoryTaskStore(), pointer, this)
        val before = pointer.currentId()
        manager.newConversation()
        assertEquals(before, pointer.currentId())
    }

    @Test
    fun openConversationLoadsFullThreadOntoLatestTurn() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        manager.submit("现在几点了？")
        advanceUntilIdle()
        val oldId = pointer.currentId()
        manager.newConversation()
        manager.openConversation(oldId)
        advanceUntilIdle()
        val live = manager.task.value
        requireNotNull(live)
        assertEquals(oldId, live.conversationId)
        assertEquals(1, manager.transcript.value.size)
        assertNotEquals(live.taskId, manager.transcript.value.single().taskId)
    }

    @Test
    fun openConversationIgnoresResultAfterNewConversation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val firstId = pointer.currentId()
        store.upsert(
            AgentTask(
                taskId = "other-1",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "other-thread",
            ),
        )
        manager.openConversation("other-thread")
        manager.newConversation()
        advanceUntilIdle()
        assertNull(manager.task.value)
        assertTrue(manager.transcript.value.isEmpty())
        assertNotEquals(firstId, pointer.currentId())
        assertNotEquals("other-thread", pointer.currentId())
    }

    @Test
    fun interruptedOtherThreadDoesNotEnterCurrentTranscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        store.upsert(
            AgentTask(
                taskId = "mine",
                input = "我现在手机还有多少电？",
                status = TaskStatus.COMPLETED,
                finalAnswer = "63%",
                conversationId = ConversationIds.DEFAULT,
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "theirs",
                input = "旁路",
                status = TaskStatus.THINKING,
                conversationId = "other-thread",
            ),
        )
        val recovered = recoverInterrupted(store)
        requireNotNull(recovered)
        assertEquals("other-thread", recovered.conversationId)
        assertEquals(TaskStatus.FAILED, recovered.status)
        val manager = manager(dispatcher, store, pointer, this)
        if (recovered.conversationId == pointer.currentId()) {
            manager.seed(recovered)
        }
        manager.reloadTranscript()
        assertNull(manager.task.value)
        assertEquals(listOf("mine"), manager.transcript.value.map { it.taskId })
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
    }

    @Test
    fun retrySubmitKeepsCurrentConversationId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer("thread-a")
        val manager = manager(dispatcher, store, pointer, this)
        manager.seed(
            AgentTask(
                taskId = "failed",
                input = "我现在手机还有多少电？",
                status = TaskStatus.FAILED,
                lastError = "网络失败",
                conversationId = "thread-a",
            ),
        )
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val retried = manager.task.value
        requireNotNull(retried)
        assertNotEquals("failed", retried.taskId)
        assertEquals("thread-a", retried.conversationId)
    }

    @Test
    fun busyNewConversationDoesNotSwitch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, InMemoryTaskStore(), pointer, this)
        manager.seed(
            AgentTask(taskId = "live", input = "进行中", status = TaskStatus.THINKING),
        )
        val before = pointer.currentId()
        manager.newConversation()
        assertEquals(before, pointer.currentId())
        assertEquals("live", manager.task.value?.taskId)
    }

    @Test
    fun deleteConversationRemovesExtraAndKeepsDefault() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "d1",
                input = "默认",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = ConversationIds.DEFAULT,
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "e1",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "extra-thread",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "e2",
                input = "旁路2",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "extra-thread",
            ),
        )
        titles.setTitle("extra-thread", "工作")
        manager.reloadTranscript()
        manager.deleteConversation("extra-thread")
        advanceUntilIdle()
        assertTrue(store.listByConversation("extra-thread").isEmpty())
        assertEquals(listOf("d1"), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
        assertTrue(titles.titles().isEmpty())
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
        assertEquals(listOf("d1"), manager.transcript.value.map { it.taskId })
    }

    @Test
    fun deleteCurrentConversationSwitchesToDefault() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val defaultTaskId = manager.task.value!!.taskId
        manager.newConversation()
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val extraId = pointer.currentId()
        assertNotEquals(ConversationIds.DEFAULT, extraId)
        titles.setTitle(extraId, "临时")
        manager.deleteConversation(extraId)
        advanceUntilIdle()
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
        assertTrue(store.listByConversation(extraId).isEmpty())
        assertTrue(titles.titles().isEmpty())
        assertEquals(defaultTaskId, manager.task.value?.taskId)
        assertEquals(ConversationIds.DEFAULT, manager.task.value?.conversationId)
        assertTrue(manager.transcript.value.isEmpty())
    }

    @Test
    fun deleteOtherConversationDoesNotSwitchCurrent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "keep1",
                input = "保留",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "keep-thread",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "drop1",
                input = "丢掉",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "drop-thread",
            ),
        )
        titles.setTitle("keep-thread", "工作")
        titles.setTitle("drop-thread", "临时")
        manager.openConversation("keep-thread")
        advanceUntilIdle()
        manager.deleteConversation("drop-thread")
        advanceUntilIdle()
        assertTrue(store.listByConversation("drop-thread").isEmpty())
        assertEquals(listOf("keep1"), store.listByConversation("keep-thread").map { it.taskId })
        assertEquals(mapOf("keep-thread" to "工作"), titles.titles())
        assertEquals("keep-thread", pointer.currentId())
        assertEquals("keep1", manager.task.value?.taskId)
        assertEquals("keep-thread", manager.task.value?.conversationId)
    }

    @Test
    fun busyDeleteConversationDoesNotChangeStore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "other",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "other-thread",
            ),
        )
        titles.setTitle("other-thread", "工作")
        manager.seed(
            AgentTask(taskId = "live", input = "进行中", status = TaskStatus.THINKING),
        )
        manager.deleteConversation("other-thread")
        advanceUntilIdle()
        assertEquals(listOf("other"), store.listByConversation("other-thread").map { it.taskId })
        assertEquals(mapOf("other-thread" to "工作"), titles.titles())
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
        assertEquals("live", manager.task.value?.taskId)
    }

    @Test
    fun deleteDefaultConversationIsNoop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "d1",
                input = "默认",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = ConversationIds.DEFAULT,
            ),
        )
        titles.setTitle(ConversationIds.DEFAULT, "家里")
        manager.deleteConversation(ConversationIds.DEFAULT)
        manager.deleteConversation("")
        advanceUntilIdle()
        assertEquals(listOf("d1"), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
        assertEquals(mapOf(ConversationIds.DEFAULT to "家里"), titles.titles())
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
    }

    @Test
    fun busyOpenConversationDoesNotSwitch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        store.upsert(
            AgentTask(
                taskId = "other",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "other-thread",
            ),
        )
        manager.seed(
            AgentTask(taskId = "live", input = "进行中", status = TaskStatus.THINKING),
        )
        val before = pointer.currentId()
        manager.openConversation("other-thread")
        advanceUntilIdle()
        assertEquals(before, pointer.currentId())
        assertEquals("live", manager.task.value?.taskId)
    }

    @Test
    fun deleteTaskBusyDoesNotChangeStore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "other",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "other-thread",
            ),
        )
        titles.setTitle("other-thread", "工作")
        manager.seed(
            AgentTask(taskId = "live", input = "进行中", status = TaskStatus.THINKING),
        )
        assertNull(manager.deleteTask("other"))
        advanceUntilIdle()
        assertEquals(listOf("other"), store.listByConversation("other-thread").map { it.taskId })
        assertEquals(mapOf("other-thread" to "工作"), titles.titles())
        assertEquals("live", manager.task.value?.taskId)
    }

    @Test
    fun deleteTaskLastExtraSwitchesToDefault() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "d1",
                input = "默认",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = ConversationIds.DEFAULT,
            ),
        )
        manager.reloadTranscript()
        manager.newConversation()
        val extraId = pointer.currentId()
        store.upsert(
            AgentTask(
                taskId = "e1",
                input = "旁路",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = extraId,
            ),
        )
        titles.setTitle(extraId, "工作")
        manager.openConversation(extraId)
        advanceUntilIdle()
        manager.deleteTask("e1")
        advanceUntilIdle()
        assertTrue(store.listByConversation(extraId).isEmpty())
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
        assertTrue(titles.titles().isEmpty())
        assertEquals("d1", manager.task.value?.taskId)
        assertEquals(ConversationIds.DEFAULT, manager.task.value?.conversationId)
    }

    @Test
    fun deleteTaskLastSiblingExtraDoesNotSwitchCurrent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "keep1",
                input = "保留",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "keep-thread",
            ),
        )
        store.upsert(
            AgentTask(
                taskId = "drop1",
                input = "丢掉",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = "drop-thread",
            ),
        )
        titles.setTitle("keep-thread", "工作")
        titles.setTitle("drop-thread", "临时")
        manager.openConversation("keep-thread")
        advanceUntilIdle()
        manager.deleteTask("drop1")
        advanceUntilIdle()
        assertTrue(store.listByConversation("drop-thread").isEmpty())
        assertEquals(listOf("keep1"), store.listByConversation("keep-thread").map { it.taskId })
        assertEquals(mapOf("keep-thread" to "工作"), titles.titles())
        assertEquals("keep-thread", pointer.currentId())
        assertEquals("keep1", manager.task.value?.taskId)
        assertEquals("keep-thread", manager.task.value?.conversationId)
    }

    @Test
    fun deleteTaskDefaultEmptiedStaysDefault() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val titles = InMemoryConversationTitles()
        val manager = manager(dispatcher, store, pointer, this, titles)
        store.upsert(
            AgentTask(
                taskId = "d1",
                input = "默认",
                status = TaskStatus.COMPLETED,
                finalAnswer = "好了",
                conversationId = ConversationIds.DEFAULT,
            ),
        )
        titles.setTitle(ConversationIds.DEFAULT, "家里")
        manager.openConversation(ConversationIds.DEFAULT)
        advanceUntilIdle()
        manager.deleteTask("d1")
        advanceUntilIdle()
        assertTrue(store.listByConversation(ConversationIds.DEFAULT).isEmpty())
        assertEquals(ConversationIds.DEFAULT, pointer.currentId())
        assertEquals(mapOf(ConversationIds.DEFAULT to "家里"), titles.titles())
        assertNull(manager.task.value)
        assertTrue(manager.transcript.value.isEmpty())
    }

    @Test
    fun deleteTaskCurrentSeedsRemaining() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val first = manager.task.value
        requireNotNull(first)
        manager.submit("现在几点了？")
        advanceUntilIdle()
        val second = manager.task.value
        requireNotNull(second)
        manager.deleteTask(second.taskId)
        advanceUntilIdle()
        assertEquals(first.taskId, manager.task.value?.taskId)
        assertTrue(manager.transcript.value.none { it.taskId == second.taskId })
        assertEquals(listOf(first.taskId), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
    }

    @Test
    fun deleteTaskPastTurnReloadsTranscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryTaskStore()
        val pointer = InMemoryConversationPointer()
        val manager = manager(dispatcher, store, pointer, this)
        manager.submit("我现在手机还有多少电？")
        advanceUntilIdle()
        val first = manager.task.value
        requireNotNull(first)
        manager.submit("现在几点了？")
        advanceUntilIdle()
        val second = manager.task.value
        requireNotNull(second)
        manager.deleteTask(first.taskId)
        advanceUntilIdle()
        assertEquals(second.taskId, manager.task.value?.taskId)
        assertTrue(manager.transcript.value.none { it.taskId == first.taskId })
        assertEquals(listOf(second.taskId), store.listByConversation(ConversationIds.DEFAULT).map { it.taskId })
    }

    private fun manager(
        dispatcher: CoroutineDispatcher,
        store: InMemoryTaskStore,
        pointer: InMemoryConversationPointer,
        scope: CoroutineScope,
        titles: ConversationTitles? = null,
    ) = TaskManager(
        loopEngine = LoopEngine(
            llm = FakeLlmProvider(),
            tools = mapOf("battery" to FakeBatteryTool()),
            dispatcher = dispatcher,
            stepDelayMs = 0,
            taskStore = store,
        ),
        dispatcher = dispatcher,
        scope = scope,
        taskStore = store,
        conversation = pointer,
        titles = titles,
    )
}
