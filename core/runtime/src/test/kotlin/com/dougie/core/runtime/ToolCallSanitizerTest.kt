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
}
