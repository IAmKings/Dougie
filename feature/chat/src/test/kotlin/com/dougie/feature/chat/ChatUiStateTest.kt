package com.dougie.feature.chat

import com.dougie.core.model.AgentTask
import com.dougie.core.model.ConversationHit
import com.dougie.core.model.MemoryEntry
import com.dougie.core.model.RiskLevel
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.ToolTraceEntry
import com.dougie.core.model.ToolTraceStatus
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun preparingShowsUserThenThinkingWithLoopNumber() {
        listOf(TaskStatus.PREPARING, TaskStatus.THINKING).forEach { status ->
            val state = AgentTask(taskId = "t", input = BATTERY_EXAMPLE, status = status)
                .toChatUiState()
            assertEquals(
                listOf("user", "thinking-1"),
                state.items.map { it.kind() },
            )
            val thinking = state.items[1] as ChatItem.Thinking
            assertEquals(1, thinking.loopNumber)
            assertEquals(true, thinking.live)
        }
    }

    @Test
    fun completedShowsUserThinkingToolChainThenFinal() {
        val tools = (1..3).map { n ->
            ToolTraceEntry(
                toolCallId = "battery-$n",
                toolName = "battery",
                argsSummary = "{}",
                resultJson = """{"battery_percent":63,"charging":true}""",
                status = ToolTraceStatus.SUCCESS,
            )
        }
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            loopCount = 3,
            toolTrace = tools,
            finalAnswer = "你现在的手机电量是 63%。",
        ).toChatUiState()

        assertEquals(
            listOf(
                "user",
                "thinking-1",
                "tool-battery-1",
                "thinking-2",
                "tool-battery-2",
                "thinking-3",
                "tool-battery-3",
                "agent",
            ),
            state.items.map { it.kind() },
        )
        assertTrue(state.inputEnabled)
        assertEquals(false, state.canCancel)
        assertTrue((state.items.last() as ChatItem.AgentMessage).text.contains("63"))
        assertEquals(emptyList<String>(), (state.items.last() as ChatItem.AgentMessage).memorySources)
        assertEquals(false, state.canRetry)
        assertEquals(true, state.canSpeakReply)
        val past = state.items.filterIsInstance<ChatItem.Thinking>()
        assertEquals(listOf(false, false, false), past.map { it.live })
    }

    @Test
    fun afterToolSuccessLiveThinkingIsOnlyTheNextLoop() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            loopCount = 1,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "battery-1",
                    toolName = "battery",
                    argsSummary = "{}",
                    resultJson = """{"battery_percent":63,"charging":true}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
        ).toChatUiState()
        val chips = state.items.filterIsInstance<ChatItem.Thinking>()
        assertEquals(listOf(1, 2), chips.map { it.loopNumber })
        assertEquals(listOf(false, true), chips.map { it.live })
    }

    @Test
    fun completedWithRetrievedMemoriesPutsUniqueSourcesOnFinalAnswer() {
        val state = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.COMPLETED,
            finalAnswer = "你叫小明。",
            retrievedMemories = listOf(
                fact("m1", source = "task-0"),
                fact("m2", source = "task-1"),
                fact("m3", source = "task-0"),
                fact("m4", source = "  "),
            ),
        ).toChatUiState()
        val message = state.items.last() as ChatItem.AgentMessage
        assertEquals("你叫小明。", message.text)
        assertEquals(listOf("task-0", "task-1"), message.memorySources)

        val blankOnly = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.COMPLETED,
            finalAnswer = "你叫小明。",
            retrievedMemories = listOf(fact("m1", source = "   ")),
        ).toChatUiState()
        assertEquals(emptyList<String>(), (blankOnly.items.last() as ChatItem.AgentMessage).memorySources)
    }

    @Test
    fun completedMergesMemoryThenConversationCitationSources() {
        val state = AgentTask(
            taskId = "t",
            input = "UNO 项目有哪些关键点？",
            status = TaskStatus.COMPLETED,
            finalAnswer = "本地优先。",
            retrievedMemories = listOf(
                fact("m1", source = "task-0"),
                fact("m2", source = "  "),
                fact("m3", source = "task-0"),
            ),
            retrievedConversationHits = listOf(
                ConversationHit(
                    taskId = "old-uno",
                    conversationId = "window-a",
                    sourceLabel = "工作 · UNO 项目关键点",
                    user = "UNO 项目关键点是本地优先",
                    assistant = "记下了：本地优先。",
                ),
                ConversationHit(
                    taskId = "dup",
                    conversationId = "window-a",
                    sourceLabel = "task-0",
                    user = "重复来源",
                    assistant = "不应再出现",
                ),
                ConversationHit(
                    taskId = "blank",
                    conversationId = "window-b",
                    sourceLabel = "   ",
                    user = "空白来源",
                    assistant = "省略",
                ),
            ),
        ).toChatUiState()
        val message = state.items.last() as ChatItem.AgentMessage
        assertEquals(listOf("task-0", "工作 · UNO 项目关键点"), message.memorySources)

        val past = AgentTask(
            taskId = "past",
            input = "UNO 项目有哪些关键点？",
            status = TaskStatus.COMPLETED,
            finalAnswer = "本地优先。",
            retrievedConversationHits = listOf(
                ConversationHit(
                    taskId = "old-uno",
                    conversationId = "window-a",
                    sourceLabel = "工作 · UNO 项目关键点",
                    user = "UNO 项目关键点是本地优先",
                    assistant = "记下了：本地优先。",
                ),
            ),
        ).toPastChatItems().last() as ChatItem.AgentMessage
        assertEquals(listOf("工作 · UNO 项目关键点"), past.memorySources)
    }

    @Test
    fun completedBlankFinalAnswerHasNoAgentBubble() {
        val state = AgentTask(
            taskId = "t",
            input = TIME_EXAMPLE,
            status = TaskStatus.COMPLETED,
            loopCount = 1,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "time-1",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = """{"iso_local":"2026-08-29T12:00:00+08:00"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
            finalAnswer = "",
        ).toChatUiState()
        assertEquals(
            listOf("user", "thinking-1", "tool-time-1"),
            state.items.map { it.kind() },
        )
        assertTrue(state.inputEnabled)
    }

    @Test
    fun emptyLlmReplyAfterTimeToolShowsRetry() {
        val state = AgentTask(
            taskId = "t",
            input = TIME_EXAMPLE,
            status = TaskStatus.FAILED,
            loopCount = 1,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "time-1",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = """{"iso_local":"2026-08-29T12:00:00+08:00"}""",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
            lastError = UserFacingErrors.LLM_EMPTY_REPLY,
        ).toChatUiState()
        assertTrue(state.canRetry)
        assertTrue((state.items.last() as ChatItem.AgentMessage).text.contains(UserFacingErrors.LLM_EMPTY_REPLY))
    }

    @Test
    fun streamingAndFailedMessagesOmitMemorySources() {
        val memories = listOf(fact("m1", source = "task-0"))
        val hits = listOf(
            ConversationHit(
                taskId = "old-uno",
                conversationId = "window-a",
                sourceLabel = "工作 · UNO 项目关键点",
                user = "UNO 项目关键点是本地优先",
                assistant = "记下了：本地优先。",
            ),
        )
        val streaming = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.THINKING,
            streamingText = "你叫",
            retrievedMemories = memories,
            retrievedConversationHits = hits,
        ).toChatUiState()
        assertEquals(emptyList<String>(), (streaming.items.last() as ChatItem.AgentMessage).memorySources)

        val failed = AgentTask(
            taskId = "t",
            input = "我叫什么",
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.EGRESS_BLOCKED,
            retrievedMemories = memories,
            retrievedConversationHits = hits,
        ).toChatUiState()
        assertEquals(emptyList<String>(), (failed.items.last() as ChatItem.AgentMessage).memorySources)
    }

    @Test
    fun failedShowsUserFacingEgressText() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.EGRESS_BLOCKED,
        ).toChatUiState()
        val message = state.items.last() as ChatItem.AgentMessage
        assertTrue(message.text.contains(UserFacingErrors.EGRESS_BLOCKED))
        assertTrue(state.inputEnabled)
        assertEquals(false, state.canCancel)
        assertTrue(state.canRetry)
        assertEquals(false, state.canSpeakReply)
    }

    @Test
    fun interruptedFailedShowsRetry() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.INTERRUPTED,
        ).toChatUiState()
        assertTrue(state.inputEnabled)
        assertTrue(state.canRetry)
        assertTrue((state.items.last() as ChatItem.AgentMessage).text.contains(UserFacingErrors.INTERRUPTED))
    }

    @Test
    fun thinkingShowsStreamingTextBeforeCompletion() {
        val state = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            streamingText = "你现在的手机",
        ).toChatUiState()
        assertEquals(listOf("user", "thinking-1", "agent"), state.items.map { it.kind() })
        assertEquals("你现在的手机", (state.items.last() as ChatItem.AgentMessage).text)
        assertEquals(false, state.inputEnabled)
        assertEquals(true, state.canCancel)
        assertEquals(false, state.canSpeakReply)
    }

    @Test
    fun terminalAgentMessagesShowDurationOnlyWhenBothTimestampsPresent() {
        val completed = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "你现在的手机电量是 63%。",
            startedAt = 0L,
            endedAt = 3_000L,
        ).toChatUiState()
        val completedMessage = completed.items.last() as ChatItem.AgentMessage
        assertEquals("3秒", completedMessage.durationLabel)
        assertEquals("你现在的手机电量是 63%。", completedMessage.text)

        val failed = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.EGRESS_BLOCKED,
            startedAt = 1_000L,
            endedAt = 3_000L,
        ).toChatUiState()
        assertEquals("2秒", (failed.items.last() as ChatItem.AgentMessage).durationLabel)

        val streaming = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            streamingText = "你现在的手机",
            startedAt = 0L,
        ).toChatUiState()
        assertNull((streaming.items.last() as ChatItem.AgentMessage).durationLabel)

        val streamingWithEndedAt = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.THINKING,
            streamingText = "你现在的手机",
            startedAt = 0L,
            endedAt = 3_000L,
        ).toChatUiState()
        assertNull((streamingWithEndedAt.items.last() as ChatItem.AgentMessage).durationLabel)

        val missingTimestamps = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "你现在的手机电量是 63%。",
        ).toChatUiState()
        assertNull((missingTimestamps.items.last() as ChatItem.AgentMessage).durationLabel)

        val startedOnly = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "你现在的手机电量是 63%。",
            startedAt = 0L,
        ).toChatUiState()
        assertNull((startedOnly.items.last() as ChatItem.AgentMessage).durationLabel)

        val endedOnly = AgentTask(
            taskId = "t",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "你现在的手机电量是 63%。",
            endedAt = 3_000L,
        ).toChatUiState()
        assertNull((endedOnly.items.last() as ChatItem.AgentMessage).durationLabel)

        val pastCompleted = AgentTask(
            taskId = "p",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "记下了。",
            startedAt = 0L,
            endedAt = 3_000L,
        ).toPastChatItems().last() as ChatItem.AgentMessage
        assertEquals("3秒", pastCompleted.durationLabel)

        val pastFailed = AgentTask(
            taskId = "p",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.FAILED,
            lastError = UserFacingErrors.INTERRUPTED,
            startedAt = 1_000L,
            endedAt = 3_000L,
        ).toPastChatItems().last() as ChatItem.AgentMessage
        assertEquals("2秒", pastFailed.durationLabel)

        val pastMissing = AgentTask(
            taskId = "p",
            input = BATTERY_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "记下了。",
        ).toPastChatItems().last() as ChatItem.AgentMessage
        assertNull(pastMissing.durationLabel)
    }

    @Test
    fun showsToolProgressOnlyWhilePendingOrExecuting() {
        assertTrue(ToolTraceStatus.PENDING.showsToolProgress())
        assertTrue(ToolTraceStatus.EXECUTING.showsToolProgress())
        assertFalse(ToolTraceStatus.SUCCESS.showsToolProgress())
        assertFalse(ToolTraceStatus.FAILED.showsToolProgress())
    }

    @Test
    fun toolCardsUseGenericNamesInsteadOfHardcodedBattery() {
        assertEquals("电池工具", toolDisplayName("battery"))
        assertEquals("时间工具", toolDisplayName("time"))
        assertEquals("截取屏幕", toolDisplayName("screen_capture"))
        assertEquals("念出来", toolDisplayName("speech_output"))
        assertEquals("运行脚本", toolDisplayName("js_eval"))
        assertEquals("运行 Python", toolDisplayName("py_eval"))
        assertEquals("发短信", toolDisplayName("sms_compose"))
        assertEquals("打电话", toolDisplayName("phone_dial"))
        assertEquals("calendar", toolDisplayName("calendar"))
        assertEquals(
            "隔离运行脚本，不读写文件、不上网。确认后才会执行；拒绝则跳过。",
            confirmToolBody("js_eval"),
        )
        assertEquals(
            "按完整脚本运行，结果取最后一次表达式。不读写文件、不上网。确认后才会执行；拒绝则跳过。",
            confirmToolBody("js_eval", com.dougie.core.model.RiskLevel.L4),
        )
        assertEquals(
            "可用沙箱文件处理数据，不能上网或读应用外文件。确认后才会执行；拒绝则跳过。",
            confirmToolBody("py_eval"),
        )
        assertEquals(
            "可用沙箱文件处理数据，不能上网或读应用外文件。确认后才会执行；拒绝则跳过。",
            confirmToolBody("py_eval", com.dougie.core.model.RiskLevel.L4),
        )
        assertEquals(
            "该操作会写入设备数据。确认后才会执行；拒绝则跳过。",
            confirmToolBody("calendar_create"),
        )
        val telecomConfirm = "将打开系统短信或拨号并填入内容，需你再按发送或呼叫。确认后才会打开；拒绝则跳过。"
        assertEquals(telecomConfirm, confirmToolBody("sms_compose"))
        assertEquals(telecomConfirm, confirmToolBody("phone_dial"))
        assertEquals(
            telecomConfirm,
            confirmToolBody("sms_compose", com.dougie.core.model.RiskLevel.L3),
        )
    }

    @Test
    fun awaitingConfirmationShowsConfirmCardAndDisablesInput() {
        val state = AgentTask(
            taskId = "t",
            input = "帮我约明天下午开会",
            status = TaskStatus.AWAITING_CONFIRMATION,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "cal-1",
                    toolName = "calendar_create",
                    argsSummary = """{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""",
                    status = ToolTraceStatus.PENDING,
                    riskLevel = com.dougie.core.model.RiskLevel.L2,
                ),
            ),
        ).toChatUiState()
        assertEquals(listOf("user", "thinking-1", "confirm-cal-1"), state.items.map { it.kind() })
        val card = state.items.last() as ChatItem.ConfirmCard
        assertEquals("calendar_create", card.toolName)
        assertEquals("""{"title":"开会","startIso":"2026-08-18T15:00:00+08:00"}""", card.argsJson)
        assertEquals(com.dougie.core.model.RiskLevel.L2, card.riskLevel)
        assertEquals(false, state.inputEnabled)
        assertEquals(true, state.canCancel)
    }

    @Test
    fun canCancelMatchesBusyIncludingAwaitingConfirmation() {
        listOf(
            TaskStatus.PREPARING,
            TaskStatus.THINKING,
            TaskStatus.TOOL_PENDING,
            TaskStatus.TOOL_EXECUTING,
        ).forEach { status ->
            assertEquals(
                true,
                AgentTask(taskId = "t", input = BATTERY_EXAMPLE, status = status)
                    .toChatUiState().canCancel,
            )
        }
        assertEquals(
            true,
            AgentTask(
                taskId = "t",
                input = "约开会",
                status = TaskStatus.AWAITING_CONFIRMATION,
                toolTrace = listOf(
                    ToolTraceEntry(
                        toolCallId = "cal-1",
                        toolName = "calendar_create",
                        argsSummary = "{}",
                        status = ToolTraceStatus.PENDING,
                        riskLevel = RiskLevel.L2,
                    ),
                ),
            ).toChatUiState().canCancel,
        )
        assertEquals(
            false,
            AgentTask(
                taskId = "t",
                input = BATTERY_EXAMPLE,
                status = TaskStatus.COMPLETED,
                finalAnswer = "好",
            ).toChatUiState().canCancel,
        )
        assertEquals(
            false,
            AgentTask(
                taskId = "t",
                input = BATTERY_EXAMPLE,
                status = TaskStatus.FAILED,
                lastError = UserFacingErrors.CANCELLED,
            ).toChatUiState().canCancel,
        )
        assertEquals(
            false,
            AgentTask(taskId = "t", input = BATTERY_EXAMPLE, status = TaskStatus.IDLE)
                .toChatUiState().canCancel,
        )
        assertEquals(false, (null as AgentTask?).toChatUiState().canCancel)
    }

    @Test
    fun attachmentChipLabelsHaveKindAndSizeNotPixels() {
        assertEquals(
            "屏幕 · 720×1584",
            ChatAttachmentUi("c1", com.dougie.core.model.AttachmentKind.SCREEN, 720, 1584).chipLabel(),
        )
        assertEquals(
            "相册 · 800×600",
            ChatAttachmentUi("g1", com.dougie.core.model.AttachmentKind.GALLERY, 800, 600).chipLabel(),
        )
        assertEquals(
            "拍照 · 640×480",
            ChatAttachmentUi("p1", com.dougie.core.model.AttachmentKind.CAMERA, 640, 480).chipLabel(),
        )
    }

    @Test
    fun appendVoiceTranscriptJoinsWithSpaceAndIgnoresBlankSpoken() {
        assertEquals("查电量", appendVoiceTranscript("", " 查电量 "))
        assertEquals("查电量 现在几点", appendVoiceTranscript("查电量", "现在几点"))
        assertEquals("已有", appendVoiceTranscript("已有", "  "))
    }

    @Test
    fun insertVoiceTranscriptUsesSelectionAndLeavesCursorAfterSpoken() {
        val empty = insertVoiceTranscript("", 0, 0, " 查电量 ")
        assertEquals("查电量", empty.text)
        assertEquals(3, empty.cursor)

        val atEnd = insertVoiceTranscript("查电量", 3, 3, "现在几点")
        assertEquals("查电量 现在几点", atEnd.text)
        assertEquals("查电量 现在几点".length, atEnd.cursor)

        val middle = insertVoiceTranscript("你好世界", 2, 2, "啊")
        assertEquals("你好 啊 世界", middle.text)
        assertEquals("你好 啊".length, middle.cursor)

        val replace = insertVoiceTranscript("查电量 现在几点", 0, 3, "看日历")
        assertEquals("看日历 现在几点", replace.text)
        assertEquals("看日历".length, replace.cursor)

        val blank = insertVoiceTranscript("已有", 1, 1, "  ")
        assertEquals("已有", blank.text)
        assertEquals(1, blank.cursor)
    }

    @Test
    fun voiceOverlayStatusMatchesRecordingAndLocalRecognizeCopy() {
        assertEquals("正在录音", voiceOverlayStatus(holding = true, transcribing = false))
        assertEquals("现在几点", voiceOverlayStatus(holding = true, transcribing = false, partial = " 现在几点 "))
        assertEquals("正在录音", voiceOverlayStatus(holding = true, transcribing = false, partial = "  "))
        assertEquals(
            "正在进行本地识别...",
            voiceOverlayStatus(holding = false, transcribing = true, partial = "现在几点"),
        )
        assertEquals("正在进行本地识别...", voiceOverlayStatus(holding = false, transcribing = true))
        assertEquals("", voiceOverlayStatus(holding = false, transcribing = false))
    }

    @Test
    fun attachmentStatusLineShowsSpeakingThenUnavailable() {
        assertEquals("正在播报...", attachmentStatusLine(speakingReply = true, error = null))
        assertEquals(
            "正在播报...",
            attachmentStatusLine(speakingReply = true, error = UserFacingErrors.TTS_REPLY_UNAVAILABLE),
        )
        assertEquals(false, attachmentStatusIsError(speakingReply = true, error = null))
        assertEquals(
            UserFacingErrors.TTS_REPLY_UNAVAILABLE,
            attachmentStatusLine(speakingReply = false, error = UserFacingErrors.TTS_REPLY_UNAVAILABLE),
        )
        assertEquals(
            true,
            attachmentStatusIsError(
                speakingReply = false,
                error = UserFacingErrors.TTS_REPLY_UNAVAILABLE,
            ),
        )
        assertEquals(null, attachmentStatusLine(speakingReply = false, error = null))
    }

    @Test
    fun lastCompletedAgentBubbleShowsSpeakNotRetry() {
        assertEquals(
            true,
            showAgentReplySpeak(isLastItem = true, canSpeakReply = true, ttsReady = true, text = "现在是15点"),
        )
        assertEquals(
            false,
            showAgentReplySpeak(isLastItem = true, canSpeakReply = true, ttsReady = false, text = "现在是15点"),
        )
        assertEquals(false, showAgentReplySpeak(isLastItem = true, canSpeakReply = false, ttsReady = true, text = "你现在的手机"))
        assertEquals(false, showAgentReplySpeak(isLastItem = true, canSpeakReply = false, ttsReady = true, text = "失败"))
        assertEquals(false, showAgentReplySpeak(isLastItem = false, canSpeakReply = true, ttsReady = true, text = "旧回复"))
        assertEquals("播报", agentReplySpeakLabel(speakingReply = false))
        assertEquals("停止播报", agentReplySpeakLabel(speakingReply = true))
        assertEquals(true, attachmentOffersDownload(UserFacingErrors.SPEECH_MODEL_MISSING))
        assertEquals(true, attachmentOffersDownload(UserFacingErrors.TTS_REPLY_UNAVAILABLE))
        assertEquals(false, attachmentOffersDownload(UserFacingErrors.SPEECH_EMPTY))
        assertEquals(true, attachmentOffersPermissionCenter(UserFacingErrors.PERMISSION_DENIED))
        assertEquals(false, attachmentOffersPermissionCenter(UserFacingErrors.TTS_REPLY_UNAVAILABLE))
        assertEquals("去权限中心", GO_PERMISSION_CENTER)
    }

    @Test
    fun pastTurnsOmitToolsAndMergeKeepsLiveLoop() {
        val past = AgentTask(
            taskId = "p",
            input = "我叫小明",
            status = TaskStatus.COMPLETED,
            toolTrace = listOf(
                ToolTraceEntry(
                    toolCallId = "battery-1",
                    toolName = "battery",
                    argsSummary = "{}",
                    resultJson = "{}",
                    status = ToolTraceStatus.SUCCESS,
                ),
            ),
            finalAnswer = "记下了。",
        )
        val live = AgentTask(taskId = "n", input = TIME_EXAMPLE, status = TaskStatus.THINKING)
        val pastItems = past.toPastChatItems()
        assertEquals(listOf("user", "agent"), pastItems.map { it.kind() })
        val merged = mergeChatUiState(live, listOf(past))
        assertEquals(
            listOf("user", "agent", "user", "thinking-1"),
            merged.items.map { it.kind() },
        )
        assertEquals(false, merged.isEmpty)
        assertEquals(false, merged.inputEnabled)
        assertEquals(true, merged.canCancel)
        assertEquals(false, merged.canNewConversation)
        assertEquals(true, mergeChatUiState(past, emptyList()).canNewConversation)
        assertEquals(false, mergeChatUiState(past, emptyList()).canCancel)
    }

    @Test
    fun mergeDropsPastTurnThatIsAlsoLive() {
        val turn = AgentTask(
            taskId = "p",
            input = "我叫小明",
            status = TaskStatus.COMPLETED,
            finalAnswer = "记下了。",
        )
        val keys = mergeChatUiState(turn, listOf(turn)).items.map { it.listKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("p:user", "p:agent"), keys)
    }

    @Test
    fun pastTurnsWithoutLiveStillFillWindow() {
        val past = AgentTask(
            taskId = "p",
            input = "我叫小明",
            status = TaskStatus.COMPLETED,
            finalAnswer = "记下了。",
        )
        val merged = mergeChatUiState(null, listOf(past))
        assertEquals(listOf("p:user", "p:agent"), merged.items.map { it.listKey })
        assertEquals(false, merged.isEmpty)
        assertEquals(true, merged.inputEnabled)
        assertEquals(false, merged.canCancel)
        assertEquals(true, merged.canNewConversation)
        assertEquals(false, merged.canRetry)
        assertEquals(false, merged.canSpeakReply)
    }

    @Test
    fun mergedTurnsHaveUniqueLazyListKeys() {
        val pastOne = AgentTask(
            taskId = "p1",
            input = "我叫小明",
            status = TaskStatus.COMPLETED,
            finalAnswer = "记下了。",
        )
        val pastTwo = AgentTask(
            taskId = "p2",
            input = TIME_EXAMPLE,
            status = TaskStatus.COMPLETED,
            finalAnswer = "现在是中午。",
        )
        val live = AgentTask(taskId = "n", input = BATTERY_EXAMPLE, status = TaskStatus.THINKING)
        val keys = mergeChatUiState(live, listOf(pastOne, pastTwo)).items.map { it.listKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(
            listOf("p1:user", "p1:agent", "p2:user", "p2:agent", "n:user", "n:thinking-1"),
            keys,
        )
    }

    @Test
    fun followChatFeedSkipsUnchangedListAfterBottomNavReturn() {
        assertEquals(
            false,
            shouldFollowChatFeed(
                itemCount = 4,
                firstKey = "p1:user",
                lastAgent = "记下了。",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "记下了。",
            ),
        )
        assertEquals(
            true,
            shouldFollowChatFeed(
                itemCount = 4,
                firstKey = "p1:user",
                lastAgent = "记下了。",
                previousItemCount = 0,
                previousFirstKey = null,
                previousLastAgent = null,
            ),
        )
        assertEquals(
            true,
            shouldFollowChatFeed(
                itemCount = 6,
                firstKey = "p1:user",
                lastAgent = "现在是中午。",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "记下了。",
            ),
        )
        assertEquals(
            true,
            shouldFollowChatFeed(
                itemCount = 2,
                firstKey = "n:user",
                lastAgent = "新窗口。",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "记下了。",
            ),
        )
        assertEquals(
            true,
            shouldFollowChatFeed(
                itemCount = 4,
                firstKey = "p1:user",
                lastAgent = "你现在的手",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "你现在的",
            ),
        )
    }

    @Test
    fun followChatFeedSkipsWhenFocusListKeyPending() {
        assertEquals(
            false,
            shouldFollowChatFeed(
                itemCount = 4,
                firstKey = "n:user",
                lastAgent = "新窗口。",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "记下了。",
                pendingFocusKey = userMessageListKey("old"),
            ),
        )
        assertEquals(
            true,
            shouldFollowChatFeed(
                itemCount = 4,
                firstKey = "n:user",
                lastAgent = "新窗口。",
                previousItemCount = 4,
                previousFirstKey = "p1:user",
                previousLastAgent = "记下了。",
            ),
        )
        assertEquals("t1:user", userMessageListKey("t1"))
    }

    @Test
    fun userBubbleSharedKeyStripsUserSuffix() {
        assertEquals("t1", userBubbleSharedKey(userMessageListKey("t1")))
        assertEquals("t1:thinking-1", userBubbleSharedKey("t1:thinking-1"))
    }

    @Test
    fun firstFrameFullListDoesNotPlayEnter() {
        val items = listOf(
            ChatItem.UserMessage("现在几点了？", listKey = "t:user"),
            ChatItem.Thinking(loopNumber = 1, live = false, listKey = "t:thinking-1"),
            ChatItem.ToolCard(
                entry = ToolTraceEntry(
                    toolCallId = "time-1",
                    toolName = "time",
                    argsSummary = "{}",
                    resultJson = "{}",
                    status = ToolTraceStatus.SUCCESS,
                ),
                listKey = "t:tool-time-1",
            ),
            ChatItem.ConfirmCard(
                toolName = "js_eval",
                argsJson = "{}",
                riskLevel = RiskLevel.L4,
                toolCallId = "c1",
                listKey = "t:confirm-c1",
            ),
            ChatItem.AgentMessage("中午。", durationLabel = "2秒", listKey = "t:agent"),
        )
        val enter = nextChatItemEnter(items, seenKeys = null)
        assertEquals(emptySet<String>(), enter.playKeys)
        assertEquals(
            setOf("t:user", "t:thinking-1", "t:tool-time-1", "t:confirm-c1", "t:agent"),
            enter.seenKeys,
        )
    }

    @Test
    fun firstFrameEmptyThenSendPlaysEnter() {
        val seed = nextChatItemEnter(items = emptyList(), seenKeys = null)
        assertEquals(emptySet<String>(), seed.playKeys)
        assertEquals(emptySet<String>(), seed.seenKeys)

        val sent = nextChatItemEnter(
            items = listOf(ChatItem.UserMessage("你好", listKey = "n:user")),
            seenKeys = seed.seenKeys,
        )
        assertEquals(setOf("n:user"), sent.playKeys)
        assertEquals(setOf("n:user"), sent.seenKeys)
    }

    @Test
    fun emptyListClearsSeenEnterKeys() {
        val enter = nextChatItemEnter(
            items = emptyList(),
            seenKeys = setOf("old:user", "old:agent"),
        )
        assertEquals(emptySet<String>(), enter.playKeys)
        assertEquals(emptySet<String>(), enter.seenKeys)

        val afterClear = nextChatItemEnter(
            items = listOf(ChatItem.UserMessage("你好", listKey = "n:user")),
            seenKeys = enter.seenKeys,
        )
        assertEquals(setOf("n:user"), afterClear.playKeys)
        assertEquals(setOf("n:user"), afterClear.seenKeys)
    }

    @Test
    fun unseenThinkingPlaysEnter() {
        val items = listOf(
            ChatItem.UserMessage("现在几点了？", listKey = "t:user"),
            ChatItem.Thinking(loopNumber = 1, live = true, listKey = "t:thinking-1"),
        )
        val enter = nextChatItemEnter(items, seenKeys = setOf("t:user"))
        assertEquals(setOf("t:thinking-1"), enter.playKeys)
        assertEquals(setOf("t:user", "t:thinking-1"), enter.seenKeys)
    }

    @Test
    fun unseenToolCardPlaysEnterWithoutBubbleOffset() {
        val user = ChatItem.UserMessage("现在几点了？", listKey = "t:user")
        val thinking = ChatItem.Thinking(loopNumber = 1, live = false, listKey = "t:thinking-1")
        val tool = ChatItem.ToolCard(
            entry = ToolTraceEntry(
                toolCallId = "time-1",
                toolName = "time",
                argsSummary = "{}",
                resultJson = "{}",
                status = ToolTraceStatus.SUCCESS,
            ),
            listKey = "t:tool-time-1",
        )
        val confirm = ChatItem.ConfirmCard(
            toolName = "js_eval",
            argsJson = "{}",
            riskLevel = RiskLevel.L4,
            toolCallId = "c1",
            listKey = "t:confirm-c1",
        )
        val agent = ChatItem.AgentMessage("中午。", listKey = "t:agent")
        val enter = nextChatItemEnter(
            items = listOf(user, thinking, tool),
            seenKeys = setOf("t:user", "t:thinking-1"),
        )
        assertEquals(setOf("t:tool-time-1"), enter.playKeys)
        assertFalse(tool.usesBubbleEnter())
        assertFalse(confirm.usesBubbleEnter())
        assertTrue(user.usesBubbleEnter())
        assertTrue(thinking.usesBubbleEnter())
        assertTrue(agent.usesBubbleEnter())
        assertEquals(8, BUBBLE_ENTER_OFFSET_DP)
        assertEquals(150, TOOL_SWITCH_DURATION_MS)
    }

    @Test
    fun newConfirmKeyDoesNotPlayEnter() {
        val items = listOf(
            ChatItem.UserMessage("运行脚本", listKey = "t:user"),
            ChatItem.Thinking(loopNumber = 1, live = false, listKey = "t:thinking-1"),
            ChatItem.ConfirmCard(
                toolName = "js_eval",
                argsJson = "{}",
                riskLevel = RiskLevel.L4,
                toolCallId = "c1",
                listKey = "t:confirm-c1",
            ),
        )
        val enter = nextChatItemEnter(items, seenKeys = setOf("t:user", "t:thinking-1"))
        assertFalse(enter.playKeys.contains("t:confirm-c1"))
        assertEquals(emptySet<String>(), enter.playKeys)
        assertTrue(enter.seenKeys.contains("t:confirm-c1"))
        assertEquals(setOf("t:user", "t:thinking-1", "t:confirm-c1"), enter.seenKeys)
    }

    @Test
    fun chatConfirmCardExtractsLastAndFeedDropsConfirm() {
        val confirm = ChatItem.ConfirmCard(
            toolName = "js_eval",
            argsJson = "{}",
            riskLevel = RiskLevel.L4,
            toolCallId = "c1",
            listKey = "t:confirm-c1",
        )
        val user = ChatItem.UserMessage("运行脚本", listKey = "t:user")
        val thinking = ChatItem.Thinking(loopNumber = 1, live = false, listKey = "t:thinking-1")
        val items = listOf(user, thinking, confirm)
        assertEquals(confirm, chatConfirmCard(items))
        val feed = chatFeedItemsWithoutConfirm(items)
        assertEquals(listOf(user, thinking), feed)
        assertTrue(feed.none { it is ChatItem.ConfirmCard })
        assertEquals(0, feed.indexOfFirst { it.listKey == user.listKey })
        assertEquals(1, feed.lastIndex)
        assertEquals(2, items.lastIndex)
        assertNull(chatConfirmCard(listOf(user, thinking)))
        assertEquals(listOf(user, thinking), chatFeedItemsWithoutConfirm(listOf(user, thinking)))
    }

    @Test
    fun firstConfirmEnterWithExistingKeyDoesNotPlay() {
        val enter = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        assertFalse(enter.play)
        assertTrue(enter.initialized)
        assertEquals("t:confirm-c1", enter.lastKey)
    }

    @Test
    fun initializedNullToNewConfirmKeyPlays() {
        val seeded = nextConfirmEnter(confirmKey = null, initialized = false, lastKey = null)
        assertFalse(seeded.play)
        assertTrue(seeded.initialized)
        assertNull(seeded.lastKey)

        val enter = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertTrue(enter.play)
        assertEquals("t:confirm-c1", enter.lastKey)
    }

    @Test
    fun sameConfirmKeyDoesNotReplay() {
        val seeded = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        val again = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertFalse(again.play)
        assertEquals("t:confirm-c1", again.lastKey)
    }

    @Test
    fun confirmKeyClearedThenNewKeyPlays() {
        val seeded = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        val cleared = nextConfirmEnter(
            confirmKey = null,
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertFalse(cleared.play)
        assertNull(cleared.lastKey)

        val next = nextConfirmEnter(
            confirmKey = "t:confirm-c2",
            initialized = cleared.initialized,
            lastKey = cleared.lastKey,
        )
        assertTrue(next.play)
        assertEquals("t:confirm-c2", next.lastKey)
    }

    @Test
    fun firstConfirmExitDoesNotPlay() {
        val empty = nextConfirmExit(
            confirmKey = null,
            initialized = false,
            lastKey = null,
        )
        assertFalse(empty.play)
        assertFalse(empty.keepLast)

        val existing = nextConfirmExit(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        assertFalse(existing.play)
        assertFalse(existing.keepLast)

        val leftoverLastKey = nextConfirmExit(
            confirmKey = null,
            initialized = false,
            lastKey = "t:confirm-c1",
        )
        assertFalse(leftoverLastKey.play)
        assertFalse(leftoverLastKey.keepLast)
        assertEquals(250, CONFIRM_OVERLAY_DURATION_MS)
    }

    @Test
    fun confirmPresentToAbsentPlaysExitAndKeepsLast() {
        val seeded = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        val exit = nextConfirmExit(
            confirmKey = null,
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertTrue(exit.play)
        assertTrue(exit.keepLast)
    }

    @Test
    fun sameConfirmKeyStillPresentDoesNotPlayExit() {
        val seeded = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        val exit = nextConfirmExit(
            confirmKey = "t:confirm-c1",
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertFalse(exit.play)
        assertFalse(exit.keepLast)
    }

    @Test
    fun nullAfterLastKeyAlreadyNullDoesNotPlayExit() {
        val seeded = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = false,
            lastKey = null,
        )
        val cleared = nextConfirmEnter(
            confirmKey = null,
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        val exit = nextConfirmExit(
            confirmKey = null,
            initialized = cleared.initialized,
            lastKey = cleared.lastKey,
        )
        assertFalse(exit.play)
        assertFalse(exit.keepLast)
    }

    @Test
    fun nullThenNewConfirmKeyIsEnterNotExit() {
        val seeded = nextConfirmEnter(confirmKey = null, initialized = false, lastKey = null)
        val enter = nextConfirmEnter(
            confirmKey = "t:confirm-c1",
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertTrue(enter.play)
        val exit = nextConfirmExit(
            confirmKey = "t:confirm-c1",
            initialized = seeded.initialized,
            lastKey = seeded.lastKey,
        )
        assertFalse(exit.play)
        assertFalse(exit.keepLast)
    }

    @Test
    fun sameAgentKeyDoesNotReplayEnter() {
        val seen = setOf("t:user", "t:agent")
        val streaming = listOf(
            ChatItem.UserMessage("现在几点了？", listKey = "t:user"),
            ChatItem.AgentMessage("你现在的", listKey = "t:agent"),
        )
        val streamEnter = nextChatItemEnter(streaming, seenKeys = seen)
        assertEquals(emptySet<String>(), streamEnter.playKeys)
        assertEquals(seen, streamEnter.seenKeys)

        val completed = listOf(
            ChatItem.UserMessage("现在几点了？", listKey = "t:user"),
            ChatItem.AgentMessage("中午。", durationLabel = "2秒", listKey = "t:agent"),
        )
        val finalEnter = nextChatItemEnter(completed, seenKeys = streamEnter.seenKeys)
        assertEquals(emptySet<String>(), finalEnter.playKeys)
        assertEquals(seen, finalEnter.seenKeys)
    }

    private fun fact(id: String, source: String) = MemoryEntry(
        id = id,
        content = "我叫小明，住在上海",
        source = source,
        confidence = 0.8f,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun ChatItem.kind(): String = when (this) {
        is ChatItem.UserMessage -> "user"
        is ChatItem.Thinking -> "thinking-$loopNumber"
        is ChatItem.ToolCard -> "tool-${entry.toolCallId}"
        is ChatItem.ConfirmCard -> "confirm-$toolCallId"
        is ChatItem.AgentMessage -> "agent"
    }
}
