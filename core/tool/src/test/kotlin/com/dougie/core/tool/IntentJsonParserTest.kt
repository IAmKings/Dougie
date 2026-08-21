package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentJsonParserTest {
    @Test
    fun parserReadsWrappedJson() {
        val hit = IntentJsonParser.parse(
            """here {"intent":"query_time","slots":{"when":"now"},"route":"time","confidence":0.91} thanks""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("time", hit.route)
        assertEquals("now", hit.slots["when"])
        assertEquals(0.91, hit.confidence, 0.0)
    }

    @Test
    fun parserReadsJsonAfterThinkBlock() {
        val hit = IntentJsonParser.parse(
            """<think>plan</think>{"intent":"query_time","slots":{},"route":"time","confidence":0.7}""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("time", hit.route)
    }

    @Test
    fun parserFillsRouteFromIntentWhenMissing() {
        val hit = IntentJsonParser.parse(
            """{"intent":"query_time","slots":[],"confidence":1}""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("query_time", hit.route)
        assertTrue(hit.slots.isEmpty())
        assertEquals(1.0, hit.confidence, 0.0)
    }

    @Test
    fun parserRejectsNonJson() {
        try {
            IntentJsonParser.parse("not json")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INTENT_FAILED, e.userMessage)
        }
    }

    @Test
    fun parserSkipsInvalidBraceGroupThenReadsJson() {
        val hit = IntentJsonParser.parse(
            """note {not json} then {"intent":"query_time","slots":{},"route":"time","confidence":0.8}""",
        )
        assertEquals("query_time", hit.intent)
        assertEquals("time", hit.route)
        assertEquals(0.8, hit.confidence, 0.0)
    }
}
