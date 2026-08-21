package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTreeNamesTest {
    @Test
    fun prefersCanonicalName() {
        assertEquals(
            "models",
            ModelTreeNames.pickReusableName(
                listOf("models (2)", "models", "models (1)"),
                "models",
            ),
        )
    }

    @Test
    fun reusesUniquifiedWhenCanonicalMissing() {
        assertEquals(
            "models(1)",
            ModelTreeNames.pickReusableName(listOf("other", "models(2)", "models(1)"), "models"),
        )
        assertEquals(
            "models (1)",
            ModelTreeNames.pickReusableName(listOf("models (2)", "models (1)"), "models"),
        )
    }

    @Test
    fun doesNotMatchUnrelatedPrefix() {
        assertFalse(ModelTreeNames.matchesDirectory("models-backup", "models"))
        assertFalse(ModelTreeNames.matchesDirectory("models2", "models"))
        assertTrue(ModelTreeNames.matchesDirectory("model(2)", "model"))
        assertNull(ModelTreeNames.pickReusableName(listOf("asr"), "models"))
    }
}
