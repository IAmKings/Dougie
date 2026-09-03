package com.dougie.core.tool

import com.dougie.core.model.ToolContext
import com.dougie.core.model.UserFacingErrors
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    @Test
    fun findsBundledLogoOnSyntheticScene() {
        val logo = TemplateLibrary.frame(TemplateLibrary.LOGO)!!
        val scene = stampOnBlack(logo, canvasWidth = 64, canvasHeight = 64, x = 10, y = 8)
        val match = GrayscaleNccMatcher.match(scene, logo)
        assertEquals(10, match!!.x)
        assertEquals(8, match.y)
        assertTrue(match.confidence >= GrayscaleNccMatcher.THRESHOLD)
    }
}

class TemplateLibraryTest {

    @Test
    fun idsIncludeSolidAndLogo() {
        assertTrue(TemplateLibrary.ids().contains(TemplateLibrary.SOLID))
        assertTrue(TemplateLibrary.ids().contains(TemplateLibrary.LOGO))
        assertNotNull(TemplateLibrary.frame(TemplateLibrary.SOLID))
        assertNotNull(TemplateLibrary.frame(TemplateLibrary.LOGO))
        assertNull(TemplateLibrary.frame("missing"))
    }

    @Test
    fun logoHasContrast() {
        val logo = TemplateLibrary.frame(TemplateLibrary.LOGO)!!
        assertTrue(logo.width in 16..32)
        assertTrue(logo.height in 16..32)
        assertTrue(logo.gray.toSet().size > 1)
    }
}

class ScreenFrameDownscaleTest {

    @Test
    fun smallFramesStayUnscaled() {
        val image = whiteSquareOnBlack()
        val template = TemplateLibrary.frame(TemplateLibrary.SOLID)!!
        val prep = ScreenFrameDownscale.prepare(image, template)
        assertEquals(32, prep.image.width)
        assertEquals(1.0, prep.scaleX, 0.0)
        assertEquals(1.0, prep.scaleY, 0.0)
        assertEquals(12, prep.toOriginalX(12))
        assertEquals(7, prep.toOriginalY(7))
    }

    @Test
    fun mapsWorkingCoordsBackToOriginal() {
        val image = ScreenFrame("big", 640, 480, ByteArray(640 * 480))
        val template = TemplateLibrary.frame(TemplateLibrary.LOGO)!!
        val prep = ScreenFrameDownscale.prepare(image, template, workingWidth = 320)
        assertEquals(320, prep.image.width)
        assertEquals(240, prep.image.height)
        assertEquals(80, prep.toOriginalX(40))
        assertEquals(40, prep.toOriginalY(20))
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
    fun captureStoresPreviewJpegOutsideJson() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val port = FakeScreenCapturePort(nextJpeg = jpeg)
        val store = InMemoryScreenFrameStore()
        val tool = ScreenCaptureTool(port, store)
        val result = tool.execute("{}", ToolContext("t", "c"))
        assertEquals(jpeg.toList(), store.jpeg("synthetic")?.toList())
        assertFalse(result.json.contains("FF"))
        assertFalse(result.json.contains("data:image"))
    }

    @Test
    fun endProjectionSessionClearsFakeConsent() {
        val port = FakeScreenCapturePort()
        assertTrue(port.hasProjectionConsent())
        port.endProjectionSession()
        assertFalse(port.hasProjectionConsent())
    }

    @Test
    fun secondCaptureKeepsConsent() = runTest {
        val port = FakeScreenCapturePort()
        port.capture()
        port.capture()
        assertEquals(2, port.captureCount)
        assertTrue(port.hasProjectionConsent())
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

    @Test
    fun pinnedFrameSkipsCaptureAndIgnoresLaterPuts() = runTest {
        val port = FakeScreenCapturePort()
        val store = InMemoryScreenFrameStore()
        store.put(whiteSquareOnBlack())
        store.pin()
        store.put(ScreenFrame("other", 8, 8, ByteArray(64)))
        assertEquals("synthetic", store.last()?.id)
        val result = ScreenCaptureTool(port, store).execute("{}", ToolContext("t", "c"))
        assertEquals(0, port.captureCount)
        assertTrue(result.json.contains("\"capture_id\":\"synthetic\""))
        assertFalse(result.json.contains("data:image"))
        store.clearPin()
        store.put(ScreenFrame("other", 8, 8, ByteArray(64)))
        assertEquals("other", store.last()?.id)
    }

    @Test
    fun storeHoldsFourFramesAndRejectsFifth() {
        val store = InMemoryScreenFrameStore()
        repeat(4) { i ->
            assertTrue(store.put(ScreenFrame("f$i", 2, 2, ByteArray(4))))
        }
        assertFalse(store.put(ScreenFrame("f4", 2, 2, ByteArray(4))))
        assertEquals(4, store.size())
        assertEquals("f3", store.last()?.id)
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

    @Test
    fun descriptorNamesBundledTemplateIds() {
        val tool = ScreenMatchTool(InMemoryScreenFrameStore())
        assertTrue(tool.descriptor.description.contains(TemplateLibrary.SOLID))
        assertTrue(tool.descriptor.description.contains(TemplateLibrary.LOGO))
    }

    @Test
    fun findsLogoOnStoredFrame() = runTest {
        val logo = TemplateLibrary.frame(TemplateLibrary.LOGO)!!
        val store = InMemoryScreenFrameStore()
        store.put(stampOnBlack(logo, canvasWidth = 64, canvasHeight = 64, x = 10, y = 8))
        val tool = ScreenMatchTool(store)
        val result = tool.execute("""{"template_id":"logo"}""", ToolContext("t", "c"))
        assertFalse(result.isFatal)
        assertTrue(result.json.contains("\"template_id\":\"logo\""))
        assertTrue(result.json.contains("\"found\":true"))
        assertTrue(result.json.contains("\"x\":10"))
        assertTrue(result.json.contains("\"y\":8"))
    }

    @Test
    fun findsLogoOnLargeFrameAndMapsCoordsBack() = runTest {
        val logo = TemplateLibrary.frame(TemplateLibrary.LOGO)!!
        val store = InMemoryScreenFrameStore()
        store.put(stampOnBlack(logo, canvasWidth = 640, canvasHeight = 480, x = 80, y = 40))
        val tool = ScreenMatchTool(store)
        val result = tool.execute("""{"template_id":"logo"}""", ToolContext("t", "c"))
        assertFalse(result.isFatal)
        val obj = Json.parseToJsonElement(result.json).jsonObject
        val x = obj["x"]!!.jsonPrimitive.int
        val y = obj["y"]!!.jsonPrimitive.int
        assertTrue(abs(x - 80) <= 2)
        assertTrue(abs(y - 40) <= 2)
    }
}
