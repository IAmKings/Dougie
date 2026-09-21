package com.dougie.app

import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.KokoroEval
import com.dougie.core.tool.KokoroEvalItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppKokoroRuleBEvalTest {
    @Test
    fun playAndSideloadPackageNamesAppearInRunAs() {
        val report = KokoroEval.report(
            listOf(
                KokoroEvalItem("g001", UTTERANCE, 500L, 1000L, 1, null),
            ),
        )
        val play = formatRuleBMessage(PLAY_ID, report)
        val sideload = formatRuleBMessage(SIDELOAD_ID, report)
        assertEquals(
            "adb exec-out run-as $PLAY_ID cat files/${KokoroEval.MANIFEST_RELATIVE}",
            adbPullHint(PLAY_ID, KokoroEval.MANIFEST_RELATIVE),
        )
        assertEquals(
            "adb exec-out run-as $SIDELOAD_ID cat files/${KokoroEval.MANIFEST_RELATIVE}",
            adbPullHint(SIDELOAD_ID, KokoroEval.MANIFEST_RELATIVE),
        )
        assertTrue(play.contains("ruleBPassed="))
        assertTrue(play.contains(KokoroEval.MANIFEST_RELATIVE))
        assertTrue(play.contains(adbPullHint(PLAY_ID, KokoroEval.MANIFEST_RELATIVE)))
        assertFalse(play.contains("sideload"))
        assertTrue(sideload.contains(adbPullHint(SIDELOAD_ID, KokoroEval.MANIFEST_RELATIVE)))
        assertNoLeak(play)
        assertNoLeak(sideload)
        assertNoLeak(report.toString())
    }

    @Test
    fun lastFormatsValidJsonlWithoutUtteranceAndWithoutNaturalness() {
        val tmp = File.createTempFile("kokoro-rtf", ".jsonl")
        tmp.deleteOnExit()
        tmp.writeText(SCORED_JSONL)
        val message = AppKokoroRuleBEval.last(tmp, PLAY_ID)
        assertNotNull(message)
        assertTrue(message!!.contains("ruleBPassed="))
        assertTrue(message.contains("nLabeled=5"))
        assertTrue(message.contains("nScored=5"))
        assertTrue(message.contains("naturalnessApplied=false"))
        assertTrue(message.contains(KokoroEval.MANIFEST_RELATIVE))
        assertTrue(message.contains(adbPullHint(PLAY_ID, KokoroEval.MANIFEST_RELATIVE)))
        assertNoLeak(message)
    }

    @Test
    fun lastReturnsNullWhenMissingEmptyOrBad() {
        val missing = File("/no/such/eval/tts/kokoro-rtf.jsonl")
        assertNull(AppKokoroRuleBEval.last(missing, PLAY_ID))
        assertFalse(UserFacingErrors.KOKORO_EVAL_MODEL_MISSING == AppKokoroRuleBEval.last(missing, PLAY_ID))
        val empty = File.createTempFile("kokoro-empty", ".jsonl")
        empty.deleteOnExit()
        empty.writeText("")
        assertNull(AppKokoroRuleBEval.last(empty, PLAY_ID))
        val bad = File.createTempFile("kokoro-bad", ".jsonl")
        bad.deleteOnExit()
        bad.writeText("{$UTTERANCE")
        assertNull(AppKokoroRuleBEval.last(bad, PLAY_ID))
        assertNull(AppKokoroRuleBEval.last(requireNotNull(empty.parentFile), PLAY_ID))
    }

    @Test
    fun markNaturalnessOkRequiresFiveScoredAndDoesNotAutoTrueOnSynth() {
        val tmp = File.createTempFile("kokoro-mark", ".jsonl")
        tmp.deleteOnExit()
        tmp.writeText(SCORED_JSONL)
        val before = AppKokoroRuleBEval.last(tmp, PLAY_ID)!!
        assertTrue(before.contains("naturalnessApplied=false"))
        assertTrue(before.contains("ruleBPassed=false"))
        val after = AppKokoroRuleBEval.markNaturalnessOk(tmp, PLAY_ID)
        assertTrue(after.contains("naturalnessApplied=true"))
        assertTrue(after.contains("ruleBPassed=true"))
        assertNoLeak(after)
        val roundtrip = KokoroEval.loadJsonl(tmp.readText())
        assertTrue(roundtrip.all { it.naturalnessOk == true })

        val few = File.createTempFile("kokoro-few", ".jsonl")
        few.deleteOnExit()
        few.writeText(FOUR_SCORED_JSONL)
        val fewBefore = AppKokoroRuleBEval.last(few, PLAY_ID)!!
        val fewAfter = AppKokoroRuleBEval.markNaturalnessOk(few, PLAY_ID)
        assertEquals(fewBefore, fewAfter)
        assertTrue(fewAfter.contains("naturalnessApplied=false"))
        assertTrue(KokoroEval.loadJsonl(few.readText()).all { it.naturalnessOk == null })
    }

    private fun assertNoLeak(text: String) {
        assertFalse(text.contains(UTTERANCE))
        assertFalse(text.contains("请把灯打开"))
        assertFalse(text.contains("明天上午有会吗"))
        assertFalse(text.contains("帮我念一遍这句话"))
        assertFalse(text.contains("已达标"))
        assertFalse(text.contains("PCM"))
    }

    companion object {
        private const val PLAY_ID = "com.dougie.app"
        private const val SIDELOAD_ID = "com.dougie.app.sideload"
        private const val UTTERANCE = "请把灯打开"
        private const val SCORED_JSONL =
            """{"id":"g001","text":"请把灯打开","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g002","text":"明天上午有会吗","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g003","text":"帮我念一遍这句话","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g004","text":"现在外面下雨了","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g005","text":"提醒我喝水","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n"
        private const val FOUR_SCORED_JSONL =
            """{"id":"g001","text":"请把灯打开","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g002","text":"明天上午有会吗","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g003","text":"帮我念一遍这句话","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n" +
                """{"id":"g004","text":"现在外面下雨了","synthMs":500,"audioDurationMs":1000,"numThreads":1}""" + "\n"
    }
}
