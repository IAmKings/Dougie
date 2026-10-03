package com.dougie.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerifiedResultJsonTest {

    @Test
    fun injectsFlagAndReadsItBack() {
        val json = VerifiedResultJson.withFlag("""{"ok":true}""", true)
        assertEquals("""{"ok":true,"verified":true}""", json)
        assertEquals(true, VerifiedResultJson.fromJson(json))
        assertEquals(false, VerifiedResultJson.fromJson(VerifiedResultJson.withFlag("""{"ok":true}""", false)))
    }

    @Test
    fun leavesUnparseableJsonUntouched() {
        assertEquals("not-json", VerifiedResultJson.withFlag("not-json", true))
        assertNull(VerifiedResultJson.fromJson("not-json"))
    }

    @Test
    fun missingKeyReadsAsUnverified() {
        assertNull(VerifiedResultJson.fromJson("""{"ok":true}"""))
    }
}
