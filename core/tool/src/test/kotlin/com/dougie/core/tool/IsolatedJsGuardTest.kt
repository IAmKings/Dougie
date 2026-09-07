package com.dougie.core.tool

import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedJsGuardTest {
    @Test
    fun fetchAndJavaTokensAreHost() {
        try {
            IsolatedJsGuard.assertNoHost("return fetch('https://x')")
            throw AssertionError("expected")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.JS_EVAL_HOST, e.userMessage)
        }
        try {
            IsolatedJsGuard.assertNoHost("return Java.type('x')")
            throw AssertionError("expected")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.JS_EVAL_HOST, e.userMessage)
        }
    }

    @Test
    fun identityScriptAllowed() {
        IsolatedJsGuard.assertNoHost("return data.n + 1")
        IsolatedJsGuard.assertSize("return data", "{}")
    }

    @Test
    fun commaSeparatedNumbersBecomeJsonArray() {
        assertEquals("[1,2]", IsolatedJsGuard.canonicalizeData("1,2"))
        assertEquals("[1,2]", IsolatedJsGuard.canonicalizeData("[1,2]"))
        assertEquals("[1,2]", IsolatedJsGuard.canonicalizeData("1, 2"))
        val program = IsolatedJsGuard.wrapProgram("return data.reduce((a,b)=>a+b,0)", "[1,2]")
        assertTrue(program.contains("JSON.parse(" + IsolatedJsGuard.quoteJs("[1,2]") + ")"))
    }
}
