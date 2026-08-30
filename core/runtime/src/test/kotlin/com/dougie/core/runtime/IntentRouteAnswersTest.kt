package com.dougie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentRouteAnswersTest {
    @Test
    fun timeExampleNormalizesToProbePhrase() {
        assertEquals("现在几点", IntentRouteAnswers.normalize("现在几点了？"))
        assertEquals(
            listOf("现在几点了？", "现在几点"),
            IntentRouteAnswers.classifyTexts("现在几点了？"),
        )
    }
}
