package com.dougie.core.tool

import com.dougie.core.model.RiskLevel
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

class PyEvalToolTest {
    @Test
    fun numpySumOfCommaDataIsThree() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        assertEquals(RiskLevel.L4, tool.descriptor.riskLevel)
        val result = tool.execute(
            """{"script":"import numpy as np; float(np.array(data).sum())","data":"1,2"}""",
            ToolContext("t", "c1"),
        )
        assertFalse(result.isFatal)
        assertEquals("[1,2]", port.lastData)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(true, obj["ok"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(3, obj["value"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun identityScriptReturnsData() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        val result = tool.execute(
            """{"script":"data","data":"{\"n\":1}"}""",
            ToolContext("t", "c2"),
        )
        assertFalse(result.isFatal)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        assertEquals(1, obj["value"]!!.jsonObject["n"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun emptyLastValueIsFatal() = runTest {
        val tool = PyEvalTool(FakePyEvalPort())
        val result = tool.execute(
            """{"script":"None","data":"{}"}""",
            ToolContext("t", "c3"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PY_EVAL_NO_VALUE, result.error)
    }

    @Test
    fun openAbsolutePathFailsAtExecute() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        tool.validateArguments("""{"script":"open('/etc/passwd')","data":"{}"}""")
        val result = tool.execute(
            """{"script":"open('/etc/passwd')","data":"{}"}""",
            ToolContext("t", "cAbs"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PY_EVAL_HOST, result.error)
        assertEquals("open('/etc/passwd')", port.lastScript)
    }

    @Test
    fun ioOpenAbsolutePathFailsAtExecute() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        tool.validateArguments("""{"script":"io.open('/tmp/x')","data":"{}"}""")
        val result = tool.execute(
            """{"script":"io.open('/tmp/x')","data":"{}"}""",
            ToolContext("t", "cIoAbs"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PY_EVAL_HOST, result.error)
        assertEquals("io.open('/tmp/x')", port.lastScript)
    }

    @Test
    fun parentPathFailsAtExecute() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("py-sandbox").toFile()
        try {
            val port = FakePyEvalPort(sandboxDir = dir)
            val tool = PyEvalTool(port)
            val result = tool.execute(
                """{"script":"open('../x')","data":"{}"}""",
                ToolContext("t", "cParent"),
            )
            assertTrue(result.isFatal)
            assertEquals(UserFacingErrors.PY_EVAL_HOST, result.error)
            assertEquals("open('../x')", port.lastScript)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun urllibInScriptFailsBeforePort() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        try {
            tool.validateArguments("""{"script":"import urllib.request","data":"{}"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.PY_EVAL_HOST, e.userMessage)
        }
        assertEquals("", port.lastScript)
    }

    @Test
    fun ctypesInScriptFailsBeforePort() = runTest {
        val port = FakePyEvalPort()
        val tool = PyEvalTool(port)
        try {
            tool.validateArguments("""{"script":"import ctypes","data":"{}"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.PY_EVAL_HOST, e.userMessage)
        }
        assertEquals("", port.lastScript)
    }

    @Test
    fun importOsAllowedAtValidate() {
        PyEvalTool(FakePyEvalPort()).validateArguments(
            """{"script":"import os; os.listdir('.')","data":"{}"}""",
        )
    }

    @Test
    fun fromOsAllowedAtValidate() {
        PyEvalTool(FakePyEvalPort()).validateArguments(
            """{"script":"from os import listdir","data":"{}"}""",
        )
    }

    @Test
    fun csvWriteThenReadSumsToThreeAndPersists() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("py-sandbox").toFile()
        try {
            val port = FakePyEvalPort(sandboxDir = dir)
            val tool = PyEvalTool(port)
            val write = tool.execute(
                """{"script":"open('t.csv','w'); import pandas as pd; float(pd.read_csv('t.csv', header=None)[0].sum())","data":"{}"}""",
                ToolContext("t", "cCsv1"),
            )
            assertFalse(write.isFatal)
            val first = Json.parseToJsonElement(write.json).jsonObject
            assertEquals(3, first["value"]!!.jsonPrimitive.content.toInt())
            val read = tool.execute(
                """{"script":"import pandas as pd; float(pd.read_csv('t.csv', header=None)[0].sum())","data":"{}"}""",
                ToolContext("t", "cCsv2"),
            )
            assertFalse(read.isFatal)
            val second = Json.parseToJsonElement(read.json).jsonObject
            assertEquals(3, second["value"]!!.jsonPrimitive.content.toInt())
            assertTrue(java.io.File(dir, "t.csv").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sandboxQuotaExceededIsFatal() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("py-sandbox").toFile()
        try {
            java.io.File(dir, "full.bin").writeBytes(ByteArray(FakePyEvalPort.QUOTA_BYTES.toInt()))
            val port = FakePyEvalPort(sandboxDir = dir)
            val tool = PyEvalTool(port)
            val result = tool.execute(
                """{"script":"open('t.csv','w'); import pandas as pd; float(pd.read_csv('t.csv', header=None)[0].sum())","data":"{}"}""",
                ToolContext("t", "cQuota"),
            )
            assertTrue(result.isFatal)
            assertEquals(UserFacingErrors.PY_EVAL_QUOTA, result.error)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun engineNotReadyIsFatal() = runTest {
        val tool = PyEvalTool(FakePyEvalPort(ready = false))
        val result = tool.execute(
            """{"script":"data","data":"{}"}""",
            ToolContext("t", "c4"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PY_ENGINE_NOT_READY, result.error)
    }

    @Test
    fun timeoutFromPortIsFatal() = runTest {
        val tool = PyEvalTool(
            FakePyEvalPort(failWith = UserFacingErrors.PY_EVAL_TIMEOUT),
        )
        val result = tool.execute(
            """{"script":"data","data":"{}"}""",
            ToolContext("t", "c5"),
        )
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PY_EVAL_TIMEOUT, result.error)
    }

    @Test
    fun oversizedScriptRejected() = runTest {
        val tool = PyEvalTool(FakePyEvalPort())
        val script = "data" + "x".repeat(IsolatedPyGuard.SCRIPT_MAX)
        try {
            tool.validateArguments("""{"script":"$script","data":"{}"}""")
            throw AssertionError("expected")
        } catch (e: com.dougie.core.model.AgentException) {
            assertEquals(UserFacingErrors.PY_EVAL_TOO_LARGE, e.userMessage)
        }
    }

    @Test
    fun jobCancelIsNotMappedToEvalFailed() = runTest {
        val port = object : PyEvalPort {
            override fun isReady(): Boolean = true
            override fun evaluate(script: String, dataJson: String): String {
                throw CancellationException("job")
            }
        }
        val tool = PyEvalTool(port)
        try {
            tool.execute(
                """{"script":"data","data":"{}"}""",
                ToolContext("t", "cCancel"),
            )
            throw AssertionError("expected cancel")
        } catch (e: CancellationException) {
            assertEquals("job", e.message)
        }
    }
}
