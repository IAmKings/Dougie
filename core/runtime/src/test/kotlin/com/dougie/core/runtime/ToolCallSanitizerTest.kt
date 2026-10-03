package com.dougie.core.runtime

import com.dougie.core.model.AgentException
import com.dougie.core.model.ToolDescriptor
import com.dougie.core.model.ToolParamSpec
import com.dougie.core.model.ToolParamType
import com.dougie.core.model.UserFacingErrors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallSanitizerTest {

    @Test
    fun coercesNumericStringForTypedField() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf(
                        "battery_percent" to ToolParamSpec(ToolParamType.INTEGER),
                    ),
                ),
            ),
        )
        val json = sanitizer.sanitize("probe", """{"battery_percent":"80","extra":true}""")
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(80, obj.getValue("battery_percent").jsonPrimitive.int)
        assertEquals(setOf("battery_percent"), obj.keys)
    }

    @Test
    fun emptySchemaDropsUnknownFieldsAndRepairsInvalidJson() {
        val sanitizer = ToolCallSanitizer(mapOf("time" to ToolDescriptor("time")))
        assertEquals("{}", sanitizer.sanitize("time", """{"foo":1,"bar":"x"}"""))
        assertEquals("{}", sanitizer.sanitize("time", "not-json"))
        assertEquals("{}", sanitizer.sanitize("time", ""))
    }

    @Test
    fun unknownToolNameFailsWithoutInventing() {
        val sanitizer = ToolCallSanitizer(mapOf("battery" to ToolDescriptor("battery")))
        try {
            sanitizer.sanitize("calendar", "{}")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.UNKNOWN_TOOL, e.userMessage)
        }
    }

    @Test
    fun coercesBooleanString() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf("charging" to ToolParamSpec(ToolParamType.BOOLEAN)),
                ),
            ),
        )
        val json = sanitizer.sanitize("probe", """{"charging":"true"}""")
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(true, obj.getValue("charging").jsonPrimitive.boolean)
    }

    @Test
    fun missingRequiredFieldWithoutDefaultFails() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "calendar_create" to ToolDescriptor(
                    name = "calendar_create",
                    properties = mapOf(
                        "title" to ToolParamSpec(ToolParamType.STRING),
                        "startIso" to ToolParamSpec(ToolParamType.STRING),
                    ),
                ),
            ),
        )
        try {
            sanitizer.sanitize("calendar_create", """{"title":"开会"}""")
            throw AssertionError("expected AgentException")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
    }

    @Test
    fun fillsDefaultWhenRequiredFieldMissing() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf(
                        "battery_percent" to ToolParamSpec(ToolParamType.INTEGER, defaultJson = "0"),
                    ),
                ),
            ),
        )
        val json = sanitizer.sanitize("probe", "{}")
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(0, obj.getValue("battery_percent").jsonPrimitive.int)
    }

    @Test
    fun jsEvalDataArrayIsKeptAsJson() {
        val sanitizer = ToolCallSanitizer(
            mapOf("js_eval" to com.dougie.core.tool.JsEvalTool.DESCRIPTOR),
        )
        val json = sanitizer.sanitize(
            "js_eval",
            """{"script":"return data.reduce((a,b)=>a+b,0)","data":[1,2]}""",
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("[1,2]", obj.getValue("data").toString())
    }

    @Test
    fun oversizedStringParamIsRejected() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf("text" to ToolParamSpec(ToolParamType.STRING)),
                ),
            ),
        )
        try {
            sanitizer.sanitize("probe", """{"text":"${"字".repeat(10_001)}"}""")
            throw AssertionError("expected oversize reject")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
        val ok = sanitizer.sanitize("probe", """{"text":"${"字".repeat(10_000)}"}""")
        assertEquals(10_000, Json.parseToJsonElement(ok).jsonObject.getValue("text").jsonPrimitive.content.length)
    }

    @Test
    fun oversizedObjectParamIsRejected() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf("data" to ToolParamSpec(ToolParamType.OBJECT)),
                ),
            ),
        )
        val bigList = (0 until 20_000).joinToString(",", "[", "]")
        try {
            sanitizer.sanitize("probe", """{"data":$bigList}""")
            throw AssertionError("expected oversize reject")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
        // js_eval-sized payloads (own 32KiB cap) still pass the 64K sanitizer budget.
        val legal = (0 until 8_000).joinToString(",", "[", "]")
        val json = sanitizer.sanitize("probe", """{"data":$legal}""")
        assertTrue(Json.parseToJsonElement(json).jsonObject.getValue("data").toString().length > 30_000)
    }

    @Test
    fun overlyDeepObjectParamIsRejected() {
        val sanitizer = ToolCallSanitizer(
            mapOf(
                "probe" to ToolDescriptor(
                    name = "probe",
                    properties = mapOf("data" to ToolParamSpec(ToolParamType.OBJECT)),
                ),
            ),
        )
        val deep = buildString {
            append("{\"a\":")
            repeat(9) { append("[") }
            append("1")
            repeat(9) { append("]") }
            append("}")
        }
        try {
            sanitizer.sanitize("probe", """{"data":$deep}""")
            throw AssertionError("expected depth reject")
        } catch (e: AgentException) {
            assertEquals(UserFacingErrors.INVALID_TOOL_ARGS, e.userMessage)
        }
        val shallow = sanitizer.sanitize("probe", """{"data":{"a":[1,2]}}""")
        assertEquals("""{"a":[1,2]}""", Json.parseToJsonElement(shallow).jsonObject.getValue("data").toString())
    }
}
