package com.dougie.core.llm

import com.dougie.core.model.AgentTask
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentMeta
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptAssemblerTest {
    @Test
    fun identityIsChineseDougieWithoutToolNames() {
        val prefix = ChatPromptAssembler.systemPrefix(AgentTask(taskId = "t1", input = "你是什么模型"))
        assertEquals(ChatPromptAssembler.IDENTITY, prefix)
        assertTrue(prefix.contains("你是 Dougie"))
        assertTrue(prefix.contains("本地优先"))
        assertTrue(prefix.contains("用中文回答"))
        IDENTITY_TOOL_NAMES.forEach { name ->
            assertTrue(!prefix.contains(name))
        }
        assertTrue(!prefix.contains("data:image"))
        assertTrue(!prefix.contains("base64"))
    }

    @Test
    fun prefixIncludesLegacyAttachedCaptureMetadataWithoutPixels() {
        val prefix = ChatPromptAssembler.systemPrefix(
            AgentTask(
                taskId = "t-cap",
                input = "匹配一下",
                attachedCaptureId = "cap1",
                attachedWidth = 720,
                attachedHeight = 1584,
            ),
        )
        assertTrue(prefix.startsWith(ChatPromptAssembler.IDENTITY))
        assertTrue(prefix.contains("capture_id=cap1"))
        assertTrue(prefix.contains("720x1584"))
        assertTrue(prefix.contains("screen_match"))
        assertTrue(!prefix.contains("data:image"))
        assertTrue(!prefix.contains("base64"))
        assertTrue(!prefix.contains(CLIPBOARD_SECRET))
    }

    @Test
    fun prefixIncludesKnownFactsAndAttachmentMetadataWithoutPixels() {
        val prefix = ChatPromptAssembler.systemPrefix(
            AgentTask(
                taskId = "t-ctx",
                input = "这是什么",
                retrievedMemories = listOf(
                    MemoryEntry(
                        id = "m1",
                        content = "我叫小明，住在上海",
                        source = "task-0",
                        confidence = 0.8f,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
                attachments = listOf(
                    AttachmentMeta("cap1", AttachmentKind.SCREEN, 720, 1584),
                    AttachmentMeta("g1", AttachmentKind.GALLERY, 800, 600),
                ),
            ),
        )
        assertTrue(prefix.startsWith(ChatPromptAssembler.IDENTITY))
        assertTrue(prefix.contains("Known facts"))
        assertTrue(prefix.contains("我叫小明，住在上海"))
        assertTrue(prefix.contains("kind=screen"))
        assertTrue(prefix.contains("cap1"))
        assertTrue(prefix.contains("720x1584"))
        assertTrue(prefix.contains("kind=gallery"))
        assertTrue(prefix.contains("g1"))
        assertTrue(prefix.contains("800x600"))
        assertTrue(prefix.contains("Pixels stay on device"))
        assertTrue(!prefix.contains("data:image"))
        assertTrue(!prefix.contains("base64"))
        assertTrue(!prefix.contains("\"gray\""))
        assertTrue(!prefix.contains(CLIPBOARD_SECRET))
        IDENTITY_TOOL_NAMES.filter { it != "screen_match" && it != "screen_capture" }.forEach { name ->
            assertTrue(!prefix.contains(name))
        }
    }

    @Test
    fun localPromptIsPrefixBlankLineUserAndToolTrace() {
        val task = AgentTask(
            taskId = "t-local",
            input = "你是什么模型",
            retrievedMemories = listOf(
                MemoryEntry(
                    id = "m1",
                    content = "我叫小明，住在上海",
                    source = "task-0",
                    confidence = 0.8f,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            attachments = listOf(
                AttachmentMeta("cap1", AttachmentKind.SCREEN, 720, 1584),
            ),
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "c1",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = """{"iso":"2026-09-06T12:00:00"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        )
        val prompt = ChatPromptAssembler.localPrompt(task)
        val prefix = ChatPromptAssembler.systemPrefix(task)
        assertEquals(
            prefix + "\n\n" + task.input + "\n" + "time: {\"iso\":\"2026-09-06T12:00:00\"}",
            prompt,
        )
        assertTrue(prompt.contains("Known facts"))
        assertTrue(prompt.contains("kind=screen"))
        assertTrue(prompt.contains("你是什么模型"))
        assertTrue(!prompt.contains(CLIPBOARD_SECRET))
        assertTrue(!prompt.contains("data:image"))
    }

    companion object {
        private const val CLIPBOARD_SECRET = "剪贴板里的密码是hunter2"
        private val IDENTITY_TOOL_NAMES = listOf(
            "battery",
            "time",
            "calendar_query",
            "calendar_create",
            "clipboard_read",
            "clipboard_write",
            "location",
            "screen_capture",
            "screen_match",
            "app_intent",
        )
    }
}
