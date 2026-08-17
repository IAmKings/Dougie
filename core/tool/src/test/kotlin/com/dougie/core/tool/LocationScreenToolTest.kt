package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationToolTest {

    @Test
    fun fakePortReturnsFixedCoarseJson() = runTest {
        val port = FakeLocationPort()
        val tool = LocationTool(port)
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertTrue(result.json.contains("\"latitude\":31.23"))
        assertTrue(result.json.contains("\"longitude\":121.47"))
        assertEquals(1, port.queryCount)
        assertFalse(result.isFatal)
    }
}

class GrayscaleNccMatcherTest {

    @Test
    fun findsSyntheticWhiteSquare() {
        val scene = whiteSquareOnBlack(size = 32, square = 8, x = 12, y = 7)
        val template = TemplateLibrary.frame(TemplateLibrary.SOLID)!!
        val match = GrayscaleNccMatcher.match(scene, template)
        assertEquals(12, match!!.x)
        assertEquals(7, match.y)
        assertTrue(match.confidence >= GrayscaleNccMatcher.THRESHOLD)
    }
}

class ScreenCaptureToolTest {

    @Test
    fun resultContainsMetadataOnly() = runTest {
        val port = FakeScreenCapturePort()
        val store = InMemoryScreenFrameStore()
        val tool = ScreenCaptureTool(port, store)
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertTrue(result.json.contains("\"capture_id\""))
        assertTrue(result.json.contains("\"width\""))
        assertTrue(result.json.contains("\"height\""))
        assertFalse(result.json.contains("data:image"))
        assertFalse(result.json.contains("base64"))
        assertFalse(result.json.contains("gray"))
        assertEquals("synthetic", store.last()?.id)
        assertEquals(1, port.captureCount)
    }

    @Test
    fun backgroundIsFatalWithoutCapturing() = runTest {
        val port = FakeScreenCapturePort(foreground = false)
        val tool = ScreenCaptureTool(port, InMemoryScreenFrameStore())
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.SCREEN_NOT_FOREGROUND, result.error)
        assertEquals(0, port.captureCount)
    }

    @Test
    fun missingProjectionTokenIsFatalWithoutCapturing() = runTest {
        val port = FakeScreenCapturePort(hasConsent = false)
        val tool = ScreenCaptureTool(port, InMemoryScreenFrameStore())
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.PERMISSION_DENIED, result.error)
        assertEquals(0, port.captureCount)
    }
}

class ScreenMatchToolTest {

    @Test
    fun findsSquareOnStoredFrame() = runTest {
        val store = InMemoryScreenFrameStore()
        store.put(whiteSquareOnBlack(x = 12, y = 7))
        val tool = ScreenMatchTool(store)
        val result = tool.execute("""{"template_id":"solid"}""", ToolContext("t", "c"))
        assertFalse(result.isFatal)
        assertTrue(result.json.contains("\"found\":true"))
        assertTrue(result.json.contains("\"x\":12"))
        assertTrue(result.json.contains("\"y\":7"))
    }

    @Test
    fun missingFrameFailsWithoutGuessing() = runTest {
        val tool = ScreenMatchTool(InMemoryScreenFrameStore())
        val result = tool.execute("""{"template_id":"solid"}""", ToolContext("t", "c"))
        assertTrue(result.isFatal)
        assertEquals(UserFacingErrors.SCREEN_MATCH_FAILED, result.error)
        assertTrue(result.json.contains("\"found\":false"))
        assertFalse(result.json.contains("\"x\":12"))
    }

    @Test
    fun lowConfidenceFailsWithoutGuessing() = runTest {
        val store = InMemoryScreenFrameStore()
        store.put(ScreenFrame("black", 32, 32, ByteArray(32 * 32)))
        val tool = ScreenMatchTool(store)
        val result = tool.execute("""{"template_id":"solid"}""", ToolContext("t", "c"))
        assertTrue(result.isFatal)
        assertTrue(result.json.contains("\"found\":false"))
    }

    @Test
    fun unknownTemplateFailsWithoutGuessing() = runTest {
        val store = InMemoryScreenFrameStore()
        store.put(whiteSquareOnBlack())
        val tool = ScreenMatchTool(store)
        val result = tool.execute("""{"template_id":"missing"}""", ToolContext("t", "c"))
        assertTrue(result.isFatal)
        assertTrue(result.json.contains("\"found\":false"))
    }
}
