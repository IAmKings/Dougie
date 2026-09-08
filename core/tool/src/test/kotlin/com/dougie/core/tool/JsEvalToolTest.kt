package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsEvalToolTest {
    @Test
    fun identityScriptReturnsData() = runTest {
        val port = FakeJsEvalPort()
        val tool = JsEvalTool(port)
        val result = tool.execute(
            """{"script":"return data","data":"{\"n\":1}"}""",
            ToolContext("t", "c1"),
        )
        assertFalse(result.isFatal)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(true, obj["ok"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(1, obj["value"]!!.jsonObject["n"]!!.jsonPrimitive.content.toInt())
        assertEquals("return data", port.lastScript)
    }

    @Test
    fun fetchInScriptFailsBeforePort() = runTest {
        val port = FakeJsEvalPort()
        val tool = JsEvalTool(port)
        try {
            tool.validateArguments("""{"script":"return fetch('https://x')","data":"{}"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.JS_EVAL_HOST, e.userMessage)
        }
        assertEquals("", port.lastScript)
    }

    @Test
    fun engineNotReadyIsFatal() = runTest {
        val tool = JsEvalTool(FakeJsEvalPort(ready = false))
        val result = tool.execute(
            """{"script":"return data","data":"{}"}""",
            ToolContext("t", "c2"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.JS_ENGINE_NOT_READY, result.error)
    }

    @Test
    fun oversizedScriptRejected() = runTest {
        val tool = JsEvalTool(FakeJsEvalPort())
        val script = "return data" + "x".repeat(IsolatedJsGuard.SCRIPT_MAX)
        try {
            tool.validateArguments("""{"script":"$script","data":"{}"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.JS_EVAL_TOO_LARGE, e.userMessage)
        }
    }

    @Test
    fun timeoutFromPortIsFatal() = runTest {
        val tool = JsEvalTool(
            FakeJsEvalPort(failWith = UserFacingErrors.JS_EVAL_TIMEOUT),
        )
        val result = tool.execute(
            """{"script":"return data","data":"{}"}""",
            ToolContext("t", "c3"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.JS_EVAL_TIMEOUT, result.error)
    }

    @Test
    fun invalidDataJsonRejected() = runTest {
        val tool = JsEvalTool(FakeJsEvalPort())
        try {
            tool.validateArguments("""{"script":"return data","data":"{"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }

    @Test
    fun jobCancelIsNotMappedToEvalFailed() = runTest {
        val port = object : JsEvalPort {
            override fun isReady(): Boolean = true
            override fun evaluate(script: String, dataJson: String, asProgram: Boolean): String {
                throw CancellationException("job")
            }
        }
        val tool = JsEvalTool(port)
        try {
            tool.execute(
                """{"script":"return data","data":"{}"}""",
                ToolContext("t", "cCancel"),
            )
            throw AssertionError("expected cancel")
        } catch (e: CancellationException) {
            assertEquals("job", e.message)
        }
    }

    @Test
    fun commaSeparatedNumbersWithReduceSumToThree() = runTest {
        val port = FakeJsEvalPort()
        val tool = JsEvalTool(port)
        val result = tool.execute(
            """{"script":"return data.reduce((a,b)=>a+b,0)","data":"1,2"}""",
            ToolContext("t", "c4"),
        )
        assertFalse(result.isFatal)
        assertFalse(port.lastAsProgram)
        assertEquals("[1,2]", port.lastData)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(3, obj["value"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun jsonArrayDataWithReduceSumToThree() = runTest {
        val port = FakeJsEvalPort()
        val tool = JsEvalTool(port)
        val result = tool.execute(
            """{"script":"return data.reduce((a,b)=>a+b,0)","data":[1,2]}""",
            ToolContext("t", "c5"),
        )
        assertFalse(result.isFatal)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(3, obj["value"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun l2ReduceWithoutReturnFails() = runTest {
        val tool = JsEvalTool(FakeJsEvalPort(), privileged = { false })
        val result = tool.execute(
            """{"script":"data.reduce((a,b)=>a+b,0)","data":"1,2"}""",
            ToolContext("t", "c6"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.JS_EVAL_FAILED, result.error)
    }

    @Test
    fun l4ProgramReduceWithoutReturnSumsToThree() = runTest {
        val port = FakeJsEvalPort()
        val tool = JsEvalTool(port, privileged = { true })
        assertEquals(com.dougie.core.model.RiskLevel.L4, tool.descriptor.riskLevel)
        val result = tool.execute(
            """{"script":"data.reduce((a,b)=>a+b,0)","data":"1,2"}""",
            ToolContext("t", "c7"),
        )
        assertFalse(result.isFatal)
        assertTrue(port.lastAsProgram)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(3, obj["value"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun l4UndefinedLastValueIsFatal() = runTest {
        val tool = JsEvalTool(FakeJsEvalPort(), privileged = { true })
        val result = tool.execute(
            """{"script":"undefined","data":"{}"}""",
            ToolContext("t", "c8"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.JS_EVAL_NO_VALUE, result.error)
    }
}
