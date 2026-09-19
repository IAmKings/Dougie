package com.dougie.app

import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.IntentEval
import com.dougie.core.tool.IntentPredItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppIntentRuleEEvalTest {
    @Test
    fun playAndSideloadPackageNamesAppearInRunAs() {
        val report = IntentEval.ruleEReport(
            listOf(
                IntentPredItem("h001", UTTERANCE, "query_time", "query_time", 12L),
            ),
        )
        val play = formatRuleEMessage(PLAY_ID, report)
        val sideload = formatRuleEMessage(SIDELOAD_ID, report)
        assertEquals(
            "adb exec-out run-as $PLAY_ID cat files/${IntentEval.PREDICTIONS_RELATIVE}",
            adbPullHint(PLAY_ID),
        )
        assertEquals(
            "adb exec-out run-as $SIDELOAD_ID cat files/${IntentEval.PREDICTIONS_RELATIVE}",
            adbPullHint(SIDELOAD_ID),
        )
        assertTrue(play.contains("ruleEPassed="))
        assertTrue(play.contains(IntentEval.PREDICTIONS_RELATIVE))
        assertTrue(play.contains(adbPullHint(PLAY_ID)))
        assertFalse(play.contains("sideload"))
        assertTrue(sideload.contains(adbPullHint(SIDELOAD_ID)))
        assertNoLeak(play)
        assertNoLeak(sideload)
        assertNoLeak(report.toString())
    }

    @Test
    fun lastFormatsValidJsonlWithoutUtterance() {
        val tmp = File.createTempFile("intent-pred", ".jsonl")
        tmp.deleteOnExit()
        tmp.writeText(VALID_JSONL)
        val message = AppIntentRuleEEval.last(tmp, PLAY_ID)
        assertNotNull(message)
        assertTrue(message!!.contains("ruleEPassed="))
        assertTrue(message.contains("nLabeled=3"))
        assertTrue(message.contains(IntentEval.PREDICTIONS_RELATIVE))
        assertTrue(message.contains(adbPullHint(PLAY_ID)))
        assertNoLeak(message)
    }

    @Test
    fun lastReturnsNullWhenMissingEmptyOrBad() {
        val missing = File("/no/such/eval/intent/predictions.jsonl")
        assertNull(AppIntentRuleEEval.last(missing, PLAY_ID))
        assertFalse(UserFacingErrors.INTENT_FAILED == AppIntentRuleEEval.last(missing, PLAY_ID))
        val empty = File.createTempFile("intent-empty", ".jsonl")
        empty.deleteOnExit()
        empty.writeText("")
        assertNull(AppIntentRuleEEval.last(empty, PLAY_ID))
        assertFalse(UserFacingErrors.INTENT_FAILED == AppIntentRuleEEval.last(empty, PLAY_ID))
        val blank = File.createTempFile("intent-blank", ".jsonl")
        blank.deleteOnExit()
        blank.writeText("\n\n")
        assertNull(AppIntentRuleEEval.last(blank, PLAY_ID))
        val bad = File.createTempFile("intent-bad", ".jsonl")
        bad.deleteOnExit()
        bad.writeText("{$UTTERANCE")
        assertNull(AppIntentRuleEEval.last(bad, PLAY_ID))
        assertFalse(UserFacingErrors.INTENT_FAILED == AppIntentRuleEEval.last(bad, PLAY_ID))
        assertNull(AppIntentRuleEEval.last(requireNotNull(empty.parentFile), PLAY_ID))
    }

    private fun assertNoLeak(text: String) {
        assertFalse(text.contains(UTTERANCE))
        assertFalse(text.contains("还剩多少电"))
        assertFalse(text.contains("帮我查一下日程"))
        assertFalse(text.contains("query_time"))
        assertFalse(text.contains("query_battery"))
        assertFalse(text.contains("query_calendar"))
        assertFalse(text.contains("已达标"))
    }

    companion object {
        private const val PLAY_ID = "com.dougie.app"
        private const val SIDELOAD_ID = "com.dougie.app.sideload"
        private const val UTTERANCE = "现在几点"
        private const val VALID_JSONL =
            """{"id":"h001","text":"现在几点","goldIntent":"query_time","predictedIntent":"query_time","latencyMs":12}""" +
                "\n" +
                """{"id":"h002","text":"还剩多少电","goldIntent":"query_battery","predictedIntent":"query_battery","latencyMs":9}""" +
                "\n" +
                """{"id":"h003","text":"帮我查一下日程","goldIntent":"query_calendar"}""" +
                "\n"
    }
}
